package com.adikan;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatDataListener {
    private static final Logger LOGGER = LoggerFactory.getLogger("MineberryBanMod");

    public enum AltStatus {
        ONLINE,
        OFFLINE,
        BANNED
    }

    public record AltEntry(String username, AltStatus status) {
    }

    public record PunishmentInfo(
            boolean active,
            String actor,
            String reason,
            String date,
            String duration,
            String server,
            String flags,
            List<String> rawLines
    ) {
        public static PunishmentInfo inactive() {
            return new PunishmentInfo(false, "", "", "", "", "", "", List.of());
        }

        public static PunishmentInfo unknown() {
            return new PunishmentInfo(false, "", "", "", "", "", "", List.of());
        }
    }

    public record HistoryInfo(
            boolean found,
            int page,
            int totalPages,
            List<String> rawLines
    ) {
        public static HistoryInfo unknown() {
            return new HistoryInfo(false, 1, 1, List.of());
        }
    }

    private static final Pattern ALTS_HEADER_PATTERN =
            Pattern.compile("Scanning player\\s+([^,]+),\\s*\\d+\\s+addresses", Pattern.CASE_INSENSITIVE);
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{1,16}$");

    private static final Pattern ACTIVE_MUTES_HEADER =
            Pattern.compile("\\[\\*]\\s*The player\\s+([A-Za-z0-9_]{1,16})\\s+has\\s+\\d+\\s+active mutes", Pattern.CASE_INSENSITIVE);
    private static final Pattern NOT_MUTED =
            Pattern.compile("\\[\\*]\\s*The player\\s+([A-Za-z0-9_]{1,16})\\s+is not muted\\.?", Pattern.CASE_INSENSITIVE);

    private static final Pattern ACTIVE_BANS_HEADER =
            Pattern.compile("\\[\\*]\\s*The player\\s+([A-Za-z0-9_]{1,16})\\s+has\\s+\\d+\\s+active bans", Pattern.CASE_INSENSITIVE);
    private static final Pattern IP_PUNISHMENTS_HEADER =
            Pattern.compile("\\[\\*]\\s*Player\\s+([A-Za-z0-9_]{1,16})\\s+has\\s+\\d+\\s+punishments on their last IP address:?", Pattern.CASE_INSENSITIVE);
    private static final Pattern NOT_BANNED =
            Pattern.compile("\\[\\*]\\s*The player\\s+([A-Za-z0-9_]{1,16})\\s+is not banned\\.?", Pattern.CASE_INSENSITIVE);
    private static final Pattern HISTORY_HEADER =
            Pattern.compile("\\[\\*]\\s*List of punishments\\s+([A-Za-z0-9_]{1,16})\\s+got\\s*\\(page\\s+(\\d+)\\s+of\\s+(\\d+)\\)", Pattern.CASE_INSENSITIVE);

    public static BiConsumer<String, List<AltEntry>> onAltsLine = null;
    public static BiConsumer<String, PunishmentInfo> onMuteInfo = null;
    public static BiConsumer<String, PunishmentInfo> onBanInfo = null;
    public static BiConsumer<String, HistoryInfo> onHistoryInfo = null;

    public static volatile String lastAltsUsername = null;
    public static volatile String lastAltsResult = null;
    public static volatile List<AltEntry> lastAltsEntries = List.of();

    public static volatile String lastMuteUsername = null;
    public static volatile PunishmentInfo lastMuteInfo = PunishmentInfo.unknown();

    public static volatile String lastBanUsername = null;
    public static volatile PunishmentInfo lastBanInfo = PunishmentInfo.unknown();
    public static volatile String lastHistoryUsername = null;
    public static volatile HistoryInfo lastHistoryInfo = HistoryInfo.unknown();

    private static boolean nextLineIsAlts = false;
    private static String pendingAltsUsername = null;
    private static boolean registered = false;

    private static String lastHeaderUsername = null;
    private static long lastHeaderAtMs = 0L;
    private static long lastCaptureAtMs = 0L;

    private static String collectingMuteFor = null;
    private static final List<String> muteLines = new ArrayList<>();

    private static String collectingBanFor = null;
    private static final List<String> banLines = new ArrayList<>();
    private static String collectingHistoryFor = null;
    private static int collectingHistoryPage = 1;
    private static int collectingHistoryTotalPages = 1;
    private static final List<String> historyLines = new ArrayList<>();

    private static String lastProcessedLine = "";
    private static long lastProcessedAtMs = 0L;
    private static boolean lastProcessedSuppressed = false;

    private static String suppressLookupUsername = null;
    private static long suppressLookupUntilMs = 0L;
    private static final long LOOKUP_SUPPRESS_WINDOW_MS = 45_000L;

    private record Outcome(boolean consumed, boolean suppress) {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;

        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signedMessage, sender, params, ts) -> !process(message));

        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (overlay) {
                return true;
            }
            return !process(message);
        });
    }

    public static void beginLookupSession(String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        suppressLookupUsername = username.trim();
        suppressLookupUntilMs = System.currentTimeMillis() + LOOKUP_SUPPRESS_WINDOW_MS;
        LOGGER.info("[MBERRY] Lookup suppression started for {}", suppressLookupUsername);
    }

    public static void extendLookupSession() {
        if (suppressLookupUsername == null) {
            return;
        }
        suppressLookupUntilMs = System.currentTimeMillis() + LOOKUP_SUPPRESS_WINDOW_MS;
    }

    public static void endLookupSession(String username) {
        if (suppressLookupUsername != null && username != null && suppressLookupUsername.equalsIgnoreCase(username)) {
            LOGGER.info("[MBERRY] Lookup suppression ended for {}", suppressLookupUsername);
            suppressLookupUsername = null;
            suppressLookupUntilMs = 0L;
        }
    }

    private static boolean process(Component message) {
        String line = message == null ? "" : message.getString().trim();
        if (line.isEmpty()) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (line.equals(lastProcessedLine) && now - lastProcessedAtMs < 120L) {
            return lastProcessedSuppressed;
        }

        boolean suppress = false;

        Outcome altsOutcome = processAlts(message, line, now);
        suppress |= altsOutcome.suppress;

        if (!altsOutcome.consumed) {
            suppress |= processMuteBan(line);
        }

        // Fallback suppression path for lookup outputs in case parser misses style/order edge cases.
        suppress |= shouldSuppressLookupLine(line);

        lastProcessedLine = line;
        lastProcessedAtMs = now;
        lastProcessedSuppressed = suppress;
        return suppress;
    }

    private static boolean shouldSuppressLookupLine(String line) {
        if (!isSuppressionActive()) {
            return false;
        }

        String target = suppressLookupUsername;
        if (target == null || target.isBlank()) {
            return false;
        }

        String lower = line.toLowerCase(Locale.ROOT);
        String targetLower = target.toLowerCase(Locale.ROOT);

        if (lower.contains("scanning player " + targetLower) && lower.contains("addresses")) {
            return true;
        }
        if (lower.contains("the player " + targetLower + " has ") && lower.contains("active mutes")) {
            return true;
        }
        if (lower.contains("the player " + targetLower + " has ") && lower.contains("active bans")) {
            return true;
        }
        if (lower.contains("player " + targetLower + " has ") && lower.contains("punishments on their last ip address")) {
            return true;
        }
        if (lower.contains("the player " + targetLower + " is not muted")) {
            return true;
        }
        if (lower.contains("the player " + targetLower + " is not banned")) {
            return true;
        }
        if (lower.contains("list of punishments " + targetLower + " got")) {
            return true;
        }
        if (lower.startsWith("- " + targetLower + " banned by")) {
            return true;
        }
        if (lower.startsWith("* " + targetLower + " was unbanned")) {
            return true;
        }

        // Alts payload line: comma-separated usernames where target username is present.
        if (looksLikeUsernameCsv(line) && lower.contains(targetLower)) {
            return true;
        }

        // Detail block lines that follow checkmute/checkban headers.
        if (line.startsWith("|") && (collectingMuteFor != null || collectingBanFor != null)) {
            return true;
        }
        if ((line.startsWith("-") || line.startsWith("*")) && collectingHistoryFor != null) {
            return true;
        }

        return false;
    }

    private static boolean looksLikeUsernameCsv(String line) {
        String[] users = line.split(",\\s*");
        if (users.length < 2) {
            return false;
        }
        for (String user : users) {
            if (!USERNAME_PATTERN.matcher(user.trim()).matches()) {
                return false;
            }
        }
        return true;
    }

    private static Outcome processAlts(Component message, String line, long now) {
        Matcher header = ALTS_HEADER_PATTERN.matcher(line);
        if (header.find()) {
            String username = header.group(1).trim();

            if (username.equalsIgnoreCase(lastHeaderUsername) && now - lastHeaderAtMs < 500L) {
                return new Outcome(true, shouldSuppressFor(username));
            }

            lastHeaderUsername = username;
            lastHeaderAtMs = now;
            nextLineIsAlts = true;
            pendingAltsUsername = username;
            return new Outcome(true, shouldSuppressFor(username));
        }

        if (nextLineIsAlts) {
            nextLineIsAlts = false;
            String username = pendingAltsUsername;
            pendingAltsUsername = null;

            if (username == null) {
                return new Outcome(true, false);
            }

            List<AltEntry> parsed = parseStyledAlts(message);
            if (parsed.isEmpty()) {
                return new Outcome(true, shouldSuppressFor(username));
            }

            String normalized = joinUsernames(parsed);
            if (username.equalsIgnoreCase(lastAltsUsername)
                    && normalized.equals(lastAltsResult)
                    && now - lastCaptureAtMs < 1000L) {
                return new Outcome(true, shouldSuppressFor(username));
            }
            lastCaptureAtMs = now;

            lastAltsUsername = username;
            lastAltsResult = normalized;
            lastAltsEntries = List.copyOf(parsed);

            if (onAltsLine != null) {
                onAltsLine.accept(username, lastAltsEntries);
                onAltsLine = null;
            }
            return new Outcome(true, shouldSuppressFor(username));
        }

        return new Outcome(false, false);
    }

    private static boolean processMuteBan(String line) {
        boolean suppress = false;

        Matcher activeMute = ACTIVE_MUTES_HEADER.matcher(line);
        if (activeMute.find()) {
            finalizeMuteCollectionIfAny();
            collectingMuteFor = activeMute.group(1);
            muteLines.clear();
            LOGGER.info("[MBERRY] Detected active mute header for {}", collectingMuteFor);
            return shouldSuppressFor(collectingMuteFor);
        }

        Matcher notMuted = NOT_MUTED.matcher(line);
        if (notMuted.find()) {
            String username = notMuted.group(1);
            PunishmentInfo info = PunishmentInfo.inactive();
            LOGGER.info("[MBERRY] Detected not-muted result for {}", username);
            lastMuteUsername = username;
            lastMuteInfo = info;
            if (onMuteInfo != null) {
                onMuteInfo.accept(username, info);
                onMuteInfo = null;
            }
            return shouldSuppressFor(username);
        }

        Matcher activeBan = ACTIVE_BANS_HEADER.matcher(line);
        if (activeBan.find()) {
            finalizeBanCollectionIfAny();
            collectingBanFor = activeBan.group(1);
            banLines.clear();
            LOGGER.info("[MBERRY] Detected active ban header for {}", collectingBanFor);
            return shouldSuppressFor(collectingBanFor);
        }

        Matcher ipPunishments = IP_PUNISHMENTS_HEADER.matcher(line);
        if (ipPunishments.find()) {
            finalizeBanCollectionIfAny();
            collectingBanFor = ipPunishments.group(1);
            banLines.clear();
            banLines.add(stripPipePrefix(line));
            LOGGER.info("[MBERRY] Detected IP punishments header for {}", collectingBanFor);
            return shouldSuppressFor(collectingBanFor);
        }

        Matcher notBanned = NOT_BANNED.matcher(line);
        if (notBanned.find()) {
            String username = notBanned.group(1);
            PunishmentInfo info = PunishmentInfo.inactive();
            LOGGER.info("[MBERRY] Detected not-banned result for {}", username);
            lastBanUsername = username;
            lastBanInfo = info;
            if (onBanInfo != null) {
                onBanInfo.accept(username, info);
                onBanInfo = null;
            }
            return shouldSuppressFor(username);
        }

        Matcher historyHeader = HISTORY_HEADER.matcher(line);
        if (historyHeader.find()) {
            finalizeHistoryCollectionIfAny();
            collectingHistoryFor = historyHeader.group(1);
            collectingHistoryPage = parseIntSafe(historyHeader.group(2), 1);
            collectingHistoryTotalPages = parseIntSafe(historyHeader.group(3), 1);
            historyLines.clear();
            historyLines.add(line);
            LOGGER.info("[MBERRY] Detected history header for {}", collectingHistoryFor);
            return shouldSuppressFor(collectingHistoryFor);
        }

        if (collectingMuteFor != null) {
            if (line.startsWith("|")) {
                muteLines.add(stripPipePrefix(line));
                return shouldSuppressFor(collectingMuteFor);
            }
            suppress |= shouldSuppressFor(collectingMuteFor);
            finalizeMuteCollectionIfAny();
        }

        if (collectingBanFor != null) {
            if (line.startsWith("|")) {
                banLines.add(stripPipePrefix(line));
                return shouldSuppressFor(collectingBanFor);
            }
            suppress |= shouldSuppressFor(collectingBanFor);
            finalizeBanCollectionIfAny();
        }

        if (collectingHistoryFor != null) {
            if (line.startsWith("-") || line.startsWith("*")) {
                historyLines.add(line.trim());
                return shouldSuppressFor(collectingHistoryFor);
            }
            suppress |= shouldSuppressFor(collectingHistoryFor);
            finalizeHistoryCollectionIfAny();
        }

        return suppress;
    }

    private static boolean shouldSuppressFor(String username) {
        if (!isSuppressionActive()) {
            return false;
        }
        return username != null && username.equalsIgnoreCase(suppressLookupUsername);
    }

    private static boolean isSuppressionActive() {
        if (suppressLookupUsername == null) {
            return false;
        }
        if (System.currentTimeMillis() > suppressLookupUntilMs) {
            suppressLookupUsername = null;
            suppressLookupUntilMs = 0L;
            return false;
        }
        return true;
    }

    private static String stripPipePrefix(String line) {
        String out = line;
        if (out.startsWith("|")) {
            out = out.substring(1);
        }
        return out.trim();
    }

    private static void finalizeMuteCollectionIfAny() {
        if (collectingMuteFor == null) {
            return;
        }
        PunishmentInfo info = buildPunishmentInfo(true, muteLines, true);
        String username = collectingMuteFor;
        LOGGER.info("[MBERRY] Finalized mute info for {} lines={}", username, muteLines.size());
        collectingMuteFor = null;
        muteLines.clear();

        lastMuteUsername = username;
        lastMuteInfo = info;
        if (onMuteInfo != null) {
            onMuteInfo.accept(username, info);
            onMuteInfo = null;
        }
    }

    private static void finalizeBanCollectionIfAny() {
        if (collectingBanFor == null) {
            return;
        }
        PunishmentInfo info = buildPunishmentInfo(true, banLines, false);
        String username = collectingBanFor;
        LOGGER.info("[MBERRY] Finalized ban info for {} lines={}", username, banLines.size());
        collectingBanFor = null;
        banLines.clear();

        lastBanUsername = username;
        lastBanInfo = info;
        if (onBanInfo != null) {
            onBanInfo.accept(username, info);
            onBanInfo = null;
        }
    }

    private static void finalizeHistoryCollectionIfAny() {
        if (collectingHistoryFor == null) {
            return;
        }
        String username = collectingHistoryFor;
        HistoryInfo info = new HistoryInfo(true, Math.max(1, collectingHistoryPage), Math.max(1, collectingHistoryTotalPages), List.copyOf(historyLines));
        LOGGER.info("[MBERRY] Finalized history info for {} page={}/{} lines={}", username, info.page(), info.totalPages(), info.rawLines().size());
        collectingHistoryFor = null;
        collectingHistoryPage = 1;
        collectingHistoryTotalPages = 1;
        historyLines.clear();

        lastHistoryUsername = username;
        lastHistoryInfo = info;
        if (onHistoryInfo != null) {
            onHistoryInfo.accept(username, info);
            onHistoryInfo = null;
        }
    }

    private static int parseIntSafe(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static PunishmentInfo buildPunishmentInfo(boolean active, List<String> lines, boolean muteType) {
        String actor = "";
        String reason = "";
        String date = "";
        String duration = "";
        String server = "";
        String flags = "";

        for (String line : lines) {
            if (line.startsWith("Reason:")) {
                reason = valueAfter(line, "Reason:");
            } else if (line.startsWith(muteType ? "Muted by" : "Banned by")) {
                actor = valueAfterColon(line);
            } else if (line.startsWith(muteType ? "Mute date:" : "Ban date:")) {
                date = valueAfterColon(line);
            } else if (line.startsWith(muteType ? "Muted for:" : "Banned for:")) {
                duration = valueAfterColon(line);
            } else if (line.startsWith(muteType ? "Muted on server:" : "Banned on server:")) {
                server = valueAfterColon(line);
            } else if (line.startsWith(muteType ? "IP mute:" : "IP ban:")) {
                flags = line;
            }
        }

        return new PunishmentInfo(active, actor, reason, date, duration, server, flags, List.copyOf(lines));
    }

    private static String valueAfter(String line, String prefix) {
        return line.substring(prefix.length()).trim();
    }

    private static String valueAfterColon(String line) {
        int idx = line.indexOf(':');
        if (idx < 0 || idx == line.length() - 1) {
            return "";
        }
        return line.substring(idx + 1).trim();
    }

    private static List<AltEntry> parseStyledAlts(Component component) {
        List<StyledChunk> chunks = new ArrayList<>();
        component.visit((style, text) -> {
            chunks.add(new StyledChunk(text, style));
            return Optional.empty();
        }, Style.EMPTY);

        List<AltEntry> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        StringBuilder token = new StringBuilder();
        AltStatus tokenStatus = AltStatus.OFFLINE;

        for (StyledChunk chunk : chunks) {
            AltStatus chunkStatus = statusFromStyle(chunk.style);
            String text = chunk.text;

            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == ',') {
                    addToken(out, seen, token, tokenStatus);
                    token = new StringBuilder();
                    tokenStatus = AltStatus.OFFLINE;
                    continue;
                }

                token.append(c);
                if (!Character.isWhitespace(c) && chunkStatus != AltStatus.OFFLINE) {
                    tokenStatus = chunkStatus;
                }
            }
        }

        addToken(out, seen, token, tokenStatus);
        return out;
    }

    private static void addToken(List<AltEntry> out, Set<String> seen, StringBuilder token, AltStatus status) {
        String username = token.toString().trim();
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            return;
        }
        String lower = username.toLowerCase(Locale.ROOT);
        if (seen.add(lower)) {
            out.add(new AltEntry(username, status));
        }
    }

    private static AltStatus statusFromStyle(Style style) {
        TextColor color = style.getColor();
        if (color == null) {
            return AltStatus.OFFLINE;
        }

        int rgb = color.getValue();
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        if (r >= g + 30 && r >= b + 30) {
            return AltStatus.BANNED;
        }
        if (g >= r + 20 && g >= b + 15) {
            return AltStatus.ONLINE;
        }
        return AltStatus.OFFLINE;
    }

    private static String joinUsernames(List<AltEntry> entries) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(entries.get(i).username());
        }
        return sb.toString();
    }

    private record StyledChunk(String text, Style style) {
    }

    /** Call this before sending moderation lookup commands so buffers are invalidated for this lookup. */
    public static void clearBuffer() {
        lastAltsUsername = null;
        lastAltsResult = null;
        lastAltsEntries = List.of();

        lastMuteUsername = null;
        lastMuteInfo = PunishmentInfo.unknown();

        lastBanUsername = null;
        lastBanInfo = PunishmentInfo.unknown();
        lastHistoryUsername = null;
        lastHistoryInfo = HistoryInfo.unknown();

        collectingMuteFor = null;
        muteLines.clear();
        collectingBanFor = null;
        banLines.clear();
        collectingHistoryFor = null;
        historyLines.clear();

        nextLineIsAlts = false;
        pendingAltsUsername = null;
    }
}
