package com.adikan;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ModerationScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger("MineberryBanMod");

    private enum Step {
        HOME,
        REASON,
        DURATION
    }

    private enum AltColumn {
        BANNED,
        ONLINE,
        OFFLINE
    }

    private static final int PANEL_W = 760;
    private static final int PANEL_H = 430;

    private static final int COL_BG = 0xDD0A0E17;
    private static final int COL_BORDER = 0xFF5B5BFF;
    private static final int COL_DIVIDER = 0xFF343D6C;

    private static final int ACTION_BOX_BG = 0x55101722;
    private static final int ACTION_BOX_BORDER = 0xFF2B3A66;
    private static final int ALTS_BOX_BG = 0x55101520;
    private static final int ALTS_BOX_BORDER = 0xFF3A4A8C;

    private static final int WARN_ACCENT = 0xFF1FA151;
    private static final int MUTE_ACCENT = 0xFF9AA1AD;
    private static final int BAN_ACCENT = 0xFFD64A4A;

    private static final int ONLINE_COLOR = 0xFF7CFF95;
    private static final int OFFLINE_COLOR = 0xFFB6BDC9;
    private static final int BANNED_COLOR = 0xFFFF7A7A;

    private static final int ALTS_TIMEOUT_TICKS = 200;
    private static final int CHECK_TIMEOUT_TICKS = 200;
    private static final int ALTS_ROW_H = 10;
    private static final int COMMAND_CHAIN_DELAY_TICKS = 30;
    private static final Pattern HISTORY_BAN_LINE = Pattern.compile(
            "-\\s+([A-Za-z0-9_]{1,16})\\s+banned\\s+by\\s+(.+?)\\s+at\\s+(.+?)\\s+for\\s+(.+?)\\s+for:\\s+'(.+?)'\\s+\\(([^)]+)\\)\\s+\\[([^\\]]+)]",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern HISTORY_UNBAN_LINE = Pattern.compile(
            "\\*\\s+([A-Za-z0-9_]{1,16})\\s+was\\s+unbanned\\s+by\\s+(.+?)\\s+\\(([^)]+)\\)",
            Pattern.CASE_INSENSITIVE
    );

    private final String targetUsername;

    private Step step = Step.HOME;
    private String action = "";
    private String reason = "";
    private String reasonLabel = "";
    private String unit = "";
    private String serverScope = "";
    private String kitpvpScope = "";
    private boolean silent = false;

    private EditBox searchInput;
    private EditBox customReasonInput;
    private EditBox numberInput;
    private final List<OutlinedButton> reasonButtons = new ArrayList<>();
    private String lastSearch = "";
    private String customReasonDraft = "";

    private volatile String altsText = "Loading...";
    private volatile boolean altsLoading = true;
    private int altsRequestTick = 0;
    private int screenTick = 0;

    private volatile ChatDataListener.PunishmentInfo muteInfo = ChatDataListener.PunishmentInfo.unknown();
    private volatile ChatDataListener.PunishmentInfo banInfo = ChatDataListener.PunishmentInfo.unknown();
    private volatile ChatDataListener.HistoryInfo historyInfo = ChatDataListener.HistoryInfo.unknown();
    private volatile boolean muteLoading = true;
    private volatile boolean banLoading = true;
    private volatile boolean historyLoading = true;
    private int muteRequestTick = 0;
    private int banRequestTick = 0;
    private int historyRequestTick = 0;
    private int pendingMuteSendTick = -1;
    private int pendingBanSendTick = -1;
    private int pendingHistorySendTick = -1;
    private int muteRetryCount = 0;
    private int banRetryCount = 0;
    private int historyRetryCount = 0;
    private int historyCurrentPage = 1;
    private int historyTotalPages = 1;
    private String historyRequestCommand = "";
    private boolean muteExpanded = false;
    private boolean banExpanded = false;
    private boolean historyExpanded = false;
    private int muteDetailScrollOffset = 0;
    private int muteDetailX = 0;
    private int muteDetailY = 0;
    private int muteDetailW = 0;
    private int muteDetailH = 0;
    private int muteDetailVisibleRows = 0;
    private int banDetailScrollOffset = 0;
    private int banDetailX = 0;
    private int banDetailY = 0;
    private int banDetailW = 0;
    private int banDetailH = 0;
    private int banDetailVisibleRows = 0;
    private int historyDetailScrollOffset = 0;
    private int historyDetailX = 0;
    private int historyDetailY = 0;
    private int historyDetailW = 0;
    private int historyDetailH = 0;
    private int historyDetailVisibleRows = 0;

    private List<String> bannedAlts = new ArrayList<>();
    private List<String> onlineAlts = new ArrayList<>();
    private List<String> offlineAlts = new ArrayList<>();

    private int bannedScrollOffset = 0;
    private int onlineScrollOffset = 0;
    private int offlineScrollOffset = 0;

    private int bannedListX = 0;
    private int bannedListY = 0;
    private int bannedListW = 0;
    private int bannedListH = 0;
    private int bannedVisibleRows = 0;

    private int onlineListX = 0;
    private int onlineListY = 0;
    private int onlineListW = 0;
    private int onlineListH = 0;
    private int onlineVisibleRows = 0;

    private int offlineListX = 0;
    private int offlineListY = 0;
    private int offlineListW = 0;
    private int offlineListH = 0;
    private int offlineVisibleRows = 0;

    private static final String[][] BAN_REASONS = {
            {"Abusing Exploits", "Abusing exploits"}, {"Auto Clicker", "Auto clicker"},
            {"Block Glitching", "Block glitching"}, {"Kill Farming", "Kill farming"},
            {"Major Griefing", "Major griefing"}, {"Minor Griefing", "Minor griefing"},
            {"NSFW Images", "NSFW images"}, {"PScale Abuse", "PScale abuse"},
            {"RG Abuse", "RG abuse"}, {"Scamming on Auction", "Scamming on auction"},
            {"Scamming Players", "Scamming players"}, {"Setting Home Near RG", "Setting home near other rgs"},
            {"Spawn Killing", "Spawn killing"}, {"TP Killing", "TP killing"},
            {"Trespassing", "Trespassing/naughty player"}, {"Warp Killing", "Warp killing"},
            {"Auto Reconnect", "Auto reconnect"}, {"Hacks", "Hacks"},
            {"Helping Hackers", "Helping hackers"}, {"Xray", "Xray"}
    };

    private static final String[][] MUTE_REASONS = {
            {"Advertising", "Advertising"}, {"Begging", "Begging"},
            {"Flooding the Chat", "Flooding the chat"}, {"Impersonating", "Impersonating"},
            {"Insulting Others", "Insulting others"}, {"Racism", "Racism"},
            {"Spamming the Chat", "Spamming the chat"}
    };

    private static final String[][] WARN_REASONS = {
            {"Advertising", "Advertising"}, {"Begging", "Begging"},
            {"Flooding the Chat", "Flooding the chat"}, {"Impersonating", "Impersonating"},
            {"Insulting Others", "Insulting others"}, {"Racism", "Racism"},
            {"Spamming the Chat", "Spamming the chat"},
            {"Setting Home Near RG", "Setting home near other rgs"}
    };

    private static final String[][] UNITS = {
            {"Seconds", "s"}, {"Minutes", "min"}, {"Hours", "h"},
            {"Days", "d"}, {"Weeks", "w"}, {"Months", "m"}
    };
    private static final String[][] SERVER_SCOPES = {
            {"Survival", "server:surv"},
            {"Anarchy", "server:anarchy"},
            {"Op Survival", "server:opsurv"},
            {"Global", "server:*"}
    };
    private static final String[][] BAN_SERVER_SCOPES = {
            {"Survival", "server:surv"},
            {"Anarchy", "server:anarchy"},
            {"Op Survival", "server:opsurv"},
            {"Global", "server:*"},
            {"KitPvP", "server:kitpvp"}
    };
    private static final String[][] KITPVP_SCOPES = {
            {"KitPvP-1", "server:kitpvp-1"},
            {"KitPvP-2", "server:kitpvp-2"},
            {"Both", "both"}
    };

    private static final int ENTRY_PLAYER = 0;
    private static final int ENTRY_BY = 1;
    private static final int ENTRY_REASON = 2;
    private static final int ENTRY_DATE = 3;
    private static final int ENTRY_DURATION = 4;
    private static final int ENTRY_SERVER = 5;
    private static final int ENTRY_FLAGS = 6;

    public ModerationScreen(String username) {
        super(Component.empty());
        this.targetUsername = username;
    }

    @Override
    protected void init() {
        LOGGER.info("[MBERRY] ModerationScreen init for {}", targetUsername);
        ChatDataListener.beginLookupSession(targetUsername);
        buildWidgets();
        requestAllLookupData();
    }

    @Override
    public void tick() {
        super.tick();
        screenTick++;

        if (step == Step.REASON && searchInput != null) {
            String current = searchInput.getValue();
            if (!current.equals(lastSearch)) {
                rebuildReasonButtons(current);
                lastSearch = current;
            }
        }

        if (pendingMuteSendTick >= 0 && screenTick >= pendingMuteSendTick) {
            pendingMuteSendTick = -1;
            LOGGER.info("[MBERRY] Sending delayed checkmute for {}", targetUsername);
            Minecraft.getInstance().player.connection.sendCommand("checkmute " + targetUsername + " server:*");
        }
        if (pendingBanSendTick >= 0 && screenTick >= pendingBanSendTick) {
            pendingBanSendTick = -1;
            LOGGER.info("[MBERRY] Sending delayed checkban for {}", targetUsername);
            Minecraft.getInstance().player.connection.sendCommand("checkban " + targetUsername + " server:*");
        }
        if (pendingHistorySendTick >= 0 && screenTick >= pendingHistorySendTick) {
            pendingHistorySendTick = -1;
            LOGGER.info("[MBERRY] Sending delayed history for {}", targetUsername);
            if (!historyRequestCommand.isBlank()) {
                Minecraft.getInstance().player.connection.sendCommand(historyRequestCommand);
            }
        }

        if (altsLoading) {
            if (ChatDataListener.lastAltsResult != null
                    && targetUsername.equalsIgnoreCase(ChatDataListener.lastAltsUsername)) {
                applyAltsEntries(ChatDataListener.lastAltsEntries, ChatDataListener.lastAltsResult);
            } else if (screenTick - altsRequestTick > ALTS_TIMEOUT_TICKS) {
                altsLoading = false;
                altsText = "No alts response yet. Click Refresh.";
                clearAltsLists();
            }
        }

        if (muteLoading) {
            if (ChatDataListener.lastMuteUsername != null
                    && targetUsername.equalsIgnoreCase(ChatDataListener.lastMuteUsername)) {
                applyMuteInfo(ChatDataListener.lastMuteInfo);
            } else if (muteRetryCount < 2 && screenTick - muteRequestTick > (35 + muteRetryCount * 30)) {
                muteRetryCount++;
                LOGGER.info("[MBERRY] No checkmute response yet, retry {} for {}", muteRetryCount, targetUsername);
                Minecraft.getInstance().player.connection.sendCommand("checkmute " + targetUsername + " server:*");
            } else if (screenTick - muteRequestTick > CHECK_TIMEOUT_TICKS) {
                LOGGER.warn("[MBERRY] checkmute timeout for {}", targetUsername);
                muteLoading = false;
                muteInfo = ChatDataListener.PunishmentInfo.unknown();
            }
        }

        if (banLoading) {
            if (ChatDataListener.lastBanUsername != null
                    && targetUsername.equalsIgnoreCase(ChatDataListener.lastBanUsername)) {
                applyBanInfo(ChatDataListener.lastBanInfo);
            } else if (banRetryCount < 2 && screenTick - banRequestTick > (40 + banRetryCount * 30)) {
                banRetryCount++;
                LOGGER.info("[MBERRY] No checkban response yet, retry {} for {}", banRetryCount, targetUsername);
                Minecraft.getInstance().player.connection.sendCommand("checkban " + targetUsername + " server:*");
            } else if (screenTick - banRequestTick > CHECK_TIMEOUT_TICKS) {
                LOGGER.warn("[MBERRY] checkban timeout for {}", targetUsername);
                banLoading = false;
                banInfo = ChatDataListener.PunishmentInfo.unknown();
            }
        }

        if (historyLoading) {
            if (ChatDataListener.lastHistoryUsername != null
                    && targetUsername.equalsIgnoreCase(ChatDataListener.lastHistoryUsername)) {
                applyHistory(ChatDataListener.lastHistoryInfo);
            } else if (historyRetryCount < 1 && screenTick - historyRequestTick > 45) {
                historyRetryCount++;
                LOGGER.info("[MBERRY] No history response yet, retry {} for {}", historyRetryCount, targetUsername);
                if (!historyRequestCommand.isBlank()) {
                    Minecraft.getInstance().player.connection.sendCommand(historyRequestCommand);
                }
            } else if (screenTick - historyRequestTick > CHECK_TIMEOUT_TICKS) {
                LOGGER.warn("[MBERRY] history timeout for {}", targetUsername);
                historyLoading = false;
                historyInfo = ChatDataListener.HistoryInfo.unknown();
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        int x = left();
        int y = top();
        int cx = x + PANEL_W / 2;

        drawContainer(g, x, y, PANEL_W, PANEL_H);
        super.render(g, mouseX, mouseY, delta);

        if (step == Step.HOME) {
            renderHome(g, x, y, cx, mouseX, mouseY);
        } else if (step == Step.REASON) {
            g.drawCenteredString(font, action.toUpperCase() + " - Select reason", cx, y + 10, 0xFFFFFFFF);
            drawDivider(g, x + 12, y + 22, PANEL_W - 24);
            g.drawString(font, "Custom reason:", x + 170, y + PANEL_H - 104, 0xFF9AA4C8, false);
        } else {
            g.drawCenteredString(font, action.toUpperCase() + " - More options", cx, y + 10, 0xFFFFFFFF);
            drawDivider(g, x + 12, y + 22, PANEL_W - 24);
            int sectionX = cx - 300;
            int sectionW = 600;
            boolean showKitpvpChoices = "ban".equals(action) && "server:kitpvp".equals(serverScope);
            int serverY;
            int serverH;
            int silentY;

            if (!"warn".equals(action)) {
                int durationY = y + 40;
                g.fill(sectionX, durationY, sectionX + sectionW, durationY + 98, 0x22131A2D);
                g.renderOutline(sectionX, durationY, sectionW, 98, 0x553A4A8C);
                g.drawString(font, "Duration", sectionX + 10, durationY + 6, 0xFFB3B8CC, false);
                serverY = y + 148;
            } else {
                serverY = y + 64;
            }
            serverH = showKitpvpChoices ? 112 : ("ban".equals(action) ? 88 : 74);
            silentY = serverY + serverH + 8;

            g.fill(sectionX, serverY, sectionX + sectionW, serverY + serverH, 0x22131A2D);
            g.renderOutline(sectionX, serverY, sectionW, serverH, 0x553A4A8C);
            g.drawString(font, "Server Scope", sectionX + 10, serverY + 6, 0xFF9AA4C8, false);
            if (showKitpvpChoices) {
                g.drawString(font, "KitPvP target", sectionX + 10, serverY + 58, 0xFF9AA4C8, false);
            }

            g.fill(sectionX, silentY, sectionX + sectionW, silentY + 48, 0x22131A2D);
            g.renderOutline(sectionX, silentY, sectionW, 48, 0x553A4A8C);
            g.drawString(font, "Silent Option", sectionX + 10, silentY + 6, 0xFF9AA4C8, false);

            String durationPart = "warn".equals(action) ? "" : resolveDurationArgument();
            boolean durationReady = "warn".equals(action) || !durationPart.isEmpty();
            boolean serverReady = isServerSelectionReady();
            String serverPreview = getServerPreview();
            String preview = durationReady
                    ? "/" + action + " " + targetUsername
                    + ("warn".equals(action) ? " " : " " + durationPart + " ")
                    + reason + " " + serverPreview + (silent ? " -s" : "")
                    : "Select duration to continue";
            if (!serverReady) {
                preview = "Select server scope to continue";
            }
            int color = (durationReady && serverReady) ? 0xFF8BFFAA : 0xFF777C93;
            g.drawCenteredString(font, preview, cx, y + PANEL_H - 76, color);
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float delta) {
        // Intentionally blank because the screen draws its own background.
    }

    @Override
    public void onClose() {
        ChatDataListener.endLookupSession(targetUsername);
        ChatDataListener.onAltsLine = null;
        ChatDataListener.onMuteInfo = null;
        ChatDataListener.onBanInfo = null;
        ChatDataListener.onHistoryInfo = null;
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (step != Step.HOME) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        int mx = (int) mouseX;
        int my = (int) mouseY;
        int delta = scrollY > 0 ? -1 : 1;

        if (muteExpanded && isInside(mx, my, muteDetailX, muteDetailY, muteDetailW, muteDetailH)) {
            int total = wrapLines(getMuteDetailLines(), Math.max(1, muteDetailW - 10)).size();
            muteDetailScrollOffset = scrollColumn(muteDetailScrollOffset, total, muteDetailVisibleRows, delta);
            return true;
        }

        if (banExpanded && isInside(mx, my, banDetailX, banDetailY, banDetailW, banDetailH)) {
            int total = wrapLines(getBanDetailLines(), Math.max(1, banDetailW - 10)).size();
            banDetailScrollOffset = scrollColumn(banDetailScrollOffset, total, banDetailVisibleRows, delta);
            return true;
        }

        if (historyExpanded && isInside(mx, my, historyDetailX, historyDetailY, historyDetailW, historyDetailH)) {
            int total = wrapLines(getHistoryDetailLines(), Math.max(1, historyDetailW - 10)).size();
            historyDetailScrollOffset = scrollColumn(historyDetailScrollOffset, total, historyDetailVisibleRows, delta);
            return true;
        }

        if (isInside(mx, my, bannedListX, bannedListY, bannedListW, bannedListH)) {
            bannedScrollOffset = scrollColumn(bannedScrollOffset, bannedAlts.size(), bannedVisibleRows, delta);
            return true;
        }
        if (isInside(mx, my, onlineListX, onlineListY, onlineListW, onlineListH)) {
            onlineScrollOffset = scrollColumn(onlineScrollOffset, onlineAlts.size(), onlineVisibleRows, delta);
            return true;
        }
        if (isInside(mx, my, offlineListX, offlineListY, offlineListW, offlineListH)) {
            offlineScrollOffset = scrollColumn(offlineScrollOffset, offlineAlts.size(), offlineVisibleRows, delta);
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        if (event.button() == 0 && step == Step.HOME && !banExpanded && !muteExpanded && !historyExpanded && !altsLoading) {
            int mx = (int) event.x();
            int my = (int) event.y();

            String picked = pickAltFromColumn(mx, my, bannedAlts, bannedScrollOffset, bannedListX, bannedListY, bannedListW, bannedListH, bannedVisibleRows);
            if (picked == null) {
                picked = pickAltFromColumn(mx, my, onlineAlts, onlineScrollOffset, onlineListX, onlineListY, onlineListW, onlineListH, onlineVisibleRows);
            }
            if (picked == null) {
                picked = pickAltFromColumn(mx, my, offlineAlts, offlineScrollOffset, offlineListX, offlineListY, offlineListW, offlineListH, offlineVisibleRows);
            }

            if (picked != null && !picked.equalsIgnoreCase(targetUsername)) {
                Minecraft.getInstance().setScreen(new ModerationScreen(picked));
                return true;
            }
        }
        return super.mouseClicked(event, bl);
    }

    private void buildWidgets() {
        String savedSearch = searchInput != null ? searchInput.getValue() : "";
        String savedCustomReason = customReasonInput != null ? customReasonInput.getValue() : customReasonDraft;
        String savedNumber = numberInput != null ? numberInput.getValue() : "";

        clearWidgets();
        reasonButtons.clear();
        searchInput = null;
        customReasonInput = null;
        numberInput = null;

        int x = left();
        int y = top();
        int cx = x + PANEL_W / 2;

        if (step == Step.HOME) {
            if (muteExpanded) {
                addBtn(x + 16, y + PANEL_H - 34, 88, 18, "Refresh Mute", 0xFF5F4A4A, 0xFF9A7070, this::requestCheckMute);
                addBtn(x + 112, y + PANEL_H - 34, 88, 18, "Copy Text", 0xFF35506C, 0xFF6EA3D9, this::copyMuteDetailsToClipboard);
                addBtn(x + PANEL_W - 184, y + PANEL_H - 34, 88, 18, "Collapse", 0xFF5F4A4A, 0xFF9A7070, () -> {
                    muteExpanded = false;
                    muteDetailScrollOffset = 0;
                    buildWidgets();
                });
                addBtn(x + PANEL_W - 92, y + PANEL_H - 34, 80, 18, "Close", 0xFF333355, 0xFF6666AA, this::onClose);
                return;
            }

            if (banExpanded) {
                addBtn(x + 16, y + PANEL_H - 34, 88, 18, "Refresh Ban", 0xFF5F4A4A, 0xFF9A7070, this::requestCheckBan);
                addBtn(x + 112, y + PANEL_H - 34, 88, 18, "Copy Text", 0xFF35506C, 0xFF6EA3D9, this::copyBanDetailsToClipboard);
                addBtn(x + PANEL_W - 184, y + PANEL_H - 34, 88, 18, "Collapse", 0xFF5F4A4A, 0xFF9A7070, () -> {
                    banExpanded = false;
                    banDetailScrollOffset = 0;
                    buildWidgets();
                });
                addBtn(x + PANEL_W - 92, y + PANEL_H - 34, 80, 18, "Close", 0xFF333355, 0xFF6666AA, this::onClose);
                return;
            }

            if (historyExpanded) {
                addBtn(x + 16, y + PANEL_H - 34, 96, 18, "Refresh", 0xFF5F4A4A, 0xFF9A7070, this::requestHistoryLatest);
                addBtn(x + 118, y + PANEL_H - 34, 70, 18, "Prev", 0xFF35506C, 0xFF6EA3D9, () -> requestHistoryPage(historyCurrentPage - 1));
                addBtn(x + 194, y + PANEL_H - 34, 70, 18, "Next", 0xFF35506C, 0xFF6EA3D9, () -> requestHistoryPage(historyCurrentPage + 1));
                addBtn(x + 270, y + PANEL_H - 34, 88, 18, "Copy Text", 0xFF35506C, 0xFF6EA3D9, this::copyHistoryDetailsToClipboard);
                addBtn(x + PANEL_W - 184, y + PANEL_H - 34, 88, 18, "Collapse", 0xFF5F4A4A, 0xFF9A7070, () -> {
                    historyExpanded = false;
                    historyDetailScrollOffset = 0;
                    buildWidgets();
                });
                addBtn(x + PANEL_W - 92, y + PANEL_H - 34, 80, 18, "Close", 0xFF333355, 0xFF6666AA, this::onClose);
                return;
            }

            int actionsX = x + 18;
            int actionsY = y + 34;
            int buttonW = 206;
            int buttonH = 18;
            int gapY = 8;

            addBtn(actionsX + 8, actionsY + 24, buttonW, buttonH, "Warn", 0xFF0E8A3E, 0xFF17C45A, () -> {
                action = "warn";
                serverScope = "";
                kitpvpScope = "";
                silent = false;
                step = Step.REASON;
                buildWidgets();
            });
            addBtn(actionsX + 8, actionsY + 24 + (buttonH + gapY), buttonW, buttonH, "Mute", 0xFF7A7A7A, 0xFFB8B8B8, () -> {
                action = "mute";
                serverScope = "";
                kitpvpScope = "";
                silent = false;
                step = Step.REASON;
                buildWidgets();
            });
            addBtn(actionsX + 8, actionsY + 24 + (buttonH + gapY) * 2, buttonW, buttonH, "Ban", 0xFFB32323, 0xFFFF5050, () -> {
                action = "ban";
                serverScope = "";
                kitpvpScope = "";
                silent = false;
                step = Step.REASON;
                buildWidgets();
            });

            addBtn(actionsX, y + PANEL_H - 34, 92, 18, "Refresh All", 0xFF315173, 0xFF4C79AB, this::requestAllLookupData);
            addBtn(x + PANEL_W - 100, y + PANEL_H - 34, 80, 18, "Close", 0xFF333355, 0xFF6666AA, this::onClose);

            int leftX = x + 12;
            int leftW = 236;
            int cardGap = 10;
            int actionsH = 112;
            int muteH = 70;
            int muteY = actionsY + actionsH + cardGap;
            int banY = muteY + muteH + cardGap;

            addBtn(leftX + leftW - 76, muteY + 4, 68, 16, "Expand", 0xFF5F4A4A, 0xFF9A7070, () -> {
                muteExpanded = true;
                muteDetailScrollOffset = 0;
                buildWidgets();
            });
            addBtn(leftX + leftW - 76, banY + 4, 68, 16, "Expand", 0xFF5F4A4A, 0xFF9A7070, () -> {
                banExpanded = true;
                banDetailScrollOffset = 0;
                buildWidgets();
            });
            addBtn(leftX + leftW - 98, y + PANEL_H - 58, 90, 18, "View History", 0xFF35506C, 0xFF6EA3D9, () -> {
                historyExpanded = true;
                historyDetailScrollOffset = 0;
                buildWidgets();
            });
        } else if (step == Step.REASON) {
            searchInput = new EditBox(font, cx - 90, y + 34, 180, 16, Component.empty());
            searchInput.setHint(Component.literal("Search reason..."));
            searchInput.setMaxLength(40);
            searchInput.setValue(savedSearch);
            addRenderableWidget(searchInput);
            searchInput.setFocused(true);
            searchInput.moveCursorToEnd(false);

            rebuildReasonButtons(savedSearch);
            lastSearch = savedSearch;

            customReasonInput = new EditBox(font, cx - 150, y + PANEL_H - 90, 220, 16, Component.empty());
            customReasonInput.setHint(Component.literal("Type custom reason..."));
            customReasonInput.setMaxLength(64);
            customReasonInput.setValue(savedCustomReason);
            addRenderableWidget(customReasonInput);

            addBtn(cx + 76, y + PANEL_H - 90, 86, 16, "Use Custom", 0xFF5A3D72, 0xFF8D62B5, this::useCustomReason);

            addBtn(x + 16, y + PANEL_H - 34, 80, 18, "Back", 0xFF333355, 0xFF6666AA, () -> {
                customReasonDraft = customReasonInput != null ? customReasonInput.getValue() : customReasonDraft;
                step = Step.HOME;
                buildWidgets();
            });
            addBtn(x + PANEL_W - 96, y + PANEL_H - 34, 80, 18, "Close", 0xFF333355, 0xFF6666AA, this::onClose);
        } else {
            int durationTop = y + 58;
            int optionsTop;
            int silentTop;

            if (!"warn".equals(action)) {
                numberInput = new EditBox(font, cx - 44, durationTop, 88, 16, Component.empty());
                numberInput.setHint(Component.literal("amount"));
                numberInput.setFilter(s -> s.matches("\\d*"));
                numberInput.setMaxLength(4);
                numberInput.setValue(savedNumber);
                addRenderableWidget(numberInput);

                int unitW = 76;
                int unitH = 16;
                int gap = 6;
                int startX = cx - (3 * unitW + 2 * gap) / 2;
                for (int i = 0; i < UNITS.length; i++) {
                    final String selectedUnit = UNITS[i][1];
                    int ux = startX + (i % 3) * (unitW + gap);
                    int uy = durationTop + 26 + (i / 3) * (unitH + gap);
                    boolean selected = selectedUnit.equals(unit);

                    addRenderableWidget(new OutlinedButton(
                            ux, uy, unitW, unitH,
                            Component.literal(UNITS[i][0]),
                            selected ? 0xFFFFFFFF : 0xFF333355,
                            0xFF8888FF,
                            selected ? 0xFFFFFFFF : 0xFFCCCCCC,
                            b -> {
                                unit = selectedUnit;
                                buildWidgets();
                            }
                    ));
                }
                optionsTop = y + 148;
            } else {
                optionsTop = y + 64;
            }

            String[][] scopeOptions = "ban".equals(action) ? BAN_SERVER_SCOPES : SERVER_SCOPES;
            int scopeColumns = scopeOptions.length > 4 ? 3 : 2;
            int scopeW = scopeColumns == 3 ? 98 : 116;
            int scopeH = 16;
            int scopeGap = 8;
            int scopeStartX = cx - (scopeColumns * scopeW + (scopeColumns - 1) * scopeGap) / 2;
            for (int i = 0; i < scopeOptions.length; i++) {
                final String scopeCode = scopeOptions[i][1];
                int sx = scopeStartX + (i % scopeColumns) * (scopeW + scopeGap);
                int sy = optionsTop + (i / scopeColumns) * (scopeH + 6) + 22;
                boolean selected = scopeCode.equals(serverScope);

                addRenderableWidget(new OutlinedButton(
                        sx, sy, scopeW, scopeH,
                        Component.literal(scopeOptions[i][0]),
                        selected ? 0xFFFFFFFF : 0xFF35506C,
                        0xFF6EA3D9,
                        selected ? 0xFFFFFFFF : 0xFFCCD5E2,
                        b -> {
                            serverScope = scopeCode;
                            if (!"server:kitpvp".equals(serverScope)) {
                                kitpvpScope = "";
                            }
                            buildWidgets();
                        }
                ));
            }

            int scopeRows = (scopeOptions.length + scopeColumns - 1) / scopeColumns;
            int kitTop = optionsTop + 22 + scopeRows * (scopeH + 6) + 6;
            if ("ban".equals(action) && "server:kitpvp".equals(serverScope)) {
                int kitW = 104;
                int kitGap = 8;
                int kitStartX = cx - (3 * kitW + 2 * kitGap) / 2;
                for (int i = 0; i < KITPVP_SCOPES.length; i++) {
                    final String kitCode = KITPVP_SCOPES[i][1];
                    boolean selected = kitCode.equals(kitpvpScope);
                    addRenderableWidget(new OutlinedButton(
                            kitStartX + i * (kitW + kitGap),
                            kitTop,
                            kitW,
                            scopeH,
                            Component.literal(KITPVP_SCOPES[i][0]),
                            selected ? 0xFFFFFFFF : 0xFF4A3C60,
                            0xFF7A63A8,
                            selected ? 0xFFFFFFFF : 0xFFD8D0EB,
                            b -> {
                                kitpvpScope = kitCode;
                                buildWidgets();
                            }
                    ));
                }
                silentTop = optionsTop + 120;
            } else {
                silentTop = optionsTop + ("ban".equals(action) ? 96 : 82);
            }

            addBtn(cx - 60, silentTop + 20, 120, 18,
                    "Silent: " + (silent ? "ON" : "OFF"),
                    silent ? 0xFF7A3B3B : 0xFF3F4E63,
                    silent ? 0xFFB85353 : 0xFF647D9C,
                    () -> {
                        silent = !silent;
                        buildWidgets();
                    });

            addBtn(cx - 134, y + PANEL_H - 58, 84, 18, "Copy Cmd", 0xFF35506C, 0xFF6EA3D9, this::copyCommandPreviewToClipboard);
            addBtn(cx - 40, y + PANEL_H - 58, 80, 18, "Confirm", 0xFF006600, 0xFF00CC66, this::confirm);
            addBtn(x + 16, y + PANEL_H - 34, 80, 18, "Back", 0xFF333355, 0xFF6666AA, () -> {
                unit = "";
                step = Step.REASON;
                buildWidgets();
            });
            addBtn(x + PANEL_W - 96, y + PANEL_H - 34, 80, 18, "Close", 0xFF333355, 0xFF6666AA, this::onClose);
        }
    }

    private void renderHome(GuiGraphics g, int x, int y, int cx, int mouseX, int mouseY) {
        int leftX = x + 12;
        int leftW = 236;
        int cardGap = 10;

        int actionsH = 112;
        int muteH = 70;
        int banH = 96;

        int actionsY = y + 32;
        int muteY = actionsY + actionsH + cardGap;
        int banY = muteY + muteH + cardGap;

        int altsX = leftX + leftW + 8;
        int altsY = y + 34;
        int altsW = PANEL_W - leftW - 32;
        int altsH = PANEL_H - 98;

        g.drawCenteredString(font, "Moderation Panel - " + targetUsername, cx, y + 10, 0xFFFFFFFF);
        drawDivider(g, x + 12, y + 22, PANEL_W - 24);

        if (muteExpanded) {
            renderExpandedMuteDetails(g, x + 10, y + 30, PANEL_W - 20, PANEL_H - 76);
            return;
        }

        if (banExpanded) {
            renderExpandedBanDetails(g, x + 10, y + 30, PANEL_W - 20, PANEL_H - 76);
            return;
        }
        if (historyExpanded) {
            renderExpandedHistoryDetails(g, x + 10, y + 30, PANEL_W - 20, PANEL_H - 76);
            return;
        }

        g.fill(leftX, actionsY, leftX + leftW, actionsY + actionsH, ACTION_BOX_BG);
        g.renderOutline(leftX, actionsY, leftW, actionsH, ACTION_BOX_BORDER);
        g.drawString(font, "Actions", leftX + 8, actionsY + 6, 0xFFB4BBFF, false);

        // Slim side accents to avoid color bleeding through transparent UI layers.
        g.fill(leftX + 6, actionsY + 26, leftX + 9, actionsY + 42, WARN_ACCENT);
        g.fill(leftX + 6, actionsY + 52, leftX + 9, actionsY + 68, MUTE_ACCENT);
        g.fill(leftX + 6, actionsY + 78, leftX + 9, actionsY + 94, BAN_ACCENT);

        renderPunishmentBox(g, leftX, muteY, leftW, muteH,
                "Active Mutes", 0xFFE6D18B, muteLoading, muteInfo, true,
                "No active mutes.");

        renderPunishmentBox(g, leftX, banY, leftW, banH,
                "Active Bans", 0xFFFFA8A8, banLoading, banInfo, false,
                "No active bans.");

        g.fill(altsX, altsY, altsX + altsW, altsY + altsH, ALTS_BOX_BG);
        g.renderOutline(altsX, altsY, altsW, altsH, ALTS_BOX_BORDER);

        g.drawString(font, "Alts by status", altsX + 8, altsY + 6, 0xFFB4BBFF, false);

        String status;
        if (altsLoading) {
            int dots = (screenTick / 10) % 4;
            status = "Scanning" + ".".repeat(dots);
        } else {
            int total = bannedAlts.size() + onlineAlts.size() + offlineAlts.size();
            status = total + " account" + (total == 1 ? "" : "s") + " tracked";
        }
        g.drawString(font, status, altsX + 8, altsY + 18, 0xFFA7AEC9, false);

        int columnsTop = altsY + 32;
        int columnsBottom = altsY + altsH - 18;
        int columnsHeight = columnsBottom - columnsTop;
        int gap = 6;
        int colW = (altsW - 16 - gap * 2) / 3;

        int bannedX = altsX + 6;
        int onlineX = bannedX + colW + gap;
        int offlineX = onlineX + colW + gap;

        renderAltColumn(g, mouseX, mouseY, AltColumn.BANNED, bannedX, columnsTop, colW, columnsHeight,
                "Banned", BANNED_COLOR, bannedAlts, bannedScrollOffset);
        renderAltColumn(g, mouseX, mouseY, AltColumn.ONLINE, onlineX, columnsTop, colW, columnsHeight,
                "Online", ONLINE_COLOR, onlineAlts, onlineScrollOffset);
        renderAltColumn(g, mouseX, mouseY, AltColumn.OFFLINE, offlineX, columnsTop, colW, columnsHeight,
                "Offline", OFFLINE_COLOR, offlineAlts, offlineScrollOffset);

        g.drawString(font, "Scroll inside a column | Click a name to open panel", altsX + 8, altsY + altsH - 10, 0xFF7A82A1, false);

        if (!altsLoading && bannedAlts.isEmpty() && onlineAlts.isEmpty() && offlineAlts.isEmpty()) {
            g.drawCenteredString(font, altsText, altsX + altsW / 2, columnsTop + 20, 0xFF767D98);
        }
    }

    private void renderPunishmentBox(
            GuiGraphics g,
            int x,
            int y,
            int w,
            int h,
            String title,
            int titleColor,
            boolean loading,
            ChatDataListener.PunishmentInfo info,
            boolean compact,
            String inactiveText
    ) {
        g.fill(x, y, x + w, y + h, 0x44101520);
        g.renderOutline(x, y, w, h, 0xFF394A7A);
        g.drawString(font, title, x + 8, y + 6, titleColor, false);
        g.fill(x + 6, y + 22, x + w - 6, y + 23, 0x553A4A8C);

        int textX = x + 8;
        int lineY = y + 28;
        int maxW = w - 14;

        if (loading) {
            int dots = (screenTick / 10) % 4;
            g.drawString(font, "Checking" + ".".repeat(dots), textX, lineY, 0xFF9AA4C8, false);
            return;
        }

        if (!info.active()) {
            String fallback = info.rawLines().isEmpty() ? inactiveText : info.rawLines().get(0);
            g.drawString(font, clipText(fallback, maxW), textX, lineY, 0xFF8E96B2, false);
            return;
        }

        List<String> lines = new ArrayList<>();
        List<String[]> entries = parseBanEntries(info.rawLines());
        if (!compact && !entries.isEmpty()) {
            String[] first = entries.get(0);
            if (entries.size() > 1) {
                lines.add("Entries: " + entries.size());
            }
            lines.add("Player: " + valueOrDash(first[ENTRY_PLAYER]));
            lines.add("By: " + valueOrDash(first[ENTRY_BY]));
            lines.add("Reason: " + valueOrDash(first[ENTRY_REASON]));
            lines.add("For: " + valueOrDash(first[ENTRY_DURATION]));
            lines.add("On: " + valueOrDash(first[ENTRY_SERVER]));
            drawPunishmentLines(g, lines, textX, lineY, maxW, y, h, 0xFFE2E6F8);
            return;
        }

        lines.add("By: " + valueOrDash(info.actor()));
        lines.add("Reason: " + valueOrDash(info.reason()));
        lines.add("For: " + valueOrDash(info.duration()));
        lines.add("On: " + valueOrDash(info.server()));
        drawPunishmentLines(g, lines, textX, lineY, maxW, y, h, 0xFFE2E6F8);
    }

    private void renderExpandedBanDetails(GuiGraphics g, int x, int y, int w, int h) {
        banDetailX = x + 8;
        banDetailY = y + 20;
        banDetailW = w - 16;
        banDetailH = h - 28;

        g.fill(x, y, x + w, y + h, 0xCC111827);
        g.renderOutline(x, y, w, h, 0xFF8A6B6B);
        g.drawString(font, "Active Ban Details", x + 8, y + 6, 0xFFFFBABA, false);
        g.fill(x + 6, y + 16, x + w - 6, y + 17, 0x775A3F3F);

        List<String> lines = wrapLines(getBanDetailLines(), banDetailW - 10);
        if (lines.isEmpty()) {
            g.drawString(font, "No ban data yet.", banDetailX, banDetailY, 0xFF8E96B2, false);
            return;
        }

        banDetailVisibleRows = Math.max(1, banDetailH / 10);
        int maxOffset = Math.max(0, lines.size() - banDetailVisibleRows);
        banDetailScrollOffset = Mth.clamp(banDetailScrollOffset, 0, maxOffset);

        for (int i = 0; i < banDetailVisibleRows; i++) {
            int idx = banDetailScrollOffset + i;
            if (idx >= lines.size()) {
                break;
            }
            g.drawString(font, clipText(lines.get(idx), banDetailW - 8), banDetailX, banDetailY + i * 10, 0xFFD2D8EE, false);
        }

        drawScrollBar(g, banDetailX + banDetailW - 4, banDetailY, banDetailH, lines.size(), banDetailVisibleRows, banDetailScrollOffset);
        g.drawString(font, "Mouse wheel scroll", x + w - 92, y + h - 11, 0xFF7A82A1, false);
    }

    private void renderExpandedMuteDetails(GuiGraphics g, int x, int y, int w, int h) {
        muteDetailX = x + 8;
        muteDetailY = y + 20;
        muteDetailW = w - 16;
        muteDetailH = h - 28;

        g.fill(x, y, x + w, y + h, 0xCC121B22);
        g.renderOutline(x, y, w, h, 0xFF8A7A5B);
        g.drawString(font, "Active Mute Details", x + 8, y + 6, 0xFFEFD08F, false);
        g.fill(x + 6, y + 16, x + w - 6, y + 17, 0x7760563E);

        List<String> lines = wrapLines(getMuteDetailLines(), muteDetailW - 10);
        if (lines.isEmpty()) {
            g.drawString(font, "No mute data yet.", muteDetailX, muteDetailY, 0xFF8E96B2, false);
            return;
        }

        muteDetailVisibleRows = Math.max(1, muteDetailH / 10);
        int maxOffset = Math.max(0, lines.size() - muteDetailVisibleRows);
        muteDetailScrollOffset = Mth.clamp(muteDetailScrollOffset, 0, maxOffset);

        for (int i = 0; i < muteDetailVisibleRows; i++) {
            int idx = muteDetailScrollOffset + i;
            if (idx >= lines.size()) {
                break;
            }
            g.drawString(font, clipText(lines.get(idx), muteDetailW - 8), muteDetailX, muteDetailY + i * 10, 0xFFD2D8EE, false);
        }

        drawScrollBar(g, muteDetailX + muteDetailW - 4, muteDetailY, muteDetailH, lines.size(), muteDetailVisibleRows, muteDetailScrollOffset);
        g.drawString(font, "Mouse wheel scroll", x + w - 92, y + h - 11, 0xFF7A82A1, false);
    }

    private void renderExpandedHistoryDetails(GuiGraphics g, int x, int y, int w, int h) {
        historyDetailX = x + 8;
        historyDetailY = y + 20;
        historyDetailW = w - 16;
        historyDetailH = h - 28;

        g.fill(x, y, x + w, y + h, 0xCC141822);
        g.renderOutline(x, y, w, h, 0xFF5D6F9A);
        g.drawString(font, "Punishment History", x + 8, y + 6, 0xFFBCD6FF, false);
        g.drawString(font, "Page " + historyCurrentPage + " / " + historyTotalPages, x + w - 94, y + 6, 0xFF9FB2DF, false);
        g.fill(x + 6, y + 16, x + w - 6, y + 17, 0x77576A99);

        List<String> lines = getHistoryDetailLines();
        if (lines.isEmpty()) {
            g.drawString(font, "No history data yet.", historyDetailX, historyDetailY, 0xFF8E96B2, false);
            return;
        }

        historyDetailVisibleRows = Math.max(1, historyDetailH / 10);
        int maxOffset = Math.max(0, lines.size() - historyDetailVisibleRows);
        historyDetailScrollOffset = Mth.clamp(historyDetailScrollOffset, 0, maxOffset);

        for (int i = 0; i < historyDetailVisibleRows; i++) {
            int idx = historyDetailScrollOffset + i;
            if (idx >= lines.size()) {
                break;
            }
            String line = clipText(lines.get(idx), historyDetailW - 8);
            int color = 0xFFD2D8EE;
            String lower = line.toLowerCase();
            if (lower.startsWith("summary")) {
                color = 0xFFA5B4DE;
            } else if (lower.startsWith("entry #")) {
                color = 0xFF93B9FF;
            } else if (lower.startsWith("type: ban")) {
                color = 0xFFFFA2A2;
            } else if (lower.startsWith("type: unban")) {
                color = 0xFF9BE7A0;
            } else if (lower.startsWith("status:")) {
                color = 0xFFC2CAE2;
            } else if (lower.startsWith("actor:") || lower.startsWith("date:") || lower.startsWith("duration:") || lower.startsWith("server:")) {
                color = 0xFFB9C7EA;
            } else if (lower.startsWith("reason:")) {
                color = 0xFFE8EEFF;
            } else if (lower.startsWith("page ")) {
                color = 0xFFA5B4DE;
            }
            g.drawString(font, line, historyDetailX, historyDetailY + i * 10, color, false);
        }

        drawScrollBar(g, historyDetailX + historyDetailW - 4, historyDetailY, historyDetailH, lines.size(), historyDetailVisibleRows, historyDetailScrollOffset);
        g.drawString(font, "Mouse wheel scroll", x + w - 92, y + h - 11, 0xFF7A82A1, false);
    }

    private List<String> getBanDetailLines() {
        if (banLoading) {
            return List.of("Checking...");
        }
        if (banInfo == null || !banInfo.active()) {
            return List.of("No active bans.");
        }
        List<String[]> entries = parseBanEntries(banInfo.rawLines());
        if (!entries.isEmpty()) {
            List<String> out = new ArrayList<>();
            out.add("Detected " + entries.size() + " active ban entr" + (entries.size() == 1 ? "y" : "ies"));
            out.add("");
            for (int i = 0; i < entries.size(); i++) {
                String[] e = entries.get(i);
                out.add("Entry #" + (i + 1) + " - " + valueOrDash(e[ENTRY_PLAYER]));
                out.add("By: " + valueOrDash(e[ENTRY_BY]));
                out.add("Reason: " + valueOrDash(e[ENTRY_REASON]));
                out.add("Date: " + valueOrDash(e[ENTRY_DATE]));
                out.add("Duration: " + valueOrDash(e[ENTRY_DURATION]));
                out.add("Server: " + valueOrDash(e[ENTRY_SERVER]));
                out.add("Flags: " + valueOrDash(e[ENTRY_FLAGS]));
                if (i < entries.size() - 1) {
                    out.add("");
                }
            }
            return out;
        }
        List<String> fallback = new ArrayList<>();
        fallback.add("By: " + valueOrDash(banInfo.actor()));
        fallback.add("Reason: " + valueOrDash(banInfo.reason()));
        fallback.add("For: " + valueOrDash(banInfo.duration()));
        fallback.add("On: " + valueOrDash(banInfo.server()));
        fallback.add("Flags: " + valueOrDash(banInfo.flags()));
        return fallback;
    }

    private List<String> getMuteDetailLines() {
        if (muteLoading) {
            return List.of("Checking...");
        }
        if (muteInfo == null || !muteInfo.active()) {
            return List.of("No active mutes.");
        }

        List<String> out = new ArrayList<>();
        out.add("Active mute detected");
        out.add("");
        out.add("By: " + valueOrDash(muteInfo.actor()));
        out.add("Reason: " + valueOrDash(muteInfo.reason()));
        out.add("Date: " + valueOrDash(muteInfo.date()));
        out.add("Duration: " + valueOrDash(muteInfo.duration()));
        out.add("Server: " + valueOrDash(muteInfo.server()));
        out.add("Flags: " + valueOrDash(muteInfo.flags()));
        return out;
    }

    private List<String> getHistoryDetailLines() {
        if (historyLoading) {
            return List.of("Loading history...");
        }
        if (historyInfo == null || !historyInfo.found() || historyInfo.rawLines().isEmpty()) {
            return List.of("No history found.");
        }

        List<String> out = new ArrayList<>();
        out.add("Summary: " + targetUsername + " | page " + historyCurrentPage + " of " + historyTotalPages);
        out.add("");
        int entryIndex = 0;
        List<String> pendingEntry = null;
        boolean hasExplicitUnban = false;

        for (String raw : historyInfo.rawLines()) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String line = raw.trim();
            if (line.toLowerCase().contains("list of punishments")) {
                continue;
            }

            Matcher ban = HISTORY_BAN_LINE.matcher(line);
            if (ban.find()) {
                if (pendingEntry != null) {
                    out.addAll(pendingEntry);
                    if (!hasExplicitUnban) {
                        out.add("Unban: Not found (likely expired or still active)");
                    }
                    out.add("");
                }
                entryIndex++;
                pendingEntry = new ArrayList<>();
                pendingEntry.add("Entry #" + entryIndex);
                pendingEntry.add("Actor: " + ban.group(2));
                pendingEntry.add("Date: " + ban.group(3));
                pendingEntry.add("Duration: " + ban.group(4));
                pendingEntry.add("Reason: " + ban.group(5));
                pendingEntry.add("Server: " + ban.group(6));
                pendingEntry.add("Status: " + ban.group(7));
                hasExplicitUnban = false;
                continue;
            }

            Matcher unban = HISTORY_UNBAN_LINE.matcher(line);
            if (unban.find()) {
                if (pendingEntry != null) {
                    pendingEntry.add("Unbanned By: " + unban.group(2));
                    pendingEntry.add("Unban Server: " + unban.group(3));
                    hasExplicitUnban = true;
                    // Replace last status line with stronger status when unban is present.
                    for (int i = 0; i < pendingEntry.size(); i++) {
                        if (pendingEntry.get(i).startsWith("Status:")) {
                            pendingEntry.set(i, "Status: cancelled (explicit unban)");
                            break;
                        }
                    }
                }
                continue;
            }

            if (pendingEntry == null) {
                entryIndex++;
                out.add("Entry #" + entryIndex);
                out.add(line);
                out.add("");
            }
        }
        if (pendingEntry != null) {
            out.addAll(pendingEntry);
            if (!hasExplicitUnban) {
                out.add("Unban: Not found (likely expired or still active)");
            }
            out.add("");
        }
        return wrapLines(out, Math.max(1, historyDetailW - 10));
    }

    private void drawPunishmentLines(
            GuiGraphics g,
            List<String> lines,
            int x,
            int startY,
            int maxW,
            int boxY,
            int boxH,
            int color
    ) {
        int maxLines = Math.max(1, (boxH - 34) / 10);
        for (int i = 0; i < lines.size() && i < maxLines; i++) {
            int y = startY + i * 10;
            g.drawString(font, clipText(lines.get(i), maxW), x, y, color, false);
        }
    }

    private List<String> wrapLines(List<String> lines, int maxWidth) {
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                out.add("");
                continue;
            }
            String[] words = line.split(" ");
            StringBuilder current = new StringBuilder();
            for (String word : words) {
                String test = current.isEmpty() ? word : current + " " + word;
                if (font.width(test) <= maxWidth) {
                    current = new StringBuilder(test);
                } else {
                    if (!current.isEmpty()) {
                        out.add(current.toString());
                    }
                    current = new StringBuilder(word);
                }
            }
            if (!current.isEmpty()) {
                out.add(current.toString());
            }
        }
        return out;
    }

    private List<String[]> parseBanEntries(List<String> lines) {
        List<String[]> entries = new ArrayList<>();
        if (lines == null || lines.isEmpty()) {
            return entries;
        }

        String[] current = null;
        for (String raw : lines) {
            if (raw == null) {
                continue;
            }
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }

            String lower = line.toLowerCase();
            if (lower.startsWith("the player [") && lower.contains("is banned")) {
                current = new String[7];
                current[ENTRY_PLAYER] = extractPlayerName(line);
                entries.add(current);
                continue;
            }

            if (current == null) {
                continue;
            }

            if (lower.startsWith("banned by")) {
                current[ENTRY_BY] = valueAfterColon(line);
            } else if (lower.startsWith("reason:")) {
                current[ENTRY_REASON] = valueAfterColon(line);
            } else if (lower.startsWith("ban date:")) {
                current[ENTRY_DATE] = valueAfterColon(line);
            } else if (lower.startsWith("banned for:")) {
                current[ENTRY_DURATION] = valueAfterColon(line);
            } else if (lower.startsWith("banned on server:")) {
                current[ENTRY_SERVER] = valueAfterColon(line);
            } else if (lower.startsWith("ip ban:")) {
                current[ENTRY_FLAGS] = valueAfterColon(line);
            }
        }
        return entries;
    }

    private String extractPlayerName(String line) {
        int lb = line.indexOf('[');
        int dash = line.indexOf(" - ", lb + 1);
        int rb = line.indexOf(']', lb + 1);
        if (lb >= 0 && dash > lb && (rb < 0 || dash < rb)) {
            return line.substring(lb + 1, dash).trim();
        }
        return "";
    }

    private String valueAfterColon(String line) {
        int idx = line.indexOf(':');
        if (idx < 0 || idx == line.length() - 1) {
            return "";
        }
        return line.substring(idx + 1).trim();
    }

    private static String valueOrDash(String input) {
        return (input == null || input.isBlank()) ? "-" : input;
    }

    private static int countPunishmentEntries(List<String> lines) {
        int count = 0;
        for (String line : lines) {
            String l = line.toLowerCase();
            if (l.startsWith("the player [") && (l.contains("is banned") || l.contains("is muted"))) {
                count++;
            }
        }
        return Math.max(1, count);
    }

    private String clipText(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }

        String ellipsis = "...";
        int max = Math.max(0, maxWidth - font.width(ellipsis));
        String out = text;
        while (!out.isEmpty() && font.width(out) > max) {
            out = out.substring(0, out.length() - 1);
        }
        return out + ellipsis;
    }

    private void renderAltColumn(
            GuiGraphics g,
            int mouseX,
            int mouseY,
            AltColumn column,
            int x,
            int y,
            int w,
            int h,
            String title,
            int titleColor,
            List<String> names,
            int scroll
    ) {
        g.fill(x, y, x + w, y + h, 0x330C1222);
        g.renderOutline(x, y, w, h, 0x664A5A8D);

        g.drawCenteredString(font, title + " (" + names.size() + ")", x + w / 2, y + 4, titleColor);
        g.fill(x + 4, y + 14, x + w - 4, y + 15, 0x553A4A8C);

        int listX = x + 3;
        int listY = y + 18;
        int listW = w - 6;
        int listH = h - 22;

        int visibleRows = Math.max(1, listH / ALTS_ROW_H);
        int maxOffset = Math.max(0, names.size() - visibleRows);
        int clampedScroll = Mth.clamp(scroll, 0, maxOffset);
        setColumnScroll(column, clampedScroll);

        setColumnBounds(column, listX, listY, listW, listH, visibleRows);

        int contentW = listW - 8;
        for (int row = 0; row < visibleRows; row++) {
            int idx = clampedScroll + row;
            if (idx >= names.size()) {
                break;
            }

            int rowY = listY + row * ALTS_ROW_H;
            boolean hovered = isInside(mouseX, mouseY, listX + 1, rowY, contentW, ALTS_ROW_H);
            if (hovered) {
                g.fill(listX + 1, rowY, listX + contentW, rowY + ALTS_ROW_H, 0x334B5E9C);
            }

            String username = names.get(idx);
            int nameColor = username.equalsIgnoreCase(targetUsername) ? 0xFF8BE39E : (hovered ? 0xFFFFFFFF : titleColor);
            g.drawString(font, username, listX + 3, rowY + 1, nameColor, false);
        }

        drawScrollBar(g, listX + listW - 4, listY, listH, names.size(), visibleRows, clampedScroll);
    }

    private static int scrollColumn(int current, int totalRows, int visibleRows, int delta) {
        int maxOffset = Math.max(0, totalRows - Math.max(1, visibleRows));
        return Mth.clamp(current + delta, 0, maxOffset);
    }

    private String pickAltFromColumn(
            int mouseX,
            int mouseY,
            List<String> names,
            int scroll,
            int x,
            int y,
            int w,
            int h,
            int visibleRows
    ) {
        if (names.isEmpty() || visibleRows <= 0 || !isInside(mouseX, mouseY, x, y, w, h)) {
            return null;
        }

        int row = (mouseY - y) / ALTS_ROW_H;
        int idx = scroll + row;
        if (row < 0 || row >= visibleRows || idx >= names.size()) {
            return null;
        }

        return names.get(idx);
    }

    private void drawScrollBar(GuiGraphics g, int x, int y, int h, int totalRows, int visibleRows, int scrollOffset) {
        g.fill(x, y, x + 3, y + h, 0x55303B66);
        if (totalRows <= visibleRows || totalRows <= 0) {
            g.fill(x, y, x + 3, y + h, 0x99506AB3);
            return;
        }

        int thumbH = Math.max(10, (int) ((visibleRows / (double) totalRows) * h));
        int maxOffset = totalRows - visibleRows;
        int thumbTravel = h - thumbH;
        int thumbY = y + (int) ((scrollOffset / (double) maxOffset) * thumbTravel);
        g.fill(x, thumbY, x + 3, thumbY + thumbH, 0xFF7D92D9);
    }

    private void rebuildReasonButtons(String query) {
        for (OutlinedButton button : reasonButtons) {
            removeWidget(button);
        }
        reasonButtons.clear();

        int x = left();
        int y = top();
        int cx = x + PANEL_W / 2;
        int rowY = y + 56;
        String filter = query.toLowerCase();

        int shown = 0;
        for (String[] entry : getReasons()) {
            if (shown >= 9) {
                break;
            }

            String label = entry[0];
            if (!filter.isEmpty() && !label.toLowerCase().contains(filter)) {
                continue;
            }

            final String reasonCode = entry[1];
            final String reasonLabelText = label;
            OutlinedButton button = new OutlinedButton(
                    cx - 150, rowY, 300, 18,
                    Component.literal(label),
                    0xFF333366, 0xFF6666BB, 0xFFCCCCCC,
                    b -> pickReason(reasonCode, reasonLabelText)
            );
            addRenderableWidget(button);
            reasonButtons.add(button);
            shown++;
            rowY += 22;
        }
    }

    private void pickReason(String reasonCode, String reasonLabelText) {
        reason = reasonCode;
        reasonLabel = reasonLabelText;
        step = Step.DURATION;
        buildWidgets();
    }

    private void useCustomReason() {
        String custom = customReasonInput != null ? customReasonInput.getValue().trim() : "";
        customReasonDraft = custom;
        if (custom.isEmpty()) {
            return;
        }

        reason = custom;
        reasonLabel = "Custom";
        step = Step.DURATION;
        buildWidgets();
    }

    private void confirm() {
        if (!isServerSelectionReady()) {
            return;
        }
        if ("warn".equals(action)) {
            sendCommand("");
            return;
        }
        String resolvedDuration = resolveDurationArgument();
        if (!resolvedDuration.isEmpty()) {
            sendCommand(resolvedDuration);
        }
    }

    private void sendCommand(String duration) {
        onClose();
        ClientCommandHandler.enqueueCommands(buildModerationCommands(duration), COMMAND_CHAIN_DELAY_TICKS);
    }

    private List<String> buildModerationCommands(String duration) {
        List<String> commands = new ArrayList<>();
        for (String serverTarget : resolveServerTargets()) {
            if ("warn".equals(action)) {
                commands.add("warn " + targetUsername + " " + reason + " " + serverTarget + (silent ? " -s" : ""));
            } else {
                commands.add(action + " " + targetUsername + " " + duration + " " + reason + " " + serverTarget + (silent ? " -s" : ""));
            }
        }
        return commands;
    }

    private void copyCommandPreviewToClipboard() {
        if (!isServerSelectionReady()) {
            return;
        }
        String duration = "";
        if (!"warn".equals(action)) {
            duration = resolveDurationArgument();
            if (duration.isEmpty()) {
                return;
            }
        }
        List<String> commands = buildModerationCommands(duration).stream()
                .map(cmd -> "/" + cmd)
                .collect(Collectors.toList());
        Minecraft.getInstance().keyboardHandler.setClipboard(String.join("\n", commands));
    }

    private void copyBanDetailsToClipboard() {
        Minecraft.getInstance().keyboardHandler.setClipboard(String.join("\n", getBanDetailLines()));
    }

    private void copyMuteDetailsToClipboard() {
        Minecraft.getInstance().keyboardHandler.setClipboard(String.join("\n", getMuteDetailLines()));
    }

    private void copyHistoryDetailsToClipboard() {
        Minecraft.getInstance().keyboardHandler.setClipboard(String.join("\n", getHistoryDetailLines()));
    }

    private void requestAllLookupData() {
        ChatDataListener.beginLookupSession(targetUsername);
        requestAlts();
        requestCheckMute();
        requestCheckBan();
        requestHistoryLatest();
    }

    private void requestAlts() {
        ChatDataListener.extendLookupSession();
        altsLoading = true;
        altsText = "Loading...";
        clearAltsLists();
        altsRequestTick = screenTick;

        ChatDataListener.clearBuffer();
        ChatDataListener.onAltsLine = (username, entries) -> {
            if (targetUsername.equalsIgnoreCase(username)) {
                applyAltsEntries(entries, entries.stream().map(ChatDataListener.AltEntry::username).collect(Collectors.joining(", ")));
            }
        };
        Minecraft.getInstance().player.connection.sendCommand("alts " + targetUsername);
    }

    private void requestCheckMute() {
        ChatDataListener.extendLookupSession();
        muteLoading = true;
        muteInfo = ChatDataListener.PunishmentInfo.unknown();
        muteRequestTick = screenTick;
        muteRetryCount = 0;
        pendingMuteSendTick = -1;

        LOGGER.info("[MBERRY] Sending immediate checkmute for {}", targetUsername);
        ChatDataListener.onMuteInfo = (username, info) -> {
            if (targetUsername.equalsIgnoreCase(username)) {
                LOGGER.info("[MBERRY] checkmute callback matched target {}", username);
                applyMuteInfo(info);
            }
        };
        Minecraft.getInstance().player.connection.sendCommand("checkmute " + targetUsername + " server:*");
    }

    private void requestCheckBan() {
        ChatDataListener.extendLookupSession();
        banLoading = true;
        banInfo = ChatDataListener.PunishmentInfo.unknown();
        banRequestTick = screenTick;
        banRetryCount = 0;
        pendingBanSendTick = -1;

        LOGGER.info("[MBERRY] Sending immediate checkban for {}", targetUsername);
        ChatDataListener.onBanInfo = (username, info) -> {
            if (targetUsername.equalsIgnoreCase(username)) {
                LOGGER.info("[MBERRY] checkban callback matched target {}", username);
                applyBanInfo(info);
            }
        };
        Minecraft.getInstance().player.connection.sendCommand("checkban " + targetUsername + " server:*");
    }

    private void requestHistoryLatest() {
        requestHistoryPage(1, true, true);
    }

    private void requestHistoryPage(int page) {
        requestHistoryPage(page, false, false);
    }

    private void requestHistoryPage(int page, boolean force, boolean latestCommandStyle) {
        int targetPage = Math.max(1, page);
        if (historyTotalPages > 1) {
            targetPage = Mth.clamp(targetPage, 1, historyTotalPages);
        }

        ChatDataListener.extendLookupSession();
        historyLoading = true;
        historyInfo = ChatDataListener.HistoryInfo.unknown();
        historyRequestTick = screenTick;
        historyRetryCount = 0;
        pendingHistorySendTick = -1;
        historyRequestCommand = latestCommandStyle
                ? "history " + targetUsername
                : "history " + targetUsername + " " + targetPage;

        LOGGER.info("[MBERRY] Sending immediate history for {} cmd={}", targetUsername, historyRequestCommand);
        ChatDataListener.onHistoryInfo = (username, info) -> {
            if (targetUsername.equalsIgnoreCase(username)) {
                LOGGER.info("[MBERRY] history callback matched target {} page={}", username, info == null ? -1 : info.page());
                applyHistory(info);
            }
        };
        Minecraft.getInstance().player.connection.sendCommand(historyRequestCommand);
    }

    private void applyMuteInfo(ChatDataListener.PunishmentInfo info) {
        LOGGER.info("[MBERRY] Applied mute info for {} active={}", targetUsername, info != null && info.active());
        muteLoading = false;
        muteInfo = info == null ? ChatDataListener.PunishmentInfo.unknown() : info;
    }

    private void applyBanInfo(ChatDataListener.PunishmentInfo info) {
        LOGGER.info("[MBERRY] Applied ban info for {} active={}", targetUsername, info != null && info.active());
        banLoading = false;
        banInfo = info == null ? ChatDataListener.PunishmentInfo.unknown() : info;
    }

    private void applyHistory(ChatDataListener.HistoryInfo info) {
        LOGGER.info("[MBERRY] Applied history for {} page={}/{} lines={}",
                targetUsername,
                info == null ? -1 : info.page(),
                info == null ? -1 : info.totalPages(),
                info == null || info.rawLines() == null ? 0 : info.rawLines().size());
        historyLoading = false;
        historyInfo = info == null ? ChatDataListener.HistoryInfo.unknown() : info;
        historyCurrentPage = Math.max(1, historyInfo.page());
        historyTotalPages = Math.max(1, historyInfo.totalPages());
    }

    private void applyAltsEntries(List<ChatDataListener.AltEntry> entries, String rawText) {
        altsLoading = false;
        altsText = rawText == null ? "" : rawText;

        clearAltsLists();

        if (entries == null || entries.isEmpty()) {
            altsText = "No alts found.";
            return;
        }

        for (ChatDataListener.AltEntry entry : entries) {
            if (entry.status() == ChatDataListener.AltStatus.BANNED) {
                bannedAlts.add(entry.username());
            } else if (entry.status() == ChatDataListener.AltStatus.ONLINE) {
                onlineAlts.add(entry.username());
            } else {
                offlineAlts.add(entry.username());
            }
        }
    }

    private void clearAltsLists() {
        bannedAlts = new ArrayList<>();
        onlineAlts = new ArrayList<>();
        offlineAlts = new ArrayList<>();

        bannedScrollOffset = 0;
        onlineScrollOffset = 0;
        offlineScrollOffset = 0;
    }

    private void setColumnBounds(AltColumn column, int x, int y, int w, int h, int visibleRows) {
        if (column == AltColumn.BANNED) {
            bannedListX = x;
            bannedListY = y;
            bannedListW = w;
            bannedListH = h;
            bannedVisibleRows = visibleRows;
        } else if (column == AltColumn.ONLINE) {
            onlineListX = x;
            onlineListY = y;
            onlineListW = w;
            onlineListH = h;
            onlineVisibleRows = visibleRows;
        } else {
            offlineListX = x;
            offlineListY = y;
            offlineListW = w;
            offlineListH = h;
            offlineVisibleRows = visibleRows;
        }
    }

    private void setColumnScroll(AltColumn column, int value) {
        if (column == AltColumn.BANNED) {
            bannedScrollOffset = value;
        } else if (column == AltColumn.ONLINE) {
            onlineScrollOffset = value;
        } else {
            offlineScrollOffset = value;
        }
    }

    private void addBtn(int x, int y, int w, int h, String label, int border, int hover, Runnable onPress) {
        addRenderableWidget(new OutlinedButton(
                x, y, w, h,
                Component.literal(label),
                border, hover, 0xFFFFFFFF,
                b -> onPress.run()
        ));
    }

    private void drawContainer(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, COL_BG);
        g.renderOutline(x, y, w, h, COL_BORDER);
    }

    private void drawDivider(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 1, COL_DIVIDER);
    }

    private static boolean isInside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private boolean isServerSelectionReady() {
        if (serverScope == null || serverScope.isBlank()) {
            return false;
        }
        if ("server:kitpvp".equals(serverScope)) {
            return kitpvpScope != null && !kitpvpScope.isBlank();
        }
        return true;
    }

    private String getServerPreview() {
        List<String> targets = resolveServerTargets();
        if (targets.isEmpty()) {
            return "<no-server>";
        }
        if (targets.size() == 1) {
            return targets.get(0);
        }
        return String.join(", ", targets);
    }

    private String resolveDurationArgument() {
        String raw = numberInput != null ? numberInput.getValue().trim() : "";
        if (raw.isEmpty() || unit == null || unit.isBlank()) {
            return "";
        }
        long value;
        try {
            value = Long.parseLong(raw);
        } catch (NumberFormatException ignored) {
            return "";
        }
        if (value <= 0) {
            return "";
        }
        if ("min".equals(unit)) {
            return (value * 60L) + "s";
        }
        return value + unit;
    }

    private List<String> resolveServerTargets() {
        List<String> targets = new ArrayList<>();
        if (serverScope == null || serverScope.isBlank()) {
            return targets;
        }
        if (!"server:kitpvp".equals(serverScope)) {
            targets.add(serverScope);
            return targets;
        }
        if ("both".equals(kitpvpScope)) {
            targets.add("server:kitpvp-1");
            targets.add("server:kitpvp-2");
        } else if (kitpvpScope != null && !kitpvpScope.isBlank()) {
            targets.add(kitpvpScope);
        }
        return targets;
    }

    private int left() {
        return (width - PANEL_W) / 2;
    }

    private int top() {
        return (height - PANEL_H) / 2;
    }

    private String[][] getReasons() {
        return switch (action) {
            case "ban" -> BAN_REASONS;
            case "mute" -> MUTE_REASONS;
            case "warn" -> WARN_REASONS;
            default -> new String[][]{};
        };
    }
}
