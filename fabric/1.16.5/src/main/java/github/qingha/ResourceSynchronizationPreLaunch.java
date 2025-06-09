package github.qingha;

import com.google.gson.Gson;
import github.qingha.gui.gl.GlHelper;
import github.qingha.io.ProgressReceiver;
import github.qingha.io.network.DownloadDispatcher;
import github.qingha.io.network.DownloadTask;
import github.qingha.io.network.ModOutputStream;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicProgressBarUI;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class ResourceSynchronizationPreLaunch implements PreLaunchEntrypoint {
    private static final Logger LOGGER = LoggerFactory.getLogger("ModUpdater");
    private final JFrame frame;
    private final JProgressBar progressBar;
    private final JTextArea logArea;
    private final AtomicBoolean hasError = new AtomicBoolean(false);
    private String lastLogLine = "";
    private final JLabel statusLabel;
    private final JLabel progressLabel;
    private int totalFiles = 0;
    private int completedFiles = 0;

    private static final String BACKGROUND_PATH = "/assets/resource-synchronization/textures/gui/background.png";
    private static final Color PRIMARY_COLOR = new Color(63, 81, 181);
    private static final Color PRIMARY_DARK_COLOR = new Color(48, 63, 159);
    private static final Color ACCENT_COLOR = new Color(255, 64, 129);
    private static final Color BACKGROUND_COLOR = new Color(245, 245, 245);
    private static final Color CARD_BACKGROUND = new Color(255, 255, 255, 245);
    private static final Color TEXT_PRIMARY = new Color(33, 33, 33);
    private static final Color TEXT_SECONDARY = new Color(117, 117, 117);

    public ResourceSynchronizationPreLaunch() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        frame = new JFrame("资源包更新器");
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setSize(900, 600);         frame.setLocationRelativeTo(null);
        frame.setUndecorated(true); 
                BackgroundPanel mainPanel = new BackgroundPanel();
        mainPanel.setLayout(new BorderLayout(15, 15));
        mainPanel.setBorder(new EmptyBorder(25, 25, 25, 25));
        frame.setContentPane(mainPanel);

                JPanel titleBar = new MaterialPanel(12);
        titleBar.setLayout(new BorderLayout());
        titleBar.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
        titleBar.setBackground(new Color(255, 255, 255, 10));
        JLabel titleLabel = new JLabel("资源包更新器");
        titleLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 20));
        titleLabel.setForeground(PRIMARY_COLOR);
        titleBar.add(titleLabel, BorderLayout.WEST);

                JButton closeButton = createIconButton();
        closeButton.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(frame,
                    "确定要取消更新并退出游戏吗？",
                    "确认取消",
                    JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                System.exit(0);
            }
        });
        titleBar.add(closeButton, BorderLayout.EAST);
        mainPanel.add(titleBar, BorderLayout.NORTH);

                JPanel contentCard = new MaterialPanel(16);
        contentCard.setLayout(new BorderLayout(15, 15));
        contentCard.setBorder(new EmptyBorder(20, 20, 20, 20));
        contentCard.setBackground(CARD_BACKGROUND);
        mainPanel.add(contentCard, BorderLayout.CENTER);

                JPanel progressPanel = new JPanel(new BorderLayout(10, 10));
        progressPanel.setOpaque(false);
        progressPanel.setBorder(new EmptyBorder(0, 0, 15, 0));

                statusLabel = new JLabel("正在初始化...");
        statusLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 16));
        statusLabel.setForeground(TEXT_PRIMARY);
        progressPanel.add(statusLabel, BorderLayout.NORTH);

                progressLabel = new JLabel("0 / 0 文件已完成");
        progressLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
        progressLabel.setForeground(TEXT_SECONDARY);
        progressPanel.add(progressLabel, BorderLayout.SOUTH);

                progressBar = new JProgressBar(0, 100) {
                        private int targetValue = 0;
            private Timer animationTimer;

            public void setTargetValue(int value) {
                targetValue = value;
                if (animationTimer != null && animationTimer.isRunning()) {
                    animationTimer.stop();
                }
                animationTimer = new Timer(20, e -> {
                    int current = getValue();
                    if (current == targetValue) {
                        animationTimer.stop();
                        return;
                    }
                    int step = Math.max(1, Math.abs(targetValue - current) / 10);
                    setValue(current < targetValue ? current + step : current - step);
                    repaint();
                });
                animationTimer.start();
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                                g2d.setColor(new Color(238, 238, 238));
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);

                                if (getValue() > 0) {
                    GradientPaint gradient = new GradientPaint(
                            0, 0, PRIMARY_COLOR,
                            getWidth(), 0, PRIMARY_DARK_COLOR
                    );
                    g2d.setPaint(gradient);
                    g2d.fillRoundRect(0, 0,
                            (int)((getWidth() * getValue()) / 100.0),
                            getHeight(), 10, 10);
                }

                                if (isStringPainted()) {
                    g2d.setFont(getFont().deriveFont(Font.BOLD, 12f));
                    g2d.setColor(Color.WHITE);
                    String text = getString();
                    FontMetrics fm = g2d.getFontMetrics();
                    g2d.drawString(text,
                            (getWidth() - fm.stringWidth(text)) / 2,
                            (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                }
                g2d.dispose();
            }
        };
        progressBar.setStringPainted(true);
        progressBar.setFont(new Font("Microsoft YaHei", Font.BOLD, 12));
        progressBar.setForeground(Color.WHITE);
        progressBar.setBorder(new EmptyBorder(0, 0, 0, 0));
        progressBar.setString("等待开始...");
        progressBar.setUI(new BasicProgressBarUI() {
            @Override
            protected Dimension getPreferredInnerVertical() {
                return new Dimension(20, 146);
            }

            @Override
            protected Dimension getPreferredInnerHorizontal() {
                return new Dimension(146, 20);
            }
        });
        progressPanel.add(progressBar, BorderLayout.CENTER);
        contentCard.add(progressPanel, BorderLayout.NORTH);

                logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setForeground(TEXT_PRIMARY);
        logArea.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        logArea.setBackground(new Color(250, 250, 250));
        logArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);

                JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        JScrollBar verticalScrollBar = scrollPane.getVerticalScrollBar();
        verticalScrollBar.setUI(new MaterialScrollBarUI());
        verticalScrollBar.setUnitIncrement(16);
        scrollPane.getVerticalScrollBar().setUI(new MaterialScrollBarUI());

        contentCard.add(scrollPane, BorderLayout.CENTER);

                JPanel footerPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 10));
        footerPanel.setOpaque(false);
        footerPanel.setBorder(new EmptyBorder(10, 0, 0, 0));

        JButton cancelBtn = createMaterialButton("取消更新", PRIMARY_COLOR);
        cancelBtn.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(frame,
                    "确定要取消更新并退出游戏吗？",
                    "确认取消",
                    JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                System.exit(0);
            }
        });

        JButton retryBtn = createMaterialButton("重试", ACCENT_COLOR);
        retryBtn.addActionListener(e -> logArea.append("\n[系统] 用户点击了重试按钮\n"));

        footerPanel.add(retryBtn);
        footerPanel.add(cancelBtn);
        contentCard.add(footerPanel, BorderLayout.SOUTH);

                titleBar.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_ICONIFIED));
            }
        });

        titleBar.addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseDragged(MouseEvent e) {
                Point p = frame.getLocation();
                frame.setLocation(p.x + e.getX(), p.y + e.getY());
            }
        });
    }

        private JButton createMaterialButton(String text, Color bgColor) {
        return new JButton(text) {
            private Color backgroundColor = bgColor;
            private final Timer animationTimer = new Timer(10, null);
            private float animationState = 0f;
            private Point ripplePoint;

            {
                setContentAreaFilled(false);
                setFocusPainted(false);
                setBorderPainted(false);
                setForeground(Color.WHITE);
                setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                setBorder(new EmptyBorder(12, 24, 12, 24));

                animationTimer.addActionListener(e -> {
                    animationState += 0.1f;
                    if (animationState >= 1f) {
                        animationState = 0f;
                        animationTimer.stop();
                    }
                    repaint();
                });

                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent e) {
                        ripplePoint = e.getPoint();
                        animationState = 0f;
                        animationTimer.start();
                    }

                    @Override
                    public void mouseEntered(MouseEvent e) {
                        backgroundColor = bgColor.darker();
                        repaint();
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        backgroundColor = bgColor;
                        repaint();
                    }
                });
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                                g2.setColor(backgroundColor);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 24, 24);

                                if (ripplePoint != null && animationTimer.isRunning()) {
                    float radius = animationState * Math.max(getWidth(), getHeight()) * 1.5f;
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f * (1 - animationState)));
                    g2.setColor(Color.WHITE);
                    g2.fillOval(
                            ripplePoint.x - (int) (radius / 2),
                            ripplePoint.y - (int) (radius / 2),
                            (int) radius,
                            (int) radius
                    );
                    g2.setComposite(AlphaComposite.SrcOver);
                }

                                FontMetrics fm = g2.getFontMetrics();
                Rectangle stringBounds = fm.getStringBounds(getText(), g2).getBounds();
                int textX = (getWidth() - stringBounds.width) / 2;
                int textY = (getHeight() - stringBounds.height) / 2 + fm.getAscent();

                g2.setColor(getForeground());
                g2.drawString(getText(), textX, textY);

                g2.dispose();
            }
        };
    }

        private JButton createIconButton() {
        JButton button = new JButton("✖") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                if (getModel().isRollover()) {
                    g2.setColor(new Color(ResourceSynchronizationPreLaunch.PRIMARY_COLOR.getRed(), ResourceSynchronizationPreLaunch.PRIMARY_COLOR.getGreen(), ResourceSynchronizationPreLaunch.PRIMARY_COLOR.getBlue(), 30));
                    g2.fillOval(0, 0, getWidth(), getHeight());
                }

                if (getModel().isPressed()) {
                    g2.setColor(new Color(ResourceSynchronizationPreLaunch.PRIMARY_COLOR.getRed(), ResourceSynchronizationPreLaunch.PRIMARY_COLOR.getGreen(), ResourceSynchronizationPreLaunch.PRIMARY_COLOR.getBlue(), 60));
                    g2.fillOval(0, 0, getWidth(), getHeight());
                }

                                g2.setColor(ResourceSynchronizationPreLaunch.PRIMARY_COLOR);
                g2.setFont(getFont().deriveFont(Font.BOLD, 18f));
                FontMetrics fm = g2.getFontMetrics();
                int textX = (getWidth() - fm.stringWidth(getText())) / 2;
                int textY = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(getText(), textX, textY);

                g2.dispose();
            }
        };

        button.setFont(new Font("Segoe UI Symbol", Font.PLAIN, 16));
        button.setForeground(ResourceSynchronizationPreLaunch.PRIMARY_COLOR);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setPreferredSize(new Dimension(36, 36));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        return button;
    }

        private static class MaterialPanel extends JPanel {
        private final int cornerRadius;

        public MaterialPanel(int radius) {
            this.cornerRadius = radius;
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                        int shadowSize = 6;
            for (int i = 0; i < shadowSize; i++) {
                float alpha = (1.0f / (shadowSize * 2)) * (shadowSize - i);
                g2d.setColor(new Color(0, 0, 0, (int)(alpha * 255)));
                g2d.setStroke(new BasicStroke(i * 2));
                g2d.draw(new RoundRectangle2D.Float(
                        shadowSize - i, shadowSize - i,
                        getWidth() - (shadowSize - i) * 2 - 1,
                        getHeight() - (shadowSize - i) * 2 - 1,
                        cornerRadius, cornerRadius));
            }

                        g2d.setColor(getBackground());
            g2d.fill(new RoundRectangle2D.Float(
                    shadowSize, shadowSize,
                    getWidth() - shadowSize * 2,
                    getHeight() - shadowSize * 2,
                    cornerRadius, cornerRadius));

            g2d.dispose();
        }
    }

        private static class MaterialScrollBarUI extends javax.swing.plaf.basic.BasicScrollBarUI {
        private static final int SCROLL_BAR_ALPHA_ROLLOVER = 150;
        private static final int SCROLL_BAR_ALPHA = 100;
        private static final int THUMB_SIZE = 8;
        private static final Color THUMB_COLOR = new Color(200, 200, 200);

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return createZeroButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return createZeroButton();
        }

        private JButton createZeroButton() {
            JButton button = new JButton();
            button.setPreferredSize(new Dimension(0, 0));
            button.setMinimumSize(new Dimension(0, 0));
            button.setMaximumSize(new Dimension(0, 0));
            return button;
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
                    }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
            int alpha = isThumbRollover() ? SCROLL_BAR_ALPHA_ROLLOVER : SCROLL_BAR_ALPHA;
            int orientation = scrollbar.getOrientation();
            int x = thumbBounds.x;
            int y = thumbBounds.y;

            int width = orientation == JScrollBar.VERTICAL ? THUMB_SIZE : thumbBounds.width;
            width = Math.max(width, THUMB_SIZE);

            int height = orientation == JScrollBar.VERTICAL ? thumbBounds.height : THUMB_SIZE;
            height = Math.max(height, THUMB_SIZE);

            Graphics2D graphics2D = (Graphics2D) g.create();
            graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics2D.setColor(new Color(THUMB_COLOR.getRed(), THUMB_COLOR.getGreen(), THUMB_COLOR.getBlue(), alpha));
            graphics2D.fillRoundRect(x, y, width, height, 10, 10);
            graphics2D.dispose();
        }
    }

    private static class BackgroundPanel extends JPanel {
        private Image backgroundImage;

        public BackgroundPanel() {
            try {
                                InputStream imgStream = getClass().getResourceAsStream(BACKGROUND_PATH);
                if (imgStream != null) {
                    backgroundImage = new ImageIcon(ImageIO.read(imgStream)).getImage();
                } else {
                                        backgroundImage = createDefaultBackground();
                }
            } catch (Exception e) {
                                backgroundImage = createDefaultBackground();
            }
        }

                private BufferedImage createDefaultBackground() {
            int w = 900, h = 600;
            BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = image.createGraphics();

                        GradientPaint gradient = new GradientPaint(
                    0, 0, new Color(230, 240, 255),
                    w, h, new Color(180, 200, 230)
            );
            g2d.setPaint(gradient);
            g2d.fillRect(0, 0, w, h);

                        g2d.setColor(new Color(255, 255, 255, 20));
            int size = 80;
            for (int y = -size; y < h + size; y += size * 2) {
                for (int x = -size; x < w + size; x += size * 2) {
                    g2d.fillOval(x, y, size, size);
                }
            }

            g2d.dispose();
            return image;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

                        if (backgroundImage != null) {
                g2d.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), this);
            }

                        g2d.setClip(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 24, 24));

                        g2d.setColor(new Color(255, 255, 255, 200));
            g2d.fillRect(0, 0, getWidth(), getHeight());
        }
    }

    private class SwingProgressReceiver implements ProgressReceiver {
        @Override
        public void printLog(String line) {
            SwingUtilities.invokeLater(() -> {
                logArea.append(line + "\n");
                logArea.setCaretPosition(logArea.getDocument().getLength());
                lastLogLine = line;
            });
        }

        @Override
        public void amendLastLog(String postfix) {
            SwingUtilities.invokeLater(() -> {
                String text = logArea.getText();
                if (text.endsWith("\n")) {
                    text = text.substring(0, text.length() - 1);
                }
                int lastNewline = text.lastIndexOf("\n");
                if (lastNewline >= 0) {
                    logArea.setText(text.substring(0, lastNewline + 1) + lastLogLine + postfix + "\n");
                } else {
                    logArea.setText(lastLogLine + postfix + "\n");
                }
                logArea.setCaretPosition(logArea.getDocument().getLength());
            });
        }

        @Override
        public void setProgress(float primary, float secondary) {
            SwingUtilities.invokeLater(() -> {
                int progress = Math.min(100, Math.max(0, (int)(primary * 100)));
                try {
                    java.lang.reflect.Method setTargetValue = progressBar.getClass().getDeclaredMethod("setTargetValue", int.class);
                    setTargetValue.setAccessible(true);
                    setTargetValue.invoke(progressBar, progress);
                } catch (Exception e) {
                    progressBar.setValue(progress);
                }
                progressBar.setString(String.format("%d%% - 正在更新...", progress));
            });
        }

        @Override
        public void setInfo(String aux1, String aux2) {
            SwingUtilities.invokeLater(() -> {
                if (!aux1.isEmpty()) {
                    statusLabel.setText(aux1);
                    logArea.append(aux1 + "\n");
                }
                if (!aux2.isEmpty()) logArea.append(aux2 + "\n");
                logArea.setCaretPosition(logArea.getDocument().getLength());
            });
        }

        @Override
        public void setException(Exception exception) throws GlHelper.MinecraftStoppingException {
            SwingUtilities.invokeLater(() -> {
                String errorMsg = "错误: " + exception.getMessage();
                statusLabel.setText(errorMsg);
                statusLabel.setForeground(new Color(200, 50, 50));
                logArea.append(errorMsg + "\n");
                hasError.set(true);
            });
        }
    }

    @Override
    public void onPreLaunch() {
        try {
            frame.setIconImage(new ImageIcon(Objects.requireNonNull(getClass().getResource("/assets/resource-synchronization/icon.png"))).getImage());
        } catch (Exception e) {
            LOGGER.warn("无法加载窗口图标", e);
        }

        try {
            ResourceSynchronizationClient.CONFIG.load();
            if ((ResourceSynchronizationClient.CONFIG.selectedSource.value == null ||
                    ResourceSynchronizationClient.CONFIG.selectedSource.value.baseUrl.isEmpty()) &&
                    !ResourceSynchronizationClient.CONFIG.sourceList.value.isEmpty()) {
                ResourceSynchronizationClient.CONFIG.selectedSource.value =
                        ResourceSynchronizationClient.CONFIG.sourceList.value.get(0);
                ResourceSynchronizationClient.CONFIG.selectedSource.isFromLocal = true;
                ResourceSynchronizationClient.CONFIG.save();
            }

            if (ResourceSynchronizationClient.CONFIG.selectedSource.value == null
                    || ResourceSynchronizationClient.CONFIG.selectedSource.value.baseUrl.isEmpty()) {
                LOGGER.warn("未选择更新源或URL为空，跳过更新");
                statusLabel.setText("未配置更新源 - 跳过更新");
                return;
            }

            Path modsDir = FabricLoader.getInstance().getGameDir().resolve("mods");
            String baseUrl = ResourceSynchronizationClient.CONFIG.selectedSource.value.baseUrl;

            frame.setVisible(true);
            statusLabel.setText("正在检查更新...");
            boolean hasUpdates = updateMods(modsDir, baseUrl);
            if (hasUpdates) {
                MaterialOptionPane.showMaterialDialog(frame,
                        "<html><body style='width: 320px; text-align:center;'><h2 style='color:#2E7D32; margin-top:5px;'>更新完成</h2>" +
                                "<p style='font-size:14px;'>资源包已成功更新，游戏将自动关闭</p>" +
                                "<p style='font-size:13px; color:#555;'>请重新启动游戏以应用更改</p></body></html>",
                        "更新完成",
                        JOptionPane.INFORMATION_MESSAGE);
                System.exit(0);
            } else {
                statusLabel.setText("已是最新版本 - 无需更新");
                progressBar.setString("已完成 - 无需更新");
            }
        } catch (Exception e) {
            LOGGER.error("资源包更新失败", e);
            hasError.set(true);
            MaterialOptionPane.showMaterialDialog(frame,
                    "<html><body style='width: 350px;'><h2 style='color:#D32F2F; margin-top:5px;'>更新失败</h2>" +
                            "<p style='font-size:14px;'>" + e.getMessage() + "</p>" +
                            "<p style='font-size:13px; color:#555;'>请检查网络连接或联系管理员</p></body></html>",
                    "错误",
                    JOptionPane.ERROR_MESSAGE);
        } finally {
            frame.dispose();
            if (hasError.get()) {
                System.exit(1);
            }
        }
    }

    private boolean updateMods(Path modsDir, String baseUrl) throws Exception {
        Files.createDirectories(modsDir);
        boolean hasUpdates = false;

        SwingProgressReceiver progressReceiver = new SwingProgressReceiver();
        progressReceiver.printLog("正在同步资源包...");
        statusLabel.setText("正在同步资源包...");

        Gson gson = new Gson();
        ModItem[] modItems = gson.fromJson(getRemoteJson(baseUrl), ModItem[].class);
        totalFiles = modItems.length;
        completedFiles = 0;
        updateProgressLabel();

        Set<String> existingFiles = new HashSet<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsDir)) {
            for (Path path : stream) {
                existingFiles.add(path.getFileName().toString());
            }
        }

        Set<String> requiredFiles = new HashSet<>();
        DownloadDispatcher downloadDispatcher = new DownloadDispatcher(progressReceiver);

        for (ModItem item : modItems) {
            requiredFiles.add(item.file_name);
            Path modFile = modsDir.resolve(item.file_name);

            if (!Files.exists(modFile) || !verifyFileSha1(modFile, item.sha1)) {
                hasUpdates = true;
                String fullUrl = baseUrl + "/" + item.path.replace(" ", "%20");
                DownloadTask task = new DownloadTask(downloadDispatcher,
                        fullUrl, item.file_name, -1);
                downloadDispatcher.dispatch(task, () -> {
                    try {
                        return new ModOutputStream(modFile, item.sha1);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            } else {
                completedFiles++;
                updateProgressLabel();
                progressReceiver.printLog("✓ " + item.file_name + " (已是最新)");
                progressReceiver.setProgress((float) completedFiles / totalFiles, 0);
            }
        }

        try {
            while (downloadDispatcher.tasksFinished()) {
                downloadDispatcher.updateSummary();
                Thread.sleep(1000 / 30);
            }

            completedFiles = totalFiles;
            updateProgressLabel();
        } finally {
            downloadDispatcher.close();
        }

        Set<String> filesToDelete = existingFiles.stream()
                .filter(f -> !f.startsWith("[Custom]") && !requiredFiles.contains(f))
                .collect(java.util.stream.Collectors.toSet());

        if (!filesToDelete.isEmpty()) {
            hasUpdates = true;
            filesToDelete.forEach(f -> {
                try {
                    Files.deleteIfExists(modsDir.resolve(f));
                    progressReceiver.printLog("🗑️ 删除文件: " + f);
                } catch (IOException e) {
                    LOGGER.warn("Failed to delete file: {}", f, e);
                }
            });
        }

        progressReceiver.printLog("✅ 资源包同步完成");
        statusLabel.setText("资源包同步完成");
        return hasUpdates;
    }

    private void updateProgressLabel() {
        SwingUtilities.invokeLater(() -> progressLabel.setText(String.format("%d / %d 文件已完成", completedFiles, totalFiles)));
    }

    private String getRemoteJson(String baseUrl) throws IOException {
        URL url = new URL(baseUrl + "/modsmeta.json");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        try (InputStream is = connection.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        }
    }

    private boolean verifyFileSha1(Path file, String expectedSha1) throws IOException {
        try (InputStream is = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            String actualSha1 = bytesToHex(digest.digest());
            return actualSha1.equalsIgnoreCase(expectedSha1);
        } catch (Exception e) {
            throw new IOException("Failed to verify checksum", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    private static class ModItem {
        public String file_name;
        public String sha1;
        public String path;
    }

    private static class MaterialOptionPane {
        public static void showMaterialDialog(Component parent, Object message, String title, int messageType) {
            JDialog.setDefaultLookAndFeelDecorated(true);
            JOptionPane pane = new JOptionPane(message, messageType);
            JDialog dialog = pane.createDialog(parent, title);

                        for (Component comp : dialog.getComponents()) {
                if (comp instanceof JPanel) {
                    for (Component innerComp : ((JPanel) comp).getComponents()) {
                        if (innerComp instanceof JButton button) {
                            button.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
                            button.setBackground(PRIMARY_COLOR);
                            button.setForeground(Color.WHITE);
                            button.setFocusPainted(false);
                            button.setBorder(new EmptyBorder(8, 16, 8, 16));
                        } else if (innerComp instanceof JLabel label) {
                            label.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
                        }
                    }
                }
            }
            dialog.setVisible(true);
        }
    }
}