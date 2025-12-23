package com.example.rtpav.client;

import com.example.rtpav.client.media.VideoRenderer;
import com.example.rtpav.rmi.ConferenceService;
import com.example.rtpav.rmi.PeerInfo;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.InetSocketAddress;
import java.util.Set;
import java.util.Vector;
import javax.imageio.ImageIO;

public class ClientUI extends JFrame {
    // Modern color scheme with gradients
    private static final Color BG_PRIMARY = new Color(18, 18, 18);
    private static final Color BG_SECONDARY = new Color(30, 30, 30);
    private static final Color BG_TERTIARY = new Color(40, 40, 40);
    private static final Color ACCENT_BLUE = new Color(0, 122, 255);
    private static final Color ACCENT_BLUE_LIGHT = new Color(64, 156, 255);
    private static final Color ACCENT_GREEN = new Color(52, 199, 89);
    private static final Color ACCENT_RED = new Color(255, 59, 48);
    private static final Color ACCENT_RED_LIGHT = new Color(255, 99, 88);
    private static final Color ACCENT_PURPLE = new Color(175, 82, 222);
    private static final Color TEXT_PRIMARY = new Color(255, 255, 255);
    private static final Color TEXT_SECONDARY = new Color(174, 174, 178);
    
    private final ModernButton btnCam = new ModernButton(IconType.CAMERA, "Camera", false);
    private final ModernButton btnMic = new ModernButton(IconType.MICROPHONE, "Mic", false);
    // Chat tabbed pane để quản lý nhiều tab chat
    private JTabbedPane chatTabbedPane;
    // Tab chat chung (public chat) - dùng JTextPane để support HTML styling
    private final JTextPane publicChatArea = new JTextPane();
    private final JTextField publicChatBox = new JTextField();
    private JLabel selfLabel;
    private JPanel selfContainer;
    private final JList<String> peerList = new JList<>();
    private final VideoRenderer selfView = new VideoRenderer();
    private final VideoRenderer remoteView = new VideoRenderer();
    // Map để lưu video từ nhiều peers: SSRC -> VideoRenderer
    private final java.util.Map<Long, VideoRenderer> peerVideoViews = new java.util.concurrent.ConcurrentHashMap<>();
    // Map để lưu peer panels: SSRC -> JPanel
    private final java.util.Map<Long, JPanel> peerPanels = new java.util.concurrent.ConcurrentHashMap<>();
    // Map để lưu các tab chat riêng: SSRC -> ChatTab
    private final java.util.Map<Long, ChatTab> privateChatTabs = new java.util.concurrent.ConcurrentHashMap<>();
    // Map để lưu tên người dùng: SSRC -> Tên người dùng
    private final java.util.Map<Long, String> peerNames = new java.util.concurrent.ConcurrentHashMap<>();
    // Map để lưu reference đến peerLabel: SSRC -> JLabel
    private final java.util.Map<Long, JLabel> peerLabels = new java.util.concurrent.ConcurrentHashMap<>();

    private final String name;
    private final String roomId;
    private final long selfSsrc;
    private final ConferenceService svc;
    private final InetSocketAddress myRtp;

    private volatile boolean camOn = false;
    private volatile boolean micOn = false;

    public interface Hooks {
        void onToggleCamera(boolean on);
        void onToggleMic(boolean on);
        void onSendChat(String msg);
        void onClose();
    }
    
    public interface ExtendedHooks extends Hooks {
        void onSendPrivateChat(long toSsrc, String msg);
    }

    private Hooks hooks;

    public ClientUI(String name, String roomId, long selfSsrc, ConferenceService svc, InetSocketAddress myRtp) {
        super("Video Conference - " + name + " @ " + roomId);
        this.name = name; 
        this.roomId = roomId; 
        this.selfSsrc = selfSsrc; 
        this.svc = svc; 
        this.myRtp = myRtp;
        
        // Tạo avatar từ tên người dùng cho self view
        if (name != null && !name.isEmpty()) {
            selfView.setUserName(name);
        }
        
        // Set modern look and feel
        try {
            String lookAndFeel = UIManager.getSystemLookAndFeelClassName();
            UIManager.setLookAndFeel(lookAndFeel);
        } catch (Exception e) {
            // Use default look and feel
        }
        
        build();
    }

    public void setHooks(Hooks hooks) { 
        this.hooks = hooks; 
    }

    // Chat panel reference để có thể toggle
    private JPanel chatPanel;
    private JPanel mainContainer; // Reference đến main container để add/remove chat panel
    private boolean chatVisible = false;

    private void build() {
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        getContentPane().setBackground(BG_PRIMARY);
        setLayout(new BorderLayout(0, 0));
        ((JComponent)getContentPane()).setBorder(new EmptyBorder(0, 0, 0, 0));

        // Main container với video và chat
        mainContainer = new JPanel(new BorderLayout(0, 0));
        mainContainer.setBackground(BG_PRIMARY);
        
        // Main video area
        JPanel videoPanel = createVideoPanel();
        mainContainer.add(videoPanel, BorderLayout.CENTER);

        // Chat panel bên phải - không add vào lúc đầu, sẽ add/remove khi toggle
        chatPanel = createChatPanel();
        chatPanel.setPreferredSize(new Dimension(360, 0));
        chatPanel.setMinimumSize(new Dimension(360, 0));
        chatPanel.setMaximumSize(new Dimension(360, Integer.MAX_VALUE));
        // Không add vào mainContainer lúc đầu (ẩn mặc định)
        
        add(mainContainer, BorderLayout.CENTER);

        // Bottom control bar - giống Google Meet
        JPanel bottomBar = createBottomControlBar();
        add(bottomBar, BorderLayout.SOUTH);

        // Window listener
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override 
            public void windowClosed(java.awt.event.WindowEvent e) {
                if (hooks != null) hooks.onClose();
            }
        });

        setSize(1400, 900);
        setLocationRelativeTo(null);
        setMinimumSize(new Dimension(1000, 700));
        setExtendedState(JFrame.MAXIMIZED_BOTH); // Fullscreen by default
    }

    private JPanel createBottomControlBar() {
        JPanel bottomBar = new JPanel(new BorderLayout(20, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                // Dark background
                g2.setColor(new Color(32, 33, 36));
                g2.fillRect(0, 0, getWidth(), getHeight());
                
                g2.dispose();
            }
        };
        bottomBar.setOpaque(false);
        bottomBar.setBorder(new EmptyBorder(16, 20, 16, 20));

        // Left: Meeting title
        JLabel titleLabel = new JLabel(roomId);
        titleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        titleLabel.setForeground(TEXT_PRIMARY);
        bottomBar.add(titleLabel, BorderLayout.WEST);

        // Center: Control buttons (circular buttons like Google Meet)
        JPanel controlsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        controlsPanel.setOpaque(false);
        
        // Mic button (circular)
        CircularControlButton micBtn = new CircularControlButton(IconType.MICROPHONE, micOn);
        micBtn.addActionListener(e -> {
            micOn = !micOn;
            micBtn.setActive(micOn);
            micBtn.setToolTipText(micOn ? "Turn off microphone" : "Turn on microphone");
            if (hooks != null) hooks.onToggleMic(micOn);
        });
        micBtn.setToolTipText("Turn on microphone");
        controlsPanel.add(micBtn);
        
        // Camera button (circular)
        CircularControlButton camBtn = new CircularControlButton(IconType.CAMERA, camOn);
        camBtn.addActionListener(e -> {
            camOn = !camOn;
            camBtn.setActive(camOn);
            camBtn.setToolTipText(camOn ? "Turn off camera" : "Turn on camera");
            
            // Cập nhật label và border
            if (selfLabel != null) {
                selfLabel.setText("  You");
                selfLabel.setForeground(camOn ? ACCENT_GREEN : TEXT_SECONDARY);
                selfLabel.repaint();
            }
            if (selfContainer != null) {
                selfContainer.repaint();
            }
            
            // Nếu tắt camera, hiển thị avatar ngay
            if (!camOn) {
                showSelfAvatar();
            }
            
            if (hooks != null) hooks.onToggleCamera(camOn);
        });
        camBtn.setToolTipText("Turn on camera");
        controlsPanel.add(camBtn);
        
        // Chat button (circular) - toggle chat panel
        JButton chatBtn = createCircularControlButton(IconType.CHAT, chatVisible);
        chatBtn.addActionListener(e -> {
            chatVisible = !chatVisible;
            chatBtn.repaint();
            chatBtn.setToolTipText(chatVisible ? "Hide chat" : "Show chat");
            toggleChatPanel();
        });
        chatBtn.setToolTipText("Show chat");
        controlsPanel.add(chatBtn);
        
        // Leave button (red, circular)
        JButton leaveBtn = createCircularControlButton(IconType.VIDEO, false, true);
        leaveBtn.addActionListener(e -> {
                if (hooks != null) hooks.onClose();
            dispose();
        });
        leaveBtn.setToolTipText("Leave call");
        controlsPanel.add(leaveBtn);
        
        bottomBar.add(controlsPanel, BorderLayout.CENTER);

        // Right: Additional info (participants count, etc.)
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        rightPanel.setOpaque(false);
        bottomBar.add(rightPanel, BorderLayout.EAST);

        return bottomBar;
    }
    
    /**
     * Custom button class cho control buttons (mic, camera, etc.)
     */
    private static class CircularControlButton extends JButton {
        private final IconType iconType;
        private boolean active;
        private final boolean isLeave;
        
        public CircularControlButton(IconType iconType, boolean active) {
            this(iconType, active, false);
        }
        
        public CircularControlButton(IconType iconType, boolean active, boolean isLeave) {
            this.iconType = iconType;
            this.active = active;
            this.isLeave = isLeave;
            setPreferredSize(new Dimension(48, 48));
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
        }
        
        public void setActive(boolean active) {
            this.active = active;
            repaint();
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            int size = Math.min(getWidth(), getHeight());
            int x = (getWidth() - size) / 2;
            int y = (getHeight() - size) / 2;
            
            // Background color
            Color bgColor;
            if (isLeave) {
                bgColor = ACCENT_RED;
            } else if (active) {
                // Bật: màu xám
                bgColor = new Color(60, 64, 67); // Grey when ON
            } else {
                // Tắt: màu đỏ
                bgColor = new Color(234, 67, 53); // Red when OFF/muted
            }
            
            // Hover effect
            if (getModel().isRollover()) {
                bgColor = bgColor.brighter();
            }
            
            // Draw circle
            g2.setColor(bgColor);
            g2.fillOval(x, y, size, size);
            
            // Icon - centered
            int iconSize = size - 16;
            int iconX = x + (size - iconSize) / 2;
            int iconY = y + (size - iconSize) / 2;
            
            // Vẽ icon với đường gạch chéo nếu tắt (!active)
            IconRenderer.drawIconWithState(g2, iconType, iconX, iconY, iconSize, iconSize, TEXT_PRIMARY, !active);
            
            g2.dispose();
        }
    }
    
    private JButton createCircularControlButton(IconType iconType, boolean active, boolean isLeave) {
        return new CircularControlButton(iconType, active, isLeave);
    }
    
    private JButton createCircularControlButton(IconType iconType, boolean active) {
        return createCircularControlButton(iconType, active, false);
    }

    private JPanel createVideoPanel() {
        // Main container với BorderLayout
        JPanel videoPanel = new JPanel(new BorderLayout(8, 8)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG_PRIMARY);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        videoPanel.setBackground(BG_PRIMARY);
        videoPanel.setBorder(new EmptyBorder(8, 8, 8, 8));

        // Main grid view để hiển thị tất cả peers - hiển thị ở giữa màn hình
        // Grid layout tự động điều chỉnh số cột dựa trên số lượng peers
        // Bắt đầu với 2 cột, sẽ được cập nhật khi có peers
        JPanel mainGridPanel = new JPanel(new java.awt.GridLayout(0, 2, 12, 12));
        mainGridPanel.setBackground(BG_PRIMARY);
        mainGridPanel.setBorder(new EmptyBorder(0, 0, 0, 0));
        
        // Store reference để có thể cập nhật
        this.mainGridPanel = mainGridPanel;
        
        // Container cho grid - dùng BorderLayout để grid tự động căn giữa
        JPanel gridContainer = new JPanel(new BorderLayout());
        gridContainer.setBackground(BG_PRIMARY);
        gridContainer.setBorder(new EmptyBorder(0, 0, 0, 0));
        gridContainer.add(mainGridPanel, BorderLayout.CENTER);
        
        // Self view container - đặt ở góc dưới bên phải, độc lập với grid
        this.selfContainer = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                // Shadow
                g2.setColor(new Color(0, 0, 0, 60));
                g2.fillRoundRect(3, 3, getWidth() - 2, getHeight() - 2, 12, 12);
                
                // Background with gradient
                GradientPaint gradient = new GradientPaint(
                    0, 0, BG_SECONDARY,
                    0, getHeight(), new Color(BG_SECONDARY.getRed() - 5, BG_SECONDARY.getGreen() - 5, BG_SECONDARY.getBlue() - 5)
                );
                g2.setPaint(gradient);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                
                // Border glow when active
                if (camOn) {
                    g2.setStroke(new BasicStroke(2.5f));
                    g2.setColor(new Color(ACCENT_GREEN.getRed(), ACCENT_GREEN.getGreen(), ACCENT_GREEN.getBlue(), 150));
                    g2.drawRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 12, 12);
                }
                
                g2.dispose();
            }
        };
        selfContainer.setOpaque(false);
        selfContainer.setBorder(new EmptyBorder(4, 4, 4, 4));
        selfView.setPreferredSize(new Dimension(280, 157));
        selfView.setBackground(BG_TERTIARY);
        selfView.setToolTipText("Click để đổi avatar (khi camera tắt)");
        
        // Thêm MouseListener để click vào avatar để đổi
        selfView.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // Cho phép click vào avatar bất cứ lúc nào
                // Nếu camera đang bật, avatar mới sẽ được áp dụng khi tắt camera
                chooseAvatarFile();
            }
            
            @Override
            public void mouseEntered(MouseEvent e) {
                // Chỉ hiển thị cursor pointer và tooltip khi camera tắt (đang hiển thị avatar)
                if (!camOn || selfView.isShowingAvatar()) {
                    selfView.setCursor(new Cursor(Cursor.HAND_CURSOR));
                    selfView.setToolTipText("Click để đổi avatar");
                } else {
                    // Khi camera bật, vẫn cho phép click nhưng không có visual feedback
                    selfView.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                    selfView.setToolTipText(null);
                }
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                selfView.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
            }
        });
        
        selfContainer.add(selfView, BorderLayout.CENTER);
        
        // Label for self view with status indicator
        JPanel labelPanel = new JPanel(new BorderLayout(6, 0));
        labelPanel.setOpaque(false);
        labelPanel.setBorder(new EmptyBorder(4, 8, 4, 8));
        
        this.selfLabel = new JLabel("  You") {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                // Status indicator dot
                int dotSize = 8;
                int dotX = 4;
                int dotY = (getHeight() - dotSize) / 2;
                g2.setColor(camOn ? ACCENT_GREEN : TEXT_SECONDARY);
                g2.fillOval(dotX, dotY, dotSize, dotSize);
                
                // Glow effect when active
                if (camOn) {
                    g2.setColor(new Color(ACCENT_GREEN.getRed(), ACCENT_GREEN.getGreen(), ACCENT_GREEN.getBlue(), 100));
                    g2.fillOval(dotX - 2, dotY - 2, dotSize + 4, dotSize + 4);
                }
                
                g2.dispose();
            }
        };
        this.selfLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        this.selfLabel.setForeground(camOn ? ACCENT_GREEN : TEXT_SECONDARY);
        labelPanel.add(this.selfLabel, BorderLayout.CENTER);
        selfContainer.add(labelPanel, BorderLayout.NORTH);
        selfContainer.setPreferredSize(new Dimension(280, 180));
        
        // Đặt self view ở góc dưới bên phải, không che grid
        // Dùng JLayeredPane để overlay self view lên grid nhưng không che các peer cells
        JLayeredPane layeredPane = new JLayeredPane();
        layeredPane.setLayout(null);
        layeredPane.setBackground(BG_PRIMARY);
        
        // Grid container - chiếm toàn bộ không gian và căn giữa nội dung
        layeredPane.add(gridContainer, JLayeredPane.DEFAULT_LAYER);
        
        // Self view ở góc dưới bên phải, overlay nhưng không che grid cells
        selfContainer.setOpaque(true);
        layeredPane.add(selfContainer, JLayeredPane.PALETTE_LAYER);
        
        // Component listener để tự động đặt lại vị trí khi resize
        layeredPane.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                int width = layeredPane.getWidth();
                int height = layeredPane.getHeight();
                
                // Grid container chiếm toàn bộ không gian
                gridContainer.setBounds(0, 0, width, height);
                
                // Self view ở góc dưới bên phải
                int selfWidth = 280;
                int selfHeight = 180;
                int margin = 16;
                selfContainer.setBounds(width - selfWidth - margin, height - selfHeight - margin, selfWidth, selfHeight);
            }
        });
        
        videoPanel.add(layeredPane, BorderLayout.CENTER);

        return videoPanel;
    }
    
    private JPanel mainGridPanel; // Panel chứa grid của tất cả peers
    
    /**
     * Tính số cột tối ưu cho grid layout dựa trên số lượng peers
     * Giống Google Meet: tự động điều chỉnh để hiển thị đẹp nhất
     */
    private int calculateOptimalColumns(int peerCount) {
        if (peerCount == 0) return 1;
        if (peerCount == 1) return 1;
        if (peerCount == 2) return 2;
        if (peerCount <= 4) return 2;
        if (peerCount <= 9) return 3;
        if (peerCount <= 16) return 4;
        if (peerCount <= 25) return 5;
        return 6; // Tối đa 6 cột
    }

    private JPanel createPeersPanel() {
        JPanel peersPanel = new JPanel(new BorderLayout(8, 8));
        peersPanel.setBackground(BG_SECONDARY);
        peersPanel.setBorder(new CompoundBorder(
            new LineBorder(BG_TERTIARY, 1, true),
            new EmptyBorder(12, 12, 12, 12)
        ));

        // Header
        JLabel peersHeader = new JLabel("👥 Participants");
        peersHeader.setFont(new Font("Segoe UI", Font.BOLD, 14));
        peersHeader.setForeground(TEXT_PRIMARY);
        peersHeader.setBorder(new EmptyBorder(0, 0, 8, 0));
        peersPanel.add(peersHeader, BorderLayout.NORTH);


        // Peer list (danh sách tên)
        peerList.setBackground(BG_TERTIARY);
        peerList.setForeground(TEXT_PRIMARY);
        peerList.setSelectionBackground(ACCENT_BLUE);
        peerList.setSelectionForeground(TEXT_PRIMARY);
        peerList.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        peerList.setBorder(new EmptyBorder(8, 8, 8, 8));
        
        JScrollPane peersScroll = new JScrollPane(peerList);
        peersScroll.setBorder(null);
        peersScroll.setBackground(BG_TERTIARY);
        peersScroll.getViewport().setBackground(BG_TERTIARY);
        peersScroll.setPreferredSize(new Dimension(240, 540));
        peersPanel.add(peersScroll, BorderLayout.CENTER);

        return peersPanel;
    }

    private JPanel createChatPanel() {
        JPanel chatPanel = new JPanel(new BorderLayout(8, 8)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                // Shadow
                g2.setColor(new Color(0, 0, 0, 50));
                g2.fillRoundRect(2, 2, getWidth() - 2, getHeight() - 2, 16, 16);
                
                // Gradient background
                GradientPaint gradient = new GradientPaint(
                    0, 0, BG_SECONDARY,
                    getWidth(), 0, new Color(BG_SECONDARY.getRed() - 3, BG_SECONDARY.getGreen() - 3, BG_SECONDARY.getBlue() - 3)
                );
                g2.setPaint(gradient);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                
                // Subtle border
                g2.setStroke(new BasicStroke(1.5f));
                g2.setColor(new Color(BG_TERTIARY.getRed(), BG_TERTIARY.getGreen(), BG_TERTIARY.getBlue(), 120));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
                
                g2.dispose();
            }
        };
        chatPanel.setOpaque(false);
        chatPanel.setBorder(new EmptyBorder(14, 14, 14, 14));
        chatPanel.setPreferredSize(new Dimension(360, 0));
        chatPanel.setMinimumSize(new Dimension(360, 0));
        chatPanel.setMaximumSize(new Dimension(360, Integer.MAX_VALUE));

        // Header with icon
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        headerPanel.setOpaque(false);
        
        JLabel iconLabel = new JLabel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                IconRenderer.drawIcon(g, IconType.CHAT, 0, 0, getWidth(), getHeight(), ACCENT_BLUE);
            }
        };
        iconLabel.setPreferredSize(new Dimension(20, 20));
        
        JLabel chatHeader = new JLabel("Chat");
        chatHeader.setFont(new Font("Segoe UI", Font.BOLD, 15));
        chatHeader.setForeground(TEXT_PRIMARY);
        
        headerPanel.add(iconLabel);
        headerPanel.add(chatHeader);
        headerPanel.setBorder(new EmptyBorder(0, 0, 10, 0));
        chatPanel.add(headerPanel, BorderLayout.NORTH);

        // Tabbed pane để quản lý nhiều tab chat - Google Meet style với màu sắc cải thiện
        chatTabbedPane = new JTabbedPane(JTabbedPane.TOP) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // Background sáng hơn để dễ nhìn
                g2.setColor(new Color(50, 52, 55));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        // Background sáng hơn
        chatTabbedPane.setBackground(new Color(50, 52, 55));
        chatTabbedPane.setForeground(new Color(255, 255, 255));
        chatTabbedPane.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        
        // Customize tab appearance - màu sắc cải thiện để dễ nhìn và click
        UIManager.put("TabbedPane.selected", new Color(60, 64, 67)); // Tab selected sáng hơn
        UIManager.put("TabbedPane.background", new Color(50, 52, 55)); // Background tab bar sáng hơn
        UIManager.put("TabbedPane.foreground", new Color(200, 200, 200)); // Tab unselected text sáng hơn
        UIManager.put("TabbedPane.selectedForeground", new Color(255, 255, 255)); // Tab selected text trắng
        UIManager.put("TabbedPane.borderHighlightColor", ACCENT_BLUE);
        UIManager.put("TabbedPane.contentBorderInsets", new Insets(0, 0, 0, 0));
        
        // Custom UI để có control tốt hơn về màu sắc
        chatTabbedPane.setUI(new javax.swing.plaf.basic.BasicTabbedPaneUI() {
            @Override
            protected void paintTabBackground(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w, int h, boolean isSelected) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                if (isSelected) {
                    // Tab selected: màu sáng với border xanh
                    g2.setColor(new Color(60, 64, 67));
                    g2.fillRoundRect(x + 2, y + 2, w - 4, h - 4, 8, 8);
                    // Border xanh ở dưới
                    g2.setColor(ACCENT_BLUE);
                    g2.setStroke(new BasicStroke(2));
                    g2.drawLine(x + 2, y + h - 2, x + w - 2, y + h - 2);
                } else {
                    // Tab unselected: màu tối hơn một chút
                    g2.setColor(new Color(45, 47, 50));
                    g2.fillRoundRect(x + 2, y + 2, w - 4, h - 4, 8, 8);
                }
                g2.dispose();
            }
            
            @Override
            protected void paintTabBorder(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w, int h, boolean isSelected) {
                // Không vẽ border mặc định
            }
            
            @Override
            protected void paintText(Graphics g, int tabPlacement, Font font, FontMetrics metrics, int tabIndex, String title, Rectangle textRect, boolean isSelected) {
                // Chỉ vẽ text nếu tab không có custom component
                if (chatTabbedPane.getTabComponentAt(tabIndex) == null) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setFont(font);
                    // Màu text dựa trên trạng thái selected
                    g2.setColor(isSelected ? new Color(255, 255, 255) : new Color(200, 200, 200));
                    g2.drawString(title, textRect.x, textRect.y + metrics.getAscent());
                    g2.dispose();
                }
            }
            
            @Override
            protected int getTabLabelShiftX(int tabPlacement, int tabIndex, boolean isSelected) {
                return 0;
            }
            
            @Override
            protected int getTabLabelShiftY(int tabPlacement, int tabIndex, boolean isSelected) {
                return 0;
            }
        });
        
        // Tab chat chung (public chat) - không có nút đóng
        JPanel publicChatTab = createPublicChatTab();
        int publicChatIndex = chatTabbedPane.getTabCount();
        chatTabbedPane.addTab("💬 Chat chung", publicChatTab);
        
        // Thêm custom tab component cho tab "Chat chung" để đảm bảo text hiển thị đúng màu
        JPanel publicChatTabHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        publicChatTabHeader.setOpaque(false);
        JLabel publicChatTitleLabel = new JLabel("💬 Chat chung");
        publicChatTitleLabel.setForeground(new Color(255, 255, 255)); // Text trắng sáng
        publicChatTitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        publicChatTabHeader.add(publicChatTitleLabel);
        chatTabbedPane.setTabComponentAt(publicChatIndex, publicChatTabHeader);
        
        // Listener để xử lý đóng tab (trừ tab "Chat chung")
        chatTabbedPane.addChangeListener(e -> {
            // Khi tab được chọn, focus vào messageField của tab đó
            int selectedIndex = chatTabbedPane.getSelectedIndex();
            if (selectedIndex >= 0) {
                Component selectedComponent = chatTabbedPane.getComponentAt(selectedIndex);
                if (selectedComponent instanceof JPanel) {
                    // Tìm messageField trong panel và focus
                    findAndFocusTextField((JPanel) selectedComponent);
                }
            }
        });
        
        chatPanel.add(chatTabbedPane, BorderLayout.CENTER);

        return chatPanel;
    }
    
    /**
     * Tạo tab chat chung (public chat) - Google Meet style
     */
    private JPanel createPublicChatTab() {
        JPanel tabPanel = new JPanel(new BorderLayout(0, 0));
        tabPanel.setOpaque(false);
        tabPanel.setBorder(new EmptyBorder(0, 0, 0, 0));

        // Chat area với message bubbles - Google Meet style (dùng JTextPane cho HTML)
        publicChatArea.setEditable(false);
        publicChatArea.setContentType("text/html");
        publicChatArea.setBackground(new Color(45, 47, 50)); // Sáng hơn để dễ nhìn hơn
        publicChatArea.setForeground(new Color(255, 255, 255)); // Text trắng sáng
        publicChatArea.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        publicChatArea.setBorder(new EmptyBorder(16, 16, 16, 16));
        publicChatArea.setCaretColor(new Color(255, 255, 255));
        
        // Set HTML editor kit với custom styling - cải thiện contrast và màu sắc
        HTMLEditorKit kit = new HTMLEditorKit();
        javax.swing.text.html.StyleSheet styleSheet = new javax.swing.text.html.StyleSheet();
        styleSheet.addRule("body { font-family: 'Segoe UI', sans-serif; font-size: 14px; color: #FFFFFF; background-color: #2D2F32; margin: 0; padding: 8px; }");
        styleSheet.addRule(".system { color: #B0B3B8; font-size: 13px; margin: 8px 0; line-height: 1.5; }");
        styleSheet.addRule(".user { color: #FFFFFF; margin: 8px 0; line-height: 1.5; }");
        styleSheet.addRule(".timestamp { color: #B0B3B8; font-size: 12px; margin-right: 8px; }");
        styleSheet.addRule(".username { color: #8AB4F8; font-weight: 600; }");
        styleSheet.addRule(".message { color: #FFFFFF; }");
        kit.setStyleSheet(styleSheet);
        HTMLDocument doc = (HTMLDocument) kit.createDefaultDocument();
        publicChatArea.setEditorKit(kit);
        publicChatArea.setDocument(doc);
        publicChatArea.setText("<body></body>");
        
        // Thêm HyperlinkListener để xử lý click vào link file
        publicChatArea.addHyperlinkListener(e -> {
            if (e.getEventType() == javax.swing.event.HyperlinkEvent.EventType.ACTIVATED) {
                try {
                    String url = null;
                    if (e.getURL() != null) {
                        url = e.getURL().toString();
                    } else if (e.getDescription() != null) {
                        url = e.getDescription();
                    }
                    
                    System.out.println("[DEBUG] Hyperlink clicked, URL: " + url);
                    System.out.println("[DEBUG] Event type: " + e.getEventType());
                    
                    if (url != null && url.startsWith("file://")) {
                        // Decode URL
                        String fileId = url.substring(7); // Remove "file://"
                        try {
                            fileId = java.net.URLDecoder.decode(fileId, java.nio.charset.StandardCharsets.UTF_8);
                        } catch (Exception decodeEx) {
                            // Ignore decode error
                        }
                        
                        System.out.println("[DEBUG] File ID: " + fileId);
                        System.out.println("[DEBUG] File data map size: " + fileDataMap.size());
                        System.out.println("[DEBUG] File data map keys: " + fileDataMap.keySet());
                        
                        String base64Data = fileDataMap.get(fileId);
                        String fileName = fileDataMap.get(fileId + "_name");
                        
                        System.out.println("[DEBUG] Base64 data found: " + (base64Data != null));
                        System.out.println("[DEBUG] File name found: " + (fileName != null));
                        
                        if (base64Data != null && fileName != null) {
                            downloadFile(base64Data, fileName);
                        } else {
                            // Thử tìm với tên file trực tiếp (fallback)
                            for (String key : fileDataMap.keySet()) {
                                if (key.endsWith("_name") && fileDataMap.get(key).equals(fileId)) {
                                    String actualFileId = key.substring(0, key.length() - 5);
                                    base64Data = fileDataMap.get(actualFileId);
                                    fileName = fileDataMap.get(key);
                                    if (base64Data != null) {
                                        downloadFile(base64Data, fileName);
                                        return;
                                    }
                                }
                            }
                            
                            JOptionPane.showMessageDialog(this,
                                "Không tìm thấy dữ liệu file.\nFile ID: " + fileId + "\nVui lòng thử lại.",
                                "Lỗi",
                                JOptionPane.ERROR_MESSAGE);
                        }
                    } else {
                        System.out.println("[DEBUG] URL không phải file:// hoặc null: " + url);
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this,
                        "Lỗi khi tải file: " + ex.getMessage(),
                        "Lỗi",
                        JOptionPane.ERROR_MESSAGE);
                    ex.printStackTrace();
                }
            }
        });
        
        // Thêm MouseListener để detect click vào link (backup method nếu HyperlinkListener không hoạt động)
        publicChatArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                try {
                    // Lấy vị trí click trong document
                    int offset = publicChatArea.viewToModel(e.getPoint());
                    
                    if (offset >= 0) {
                        HTMLDocument doc = (HTMLDocument) publicChatArea.getDocument();
                        javax.swing.text.Element elem = doc.getCharacterElement(offset);
                        javax.swing.text.AttributeSet attr = elem.getAttributes();
                        Object href = attr.getAttribute(javax.swing.text.html.HTML.Attribute.HREF);
                        
                        if (href != null) {
                            String url = href.toString();
                            System.out.println("[DEBUG] Mouse click on link, URL: " + url);
                            
                            if (url.startsWith("file://")) {
                                String fileId = url.substring(7);
                                String originalFileId = fileId;
                                
                                // Decode URL
                                try {
                                    fileId = java.net.URLDecoder.decode(fileId, java.nio.charset.StandardCharsets.UTF_8);
                                } catch (Exception decodeEx) {
                                    // Ignore, dùng fileId gốc
                                }
                                
                                System.out.println("[DEBUG] Mouse click - Original fileId: " + originalFileId);
                                System.out.println("[DEBUG] Mouse click - Decoded fileId: " + fileId);
                                
                                // Thử tìm với decoded ID trước
                                String base64Data = fileDataMap.get(fileId);
                                String fileName = fileDataMap.get(fileId + "_name");
                                
                                // Nếu không tìm thấy, thử với original (escaped) ID
                                if (base64Data == null) {
                                    base64Data = fileDataMap.get(originalFileId);
                                    fileName = fileDataMap.get(originalFileId + "_name");
                                }
                                
                                if (base64Data != null && fileName != null) {
                                    System.out.println("[DEBUG] Found file data, downloading: " + fileName);
                                    downloadFile(base64Data, fileName);
                                } else {
                                    System.out.println("[DEBUG] File not found in map");
                                    // Fallback search - tìm tất cả keys có chứa fileId
                                    for (String key : fileDataMap.keySet()) {
                                        if (key.contains(fileId) || key.contains(originalFileId)) {
                                            if (key.endsWith("_name")) {
                                                String actualFileId = key.substring(0, key.length() - 5);
                                                base64Data = fileDataMap.get(actualFileId);
                                                fileName = fileDataMap.get(key);
                                                if (base64Data != null) {
                                                    System.out.println("[DEBUG] Found file via fallback: " + fileName);
                                                    downloadFile(base64Data, fileName);
                                                    return;
                                                }
                                            }
                                        }
                                    }
                                    
                                    JOptionPane.showMessageDialog(ClientUI.this,
                                        "Không tìm thấy dữ liệu file.\nFile ID: " + fileId,
                                        "Lỗi",
                                        JOptionPane.ERROR_MESSAGE);
                                }
                            }
                        }
                    }
                } catch (Exception ex) {
                    // Ignore errors - có thể do click không phải vào link
                }
            }
        });
        
        // Custom scrollbar - Google Meet style
        JScrollPane chatScroll = new JScrollPane(publicChatArea) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(45, 47, 50)); // Sáng hơn
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        chatScroll.setBorder(null);
        chatScroll.setBackground(new Color(45, 47, 50));
        chatScroll.getViewport().setBackground(new Color(45, 47, 50));
        
        // Custom scrollbar styling
        chatScroll.getVerticalScrollBar().setUI(new javax.swing.plaf.basic.BasicScrollBarUI() {
            @Override
            protected void configureScrollBarColors() {
                this.thumbColor = new Color(138, 180, 248, 200); // Màu xanh sáng hơn
                this.trackColor = new Color(45, 47, 50);
            }
            
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
        });
        
        tabPanel.add(chatScroll, BorderLayout.CENTER);

        // Send panel - Google Meet style với rounded input và button
        JPanel sendPanel = new JPanel(new BorderLayout(12, 0));
        sendPanel.setOpaque(false);
        sendPanel.setBorder(new EmptyBorder(12, 16, 12, 16));
        
        // Input field với rounded corners - Google Meet style (cải thiện contrast)
        publicChatBox.setBackground(new Color(60, 64, 67)); // Google Meet input background
        publicChatBox.setForeground(new Color(232, 234, 237)); // Lighter text for better readability
        publicChatBox.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        publicChatBox.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        publicChatBox.setCaretColor(new Color(138, 180, 248)); // Blue caret for better visibility
        publicChatBox.addActionListener(e -> sendPublicChat());
        
        // Wrapper panel để tạo rounded border
        JPanel inputWrapper = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                int arc = 24; // Rounded corners
                Color borderColor = publicChatBox.hasFocus() ? new Color(138, 180, 248) : new Color(95, 99, 104, 150);
                int borderWidth = publicChatBox.hasFocus() ? 2 : 1;
                
                // Border
                g2.setStroke(new BasicStroke(borderWidth));
                g2.setColor(borderColor);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
                
                g2.dispose();
            }
        };
        inputWrapper.setOpaque(false);
        inputWrapper.setBorder(new EmptyBorder(0, 0, 0, 0));
        inputWrapper.add(publicChatBox, BorderLayout.CENTER);
        
        // Add focus effect
        publicChatBox.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                inputWrapper.repaint();
            }
            
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                inputWrapper.repaint();
            }
        });
        
        // Emoji button - bên trái input field
        JButton btnEmoji = new JButton("😀") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                int w = getWidth();
                int h = getHeight();
                int arc = 24;
                
                // Background với hover effect
                if (getModel().isRollover() || getModel().isPressed()) {
                    g2.setColor(new Color(60, 64, 67));
                    g2.fillRoundRect(0, 0, w, h, arc, arc);
                }
                
                // Emoji text
                g2.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 20));
                FontMetrics fm = g2.getFontMetrics();
                String emoji = "😀";
                int textX = (w - fm.stringWidth(emoji)) / 2;
                int textY = (h + fm.getAscent() - fm.getDescent()) / 2;
                g2.setColor(new Color(255, 255, 255));
                g2.drawString(emoji, textX, textY);
                
                g2.dispose();
            }
        };
        btnEmoji.setPreferredSize(new Dimension(40, 40));
        btnEmoji.setFocusPainted(false);
        btnEmoji.setBorderPainted(false);
        btnEmoji.setContentAreaFilled(false);
        btnEmoji.setOpaque(false);
        btnEmoji.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnEmoji.setToolTipText("Chọn emoji");
        
        // Emoji picker popup
        btnEmoji.addActionListener(e -> showEmojiPicker(publicChatBox, btnEmoji));
        
        // File button - bên trái emoji button
        JButton btnFile = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                int w = getWidth();
                int h = getHeight();
                int arc = 24;
                
                // Background với hover effect
                if (getModel().isRollover() || getModel().isPressed()) {
                    g2.setColor(new Color(60, 64, 67));
                    g2.fillRoundRect(0, 0, w, h, arc, arc);
                }
                
                // File icon (clip icon)
                g2.setColor(new Color(255, 255, 255));
                g2.setStroke(new BasicStroke(2));
                int iconSize = 20;
                int iconX = (w - iconSize) / 2;
                int iconY = (h - iconSize) / 2;
                
                // Draw clip icon
                int[] xPoints = {iconX + 4, iconX + iconSize - 4, iconX + iconSize - 2, iconX + 6};
                int[] yPoints = {iconY + 2, iconY + 2, iconY + iconSize - 2, iconY + iconSize - 2};
                g2.drawPolygon(xPoints, yPoints, 4);
                g2.drawArc(iconX + 2, iconY, 6, 6, 0, 180);
                
                g2.dispose();
            }
        };
        btnFile.setPreferredSize(new Dimension(40, 40));
        btnFile.setFocusPainted(false);
        btnFile.setBorderPainted(false);
        btnFile.setContentAreaFilled(false);
        btnFile.setOpaque(false);
        btnFile.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnFile.setToolTipText("Gửi file");
        btnFile.addActionListener(e -> chooseAndSendFile(publicChatBox, true));
        
        // Panel chứa file button, emoji button và input field
        JPanel inputContainer = new JPanel(new BorderLayout(8, 0));
        inputContainer.setOpaque(false);
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        buttonPanel.setOpaque(false);
        buttonPanel.add(btnFile);
        buttonPanel.add(btnEmoji);
        inputContainer.add(buttonPanel, BorderLayout.WEST);
        inputContainer.add(inputWrapper, BorderLayout.CENTER);
        
        sendPanel.add(inputContainer, BorderLayout.CENTER);

        // Send button - Google Meet style
        JButton btnSend = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                int w = getWidth();
                int h = getHeight();
                int arc = 24;
                
                // Background với hover effect
                Color bgColor = getModel().isRollover() ? ACCENT_BLUE_LIGHT : ACCENT_BLUE;
                g2.setColor(bgColor);
                g2.fillRoundRect(0, 0, w, h, arc, arc);
                
                // Icon
                int iconSize = 20;
                int iconX = (w - iconSize) / 2;
                int iconY = (h - iconSize) / 2;
                IconRenderer.drawIcon(g2, IconType.SEND, iconX, iconY, iconSize, iconSize, TEXT_PRIMARY);
                
                g2.dispose();
            }
        };
        btnSend.setPreferredSize(new Dimension(48, 48));
        btnSend.setFocusPainted(false);
        btnSend.setBorderPainted(false);
        btnSend.setContentAreaFilled(false);
        btnSend.setOpaque(false);
        btnSend.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnSend.addActionListener(e -> sendPublicChat());
        sendPanel.add(btnSend, BorderLayout.EAST);

        tabPanel.add(sendPanel, BorderLayout.SOUTH);
        return tabPanel;
    }

    private void sendPublicChat() {
        String msg = publicChatBox.getText().trim();
        if (!msg.isEmpty()) {
            publicChatBox.setText("");
            if (hooks != null) hooks.onSendChat(msg);
        }
    }
    
    /**
     * Chọn file và gửi
     */
    private void chooseAndSendFile(JTextField textField, boolean isPublic) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Chọn file để gửi");
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            if (selectedFile != null && selectedFile.exists()) {
                // Kiểm tra kích thước file (giới hạn 10MB)
                long fileSize = selectedFile.length();
                long maxSize = 10 * 1024 * 1024; // 10MB
                
                if (fileSize > maxSize) {
                    JOptionPane.showMessageDialog(this,
                        "File quá lớn! Kích thước tối đa: 10MB\nFile của bạn: " + 
                        String.format("%.2f MB", fileSize / (1024.0 * 1024.0)),
                        "Lỗi",
                        JOptionPane.ERROR_MESSAGE);
                    return;
                }
                
                try {
                    // Đọc file và encode base64
                    byte[] fileBytes = java.nio.file.Files.readAllBytes(selectedFile.toPath());
                    String base64Data = java.util.Base64.getEncoder().encodeToString(fileBytes);
                    String fileName = selectedFile.getName();
                    
                    // Tạo message với format đặc biệt: [FILE:base64data:filename]
                    String fileMessage = "[FILE:" + base64Data + ":" + fileName + "]";
                    
                    // Gửi file
                    if (isPublic) {
                        if (hooks != null) hooks.onSendChat(fileMessage);
                    } else {
                        // Tìm SSRC từ textField (cần lưu SSRC trong ChatTab)
                        // Tạm thời gửi như public chat
                        if (hooks != null) hooks.onSendChat(fileMessage);
                    }
                    
                    // Hiển thị thông báo
                    JOptionPane.showMessageDialog(this,
                        "Đang gửi file: " + fileName + "\nKích thước: " + 
                        String.format("%.2f KB", fileSize / 1024.0),
                        "Gửi file",
                        JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(this,
                        "Lỗi khi đọc file: " + e.getMessage(),
                        "Lỗi",
                        JOptionPane.ERROR_MESSAGE);
                    e.printStackTrace();
                }
            }
        }
    }
    
    /**
     * Hiển thị emoji picker popup
     */
    private void showEmojiPicker(JTextField textField, JButton triggerButton) {
        // Danh sách emoji phổ biến
        String[] emojis = {
            "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇",
            "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗", "😙", "😚",
            "😋", "😛", "😜", "🤪", "😝", "🤑", "🤗", "🤭", "🤫", "🤔",
            "🤐", "🤨", "😐", "😑", "😶", "😏", "😒", "🙄", "😬", "🤥",
            "😌", "😔", "😪", "🤤", "😴", "😷", "🤒", "🤕", "🤢", "🤮",
            "👍", "👎", "👌", "✌️", "🤞", "🤟", "🤘", "🤙", "👏", "🙌",
            "👐", "🤲", "🤝", "🙏", "✍️", "💪", "🦵", "🦶", "👂", "👃",
            "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔",
            "❣️", "💕", "💞", "💓", "💗", "💖", "💘", "💝", "💟", "☮️",
            "🎉", "🎊", "🎈", "🎁", "🏆", "🥇", "🥈", "🥉", "⚽", "🏀",
            "🔥", "💯", "⭐", "🌟", "✨", "⚡", "☀️", "🌙", "⭐", "💫"
        };
        
        // Tạo popup window
        JWindow popup = new JWindow(this);
        popup.setFocusableWindowState(false);
        
        JPanel emojiPanel = new JPanel(new GridLayout(0, 10, 4, 4));
        emojiPanel.setBackground(new Color(50, 52, 55));
        emojiPanel.setBorder(new EmptyBorder(12, 12, 12, 12));
        
        for (String emoji : emojis) {
            JButton emojiBtn = new JButton(emoji) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    
                    int w = getWidth();
                    int h = getHeight();
                    
                    // Background với hover effect
                    if (getModel().isRollover()) {
                        g2.setColor(new Color(60, 64, 67));
                        g2.fillRoundRect(0, 0, w, h, 8, 8);
                    }
                    
                    // Emoji text
                    g2.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 24));
                    FontMetrics fm = g2.getFontMetrics();
                    int textX = (w - fm.stringWidth(emoji)) / 2;
                    int textY = (h + fm.getAscent() - fm.getDescent()) / 2;
                    g2.setColor(new Color(255, 255, 255));
                    g2.drawString(emoji, textX, textY);
                    
                    g2.dispose();
                }
            };
            emojiBtn.setPreferredSize(new Dimension(36, 36));
            emojiBtn.setFocusPainted(false);
            emojiBtn.setBorderPainted(false);
            emojiBtn.setContentAreaFilled(false);
            emojiBtn.setOpaque(false);
            emojiBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            emojiBtn.addActionListener(e -> {
                // Chèn emoji vào text field tại vị trí cursor
                int caretPos = textField.getCaretPosition();
                String text = textField.getText();
                textField.setText(text.substring(0, caretPos) + emoji + text.substring(caretPos));
                textField.setCaretPosition(caretPos + emoji.length());
                textField.requestFocus();
                popup.setVisible(false);
                popup.dispose();
            });
            emojiPanel.add(emojiBtn);
        }
        
        JScrollPane scrollPane = new JScrollPane(emojiPanel);
        scrollPane.setBorder(null);
        scrollPane.setBackground(new Color(50, 52, 55));
        scrollPane.getViewport().setBackground(new Color(50, 52, 55));
        scrollPane.setPreferredSize(new Dimension(400, 250));
        
        popup.add(scrollPane);
        popup.pack();
        
        // Vị trí popup bên trên button
        Point buttonLocation = SwingUtilities.convertPoint(triggerButton, 0, 0, this);
        int x = buttonLocation.x;
        int y = buttonLocation.y - popup.getHeight() - 5;
        
        // Đảm bảo popup không ra ngoài màn hình
        if (y < 0) {
            y = buttonLocation.y + triggerButton.getHeight() + 5;
        }
        if (x + popup.getWidth() > getWidth()) {
            x = getWidth() - popup.getWidth();
        }
        if (x < 0) {
            x = 0;
        }
        
        popup.setLocation(x, y);
        popup.setVisible(true);
        
        // Đóng popup khi click ra ngoài
        java.awt.event.WindowAdapter focusAdapter = new java.awt.event.WindowAdapter() {
            @Override
            public void windowLostFocus(java.awt.event.WindowEvent e) {
                SwingUtilities.invokeLater(() -> {
                    if (popup.isVisible()) {
                        popup.setVisible(false);
                        popup.dispose();
                        removeWindowFocusListener(this);
                    }
                });
            }
        };
        addWindowFocusListener(focusAdapter);
        
        // Đóng popup khi click vào popup nhưng không phải emoji button
        popup.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // Không làm gì, để emoji button xử lý
            }
        });
    }
    
    /**
     * Lưu file data để download sau
     */
    private void saveFileData(String base64Data, String fileName) {
        // Lưu vào map để có thể download sau
        fileDataMap.put(fileName, base64Data);
    }
    
    /**
     * Download file từ base64 data
     */
    private void downloadFile(String base64Data, String fileName) {
        SwingUtilities.invokeLater(() -> {
            try {
                System.out.println("[DEBUG] Downloading file: " + fileName);
                System.out.println("[DEBUG] Base64 data length: " + (base64Data != null ? base64Data.length() : 0));
                
                // Decode base64
                byte[] fileBytes = java.util.Base64.getDecoder().decode(base64Data);
                System.out.println("[DEBUG] Decoded file size: " + fileBytes.length + " bytes");
                
                // Chọn nơi lưu file
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setDialogTitle("Lưu file");
                fileChooser.setSelectedFile(new File(fileName));
                fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                
                int result = fileChooser.showSaveDialog(this);
                if (result == JFileChooser.APPROVE_OPTION) {
                    File saveFile = fileChooser.getSelectedFile();
                    java.nio.file.Files.write(saveFile.toPath(), fileBytes);
                    
                    System.out.println("[DEBUG] File saved to: " + saveFile.getAbsolutePath());
                    
                    JOptionPane.showMessageDialog(this,
                        "Đã lưu file: " + saveFile.getName() + "\nVị trí: " + saveFile.getAbsolutePath(),
                        "Thành công",
                        JOptionPane.INFORMATION_MESSAGE);
                }
            } catch (IllegalArgumentException e) {
                JOptionPane.showMessageDialog(this,
                    "Lỗi: Dữ liệu file không hợp lệ (base64 decode failed).\n" + e.getMessage(),
                    "Lỗi",
                    JOptionPane.ERROR_MESSAGE);
                e.printStackTrace();
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this,
                    "Lỗi khi lưu file: " + e.getMessage(),
                    "Lỗi",
                    JOptionPane.ERROR_MESSAGE);
                e.printStackTrace();
            }
        });
    }
    
    // Map để lưu file data: fileName -> base64Data
    private final java.util.Map<String, String> fileDataMap = new java.util.concurrent.ConcurrentHashMap<>();

    public void setPeers(Set<PeerInfo> peers) {
        SwingUtilities.invokeLater(() -> {
            // Cập nhật peer list
        Vector<String> v = new Vector<>();
            for (var p : peers) {
                v.add("👤 " + p.getSsrc() + " @ " + p.getRtpEndpoint());
            }
        peerList.setListData(v);
            
            // Cập nhật grid: tạo cells cho tất cả peers (trừ self)
            if (mainGridPanel != null) {
                // Tính số peers (trừ self)
                int peerCount = 0;
                for (PeerInfo peer : peers) {
                    if (peer.getSsrc() != selfSsrc) peerCount++;
                }
                
                // Tự động tính số cột tối ưu (giống Google Meet)
                int cols = calculateOptimalColumns(peerCount);
                
                // Xóa tất cả components cũ
                mainGridPanel.removeAll();
                
                // Cập nhật grid layout với số cột mới
                mainGridPanel.setLayout(new java.awt.GridLayout(0, cols, 12, 12));
                
                // Tạo cell cho mỗi peer (trừ self)
                for (PeerInfo peer : peers) {
                    if (peer.getSsrc() == selfSsrc) continue; // Bỏ qua self
                    
                    // Lưu tên từ PeerInfo vào peerNames map (ưu tiên tên từ server)
                    String peerName = peer.getName();
                    if (peerName != null && !peerName.isEmpty()) {
                        peerNames.put(peer.getSsrc(), peerName);
                    }
                    
                    // Tạo hoặc lấy VideoRenderer cho peer này
                    VideoRenderer vr = peerVideoViews.computeIfAbsent(peer.getSsrc(), k -> {
                        VideoRenderer renderer = new VideoRenderer();
                        // Tạo avatar từ tên người dùng
                        String displayName = peerName != null && !peerName.isEmpty() 
                            ? peerName 
                            : peerNames.getOrDefault(peer.getSsrc(), "Peer #" + (peer.getSsrc() % 1000));
                        renderer.setUserName(displayName);
                        // Mặc định hiển thị avatar
                        renderer.showAvatar();
                        return renderer;
                    });
                    
                    // Cập nhật avatar nếu tên mới
                    if (peerName != null && !peerName.isEmpty()) {
                        vr.setUserName(peerName);
                    }
                    
                    // Tạo panel với label và shadow effect
                    JPanel peerPanel = new JPanel(new BorderLayout(4, 4)) {
                        @Override
                        protected void paintComponent(Graphics g) {
                            super.paintComponent(g);
                            Graphics2D g2 = (Graphics2D) g.create();
                            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                            
                            // Shadow
                            g2.setColor(new Color(0, 0, 0, 40));
                            g2.fillRoundRect(2, 2, getWidth() - 2, getHeight() - 2, 10, 10);
                            
                            // Background
                            g2.setColor(BG_SECONDARY);
                            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                            
                            // Border
                            g2.setStroke(new BasicStroke(1.5f));
                            g2.setColor(new Color(BG_TERTIARY.getRed(), BG_TERTIARY.getGreen(), BG_TERTIARY.getBlue(), 180));
                            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                            
                            g2.dispose();
                        }
                    };
                    peerPanel.setOpaque(false);
                    peerPanel.setBorder(new EmptyBorder(4, 4, 4, 4));
                    
                    // Dùng JLayeredPane để overlay tên và chat icon lên video
                    JLayeredPane peerLayeredPane = new JLayeredPane();
                    peerLayeredPane.setLayout(null);
                    peerLayeredPane.setOpaque(false);
                    
                    // Video renderer chiếm toàn bộ
                    vr.setBounds(0, 0, Integer.MAX_VALUE, Integer.MAX_VALUE);
                    peerLayeredPane.add(vr, JLayeredPane.DEFAULT_LAYER);
                    
                    // Tên hiển thị overlay ở dưới cùng - ưu tiên tên từ PeerInfo
                    String peerDisplayName;
                    if (peerName != null && !peerName.isEmpty()) {
                        peerDisplayName = peerName;
                    } else {
                        peerDisplayName = peerNames.getOrDefault(peer.getSsrc(), "Peer #" + (peer.getSsrc() % 1000));
                    }
                    JPanel nameOverlay = new JPanel(new BorderLayout(8, 0)) {
                        @Override
                        protected void paintComponent(Graphics g) {
                            super.paintComponent(g);
                            // Semi-transparent background
                            Graphics2D g2 = (Graphics2D) g.create();
                            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                            g2.setColor(new Color(0, 0, 0, 120));
                            g2.fillRect(0, 0, getWidth(), getHeight());
                            g2.dispose();
                        }
                    };
                    nameOverlay.setOpaque(false);
                    nameOverlay.setBorder(new EmptyBorder(6, 10, 6, 10));
                    
                    JLabel peerLabel = new JLabel(peerDisplayName);
                    peerLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
                    peerLabel.setForeground(TEXT_PRIMARY);
                    // Lưu reference để có thể cập nhật sau
                    peerLabels.put(peer.getSsrc(), peerLabel);
                    nameOverlay.add(peerLabel, BorderLayout.WEST);
                    
                    // Chat icon ở góc trên phải của video
                    JLabel chatIconLabel = new JLabel() {
                        @Override
                        protected void paintComponent(Graphics g) {
                            super.paintComponent(g);
                            Graphics2D g2 = (Graphics2D) g.create();
                            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                            // Background circle
                            g2.setColor(new Color(60, 64, 67, 200));
                            g2.fillOval(0, 0, getWidth(), getHeight());
                            // Icon
                            IconRenderer.drawIcon(g2, IconType.CHAT, 4, 4, getWidth() - 8, getHeight() - 8, TEXT_PRIMARY);
                            g2.dispose();
                        }
                    };
                    chatIconLabel.setPreferredSize(new Dimension(32, 32));
                    chatIconLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
                    chatIconLabel.setToolTipText("Click để chat riêng");
                    chatIconLabel.addMouseListener(new MouseAdapter() {
                        @Override
                        public void mouseClicked(MouseEvent e) {
                            openPrivateChat(peer.getSsrc());
                        }
                    });
                    
                    // Add overlay elements
                    peerLayeredPane.add(nameOverlay, JLayeredPane.PALETTE_LAYER);
                    peerLayeredPane.add(chatIconLabel, JLayeredPane.PALETTE_LAYER);
                    
                    // Set bounds for overlay elements when resized
                    peerLayeredPane.addComponentListener(new java.awt.event.ComponentAdapter() {
                        @Override
                        public void componentResized(java.awt.event.ComponentEvent e) {
                            int w = peerLayeredPane.getWidth();
                            int h = peerLayeredPane.getHeight();
                            vr.setBounds(0, 0, w, h);
                            nameOverlay.setBounds(0, h - 40, w, 40);
                            chatIconLabel.setBounds(w - 40, 8, 32, 32);
                        }
                    });
                    
                    peerPanel.add(peerLayeredPane, BorderLayout.CENTER);
                    
                    // Lưu reference đến panel
                    peerPanels.put(peer.getSsrc(), peerPanel);
                    
                    // Thêm vào grid
                    mainGridPanel.add(peerPanel);
                }
                
                mainGridPanel.revalidate();
                mainGridPanel.repaint();
            }
        });
    }

    public void addChat(String from, String msg) {
        SwingUtilities.invokeLater(() -> {
            // Lưu tên người dùng từ tin nhắn (format: "Name #XXX")
            // Tách tên từ format "Name #XXX" hoặc chỉ "Name"
            String cleanName = from;
            long ssrcFromMsg = 0;
            if (from != null && from.contains(" #")) {
                cleanName = from.substring(0, from.indexOf(" #"));
                try {
                    String ssrcStr = from.substring(from.indexOf("#") + 1).trim();
                    ssrcFromMsg = Long.parseLong(ssrcStr);
                } catch (Exception e) {
                    // Ignore
                }
            }
            
            // Tìm SSRC từ peerNames bằng cách so sánh số cuối (3 chữ số cuối của SSRC)
            if (cleanName != null && !cleanName.isEmpty() && ssrcFromMsg > 0) {
                // Tìm SSRC có 3 số cuối khớp với ssrcFromMsg
                boolean found = false;
                for (Long ssrc : peerPanels.keySet()) {
                    if ((ssrc % 1000) == ssrcFromMsg) {
                        peerNames.put(ssrc, cleanName);
                        updatePeerLabel(ssrc, cleanName);
                        found = true;
                        break;
                    }
                }
                // Nếu chưa tìm thấy, thử tìm trong peerNames
                if (!found) {
                    for (java.util.Map.Entry<Long, String> entry : peerNames.entrySet()) {
                        if ((entry.getKey() % 1000) == ssrcFromMsg) {
                            peerNames.put(entry.getKey(), cleanName);
                            updatePeerLabel(entry.getKey(), cleanName);
                            break;
                        }
                    }
                }
            }
            
            String timestamp = java.time.LocalTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("HH:mm")
            );
            
            // Format message với HTML styling - đẹp và dễ đọc hơn
            String htmlMessage;
            boolean isSystem = from != null && from.startsWith("[SYSTEM]");
            boolean isFile = msg != null && msg.startsWith("[FILE:") && msg.endsWith("]");
            
            if (isFile) {
                // Parse file message: [FILE:base64data:filename]
                try {
                    String fileContent = msg.substring(6, msg.length() - 1); // Remove [FILE: and ]
                    int lastColon = fileContent.lastIndexOf(':');
                    if (lastColon > 0) {
                        String base64Data = fileContent.substring(0, lastColon);
                        String fileName = fileContent.substring(lastColon + 1);
                        
                        // Lưu file data để download
                        saveFileData(base64Data, fileName);
                        
                        // Hiển thị file với icon và button download
                        String escapedFrom = from.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
                        String escapedFileName = fileName.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
                        
                        // Tạo unique ID cho file
                        String fileId = "file_" + System.currentTimeMillis() + "_" + fileName.hashCode();
                        
                        // Escape fileId cho URL (thay thế các ký tự đặc biệt)
                        String escapedFileId = fileId.replace(" ", "%20").replace("#", "%23");
                        
                        htmlMessage = String.format(
                            "<div class='user'><span class='timestamp'>%s</span> <span class='username'>%s</span>: " +
                            "<span class='message'>📎 <strong>%s</strong> " +
                            "<a href='file://%s' style='color: #8AB4F8; text-decoration: underline; cursor: pointer;'>[Tải về]</a>" +
                            "</span></div>",
                            timestamp, escapedFrom, escapedFileName, escapedFileId
                        );
                        
                        // Lưu file data với ID
                        fileDataMap.put(fileId, base64Data);
                        fileDataMap.put(fileId + "_name", fileName);
                    } else {
                        // Invalid file format
                        String escapedFrom = from.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
                        String escapedMsg = msg.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
                        htmlMessage = String.format(
                            "<div class='user'><span class='timestamp'>%s</span> <span class='username'>%s</span>: <span class='message'>%s</span></div>",
                            timestamp, escapedFrom, escapedMsg
                        );
                    }
                } catch (Exception e) {
                    // Error parsing file, show as normal message
                    String escapedFrom = from.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
                    String escapedMsg = msg.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
                    htmlMessage = String.format(
                        "<div class='user'><span class='timestamp'>%s</span> <span class='username'>%s</span>: <span class='message'>%s</span></div>",
                        timestamp, escapedFrom, escapedMsg
                    );
                }
            } else if (isSystem) {
                // System messages - màu xám nhạt, font nhỏ hơn
                String systemMsg = msg.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
                htmlMessage = String.format(
                    "<div class='system'><span class='timestamp'>%s</span> <span class='message'>%s</span></div>",
                    timestamp, systemMsg
                );
            } else {
                // User messages - màu sáng, tên người dùng màu xanh
                String escapedFrom = from.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
                String escapedMsg = msg.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
                htmlMessage = String.format(
                    "<div class='user'><span class='timestamp'>%s</span> <span class='username'>%s</span>: <span class='message'>%s</span></div>",
                    timestamp, escapedFrom, escapedMsg
                );
            }
            
            // Append HTML message
            try {
                HTMLDocument doc = (HTMLDocument) publicChatArea.getDocument();
                HTMLEditorKit kit = (HTMLEditorKit) publicChatArea.getEditorKit();
                kit.insertHTML(doc, doc.getLength(), htmlMessage, 0, 0, null);
                publicChatArea.setCaretPosition(publicChatArea.getDocument().getLength());
            } catch (Exception e) {
                // Fallback nếu có lỗi với HTML
                String plainMessage = String.format("[%s] %s: %s\n", timestamp, from, msg);
                publicChatArea.setText(publicChatArea.getText() + plainMessage);
                publicChatArea.setCaretPosition(publicChatArea.getDocument().getLength());
            }
        });
    }
    
    /**
     * Toggle chat panel visibility
     * Khi bật chat, video panel tự động thu nhỏ để nhường chỗ (giống Google Meet)
     * Dùng remove/add thay vì setVisible để BorderLayout tự động điều chỉnh
     */
    private void toggleChatPanel() {
        SwingUtilities.invokeLater(() -> {
            if (chatVisible) {
                // Hiển thị chat panel - add vào BorderLayout.EAST
                if (mainContainer != null && chatPanel.getParent() == null) {
                    mainContainer.add(chatPanel, BorderLayout.EAST);
                }
            } else {
                // Ẩn chat panel - remove khỏi layout
                if (mainContainer != null && chatPanel.getParent() == mainContainer) {
                    mainContainer.remove(chatPanel);
                }
            }
            
            // Force revalidate để layout tự động điều chỉnh
            if (mainContainer != null) {
                mainContainer.revalidate();
                mainContainer.repaint();
            }
            revalidate();
            repaint();
        });
    }
    
    /**
     * Cập nhật label trên peer panel khi có tên mới
     */
    private void updatePeerLabel(long ssrc, String name) {
        SwingUtilities.invokeLater(() -> {
            // Cập nhật avatar nếu có VideoRenderer
            VideoRenderer vr = peerVideoViews.get(ssrc);
            if (vr != null && name != null && !name.isEmpty()) {
                vr.setUserName(name);
                // Nếu đang hiển thị avatar, cập nhật ngay
                if (vr.isShowingAvatar()) {
                    vr.showAvatar();
                }
            }
            
            JLabel peerLabel = peerLabels.get(ssrc);
            if (peerLabel != null) {
                peerLabel.setText(name);
                peerLabel.repaint();
            }
            
            // Cũng cập nhật lại grid nếu cần
            JPanel peerPanel = peerPanels.get(ssrc);
            if (peerPanel != null) {
                peerPanel.revalidate();
                peerPanel.repaint();
            }
        });
    }

    // Renderers
    public void updateSelf(BufferedImage img) { 
        SwingUtilities.invokeLater(() -> selfView.updateFrame(img));
    }
    
    public void updateRemote(BufferedImage img) { 
        SwingUtilities.invokeLater(() -> remoteView.updateFrame(img));
    }
    
    // Map để track thời gian nhận video cuối cùng từ mỗi peer
    private final java.util.Map<Long, Long> lastVideoTimeMap = new java.util.concurrent.ConcurrentHashMap<>();
    
    /**
     * Cập nhật video từ một peer cụ thể
     */
    public void updatePeerVideo(long peerSsrc, BufferedImage img) {
        SwingUtilities.invokeLater(() -> {
            // Cập nhật thời gian nhận video
            lastVideoTimeMap.put(peerSsrc, System.currentTimeMillis());
            
            // Tạo hoặc lấy VideoRenderer cho peer này (nếu chưa có thì sẽ được tạo trong setPeers)
            VideoRenderer vr = peerVideoViews.get(peerSsrc);
            if (vr == null) {
                // Nếu chưa có trong grid, tạo mới (trường hợp đặc biệt)
                vr = peerVideoViews.computeIfAbsent(peerSsrc, k -> {
                    VideoRenderer renderer = new VideoRenderer();
                    // Tạo avatar từ tên nếu có
                    String displayName = peerNames.getOrDefault(peerSsrc, "Peer #" + (peerSsrc % 1000));
                    renderer.setUserName(displayName);
                    renderer.showAvatar(); // Mặc định avatar
                    return renderer;
                });
            }
            
            // Cập nhật frame video
            vr.updateFrame(img);
        });
    }
    
    /**
     * Kiểm tra và tự động quay lại avatar nếu không nhận được video trong 2 giây
     */
    public void checkAndShowAvatars() {
        SwingUtilities.invokeLater(() -> {
            long now = System.currentTimeMillis();
            for (java.util.Map.Entry<Long, VideoRenderer> entry : peerVideoViews.entrySet()) {
                long peerSsrc = entry.getKey();
                VideoRenderer vr = entry.getValue();
                
                Long lastTime = lastVideoTimeMap.get(peerSsrc);
                if (lastTime != null && (now - lastTime) > 2000) {
                    // Không nhận được video trong 2 giây, quay lại avatar
                    vr.showAvatar();
                }
            }
        });
    }
    
    
    public void showSelfAvatar() {
        SwingUtilities.invokeLater(() -> selfView.showAvatar());
    }
    
    public void showRemoteAvatar() {
        SwingUtilities.invokeLater(() -> remoteView.showAvatar());
    }
    
    /**
     * Mở hoặc chuyển đến tab chat riêng với một peer
     */
    private void openPrivateChat(long peerSsrc) {
        SwingUtilities.invokeLater(() -> {
            ChatTab chatTab = privateChatTabs.get(peerSsrc);
            if (chatTab == null) {
                // Tạo tab mới
                chatTab = new ChatTab(peerSsrc);
                privateChatTabs.put(peerSsrc, chatTab);
                
                // Sử dụng tên người dùng nếu có, nếu không thì dùng Peer #XXX
                String displayName = peerNames.getOrDefault(peerSsrc, "Peer #" + (peerSsrc % 1000));
                String tabTitle = "📩 Tin nhắn cho " + displayName;
                int tabIndex = chatTabbedPane.getTabCount();
                chatTabbedPane.addTab(tabTitle, chatTab.panel);
                
                // Thêm nút đóng cho tab (trừ tab "Chat chung")
                JPanel tabHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
                tabHeader.setOpaque(false);
                // Hiển thị đầy đủ tên tab với màu sắc cải thiện
                JLabel titleLabel = new JLabel(tabTitle);
                titleLabel.setForeground(new Color(255, 255, 255)); // Text trắng sáng
                titleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
                
                JButton closeButton = new JButton("×");
                closeButton.setFont(new Font("Segoe UI", Font.BOLD, 16));
                closeButton.setForeground(TEXT_SECONDARY);
                closeButton.setBorderPainted(false);
                closeButton.setContentAreaFilled(false);
                closeButton.setFocusPainted(false);
                closeButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
                closeButton.setPreferredSize(new Dimension(20, 20));
                closeButton.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        closeButton.setForeground(ACCENT_RED);
                    }
                    
                    @Override
                    public void mouseExited(MouseEvent e) {
                        closeButton.setForeground(Color.DARK_GRAY);
                    }
                });
                final ChatTab finalChatTab = chatTab;
                final long finalPeerSsrc = peerSsrc;
                closeButton.addActionListener(e -> {
                    int index = chatTabbedPane.indexOfComponent(finalChatTab.panel);
                    if (index >= 0) {
                        chatTabbedPane.removeTabAt(index);
                        privateChatTabs.remove(finalPeerSsrc);
                    }
                });
                
                tabHeader.add(titleLabel);
                tabHeader.add(closeButton);
                chatTabbedPane.setTabComponentAt(tabIndex, tabHeader);
                
                // Chuyển đến tab mới
                chatTabbedPane.setSelectedIndex(tabIndex);
            } else {
                // Chuyển đến tab đã có
                int tabIndex = chatTabbedPane.indexOfComponent(chatTab.panel);
                if (tabIndex >= 0) {
                    chatTabbedPane.setSelectedIndex(tabIndex);
                }
            }
        });
    }
    
    /**
     * Helper method để tìm và focus vào textField trong một panel
     */
    private void findAndFocusTextField(JPanel panel) {
        for (Component comp : panel.getComponents()) {
            if (comp instanceof JTextField) {
                comp.requestFocus();
                return;
            } else if (comp instanceof JPanel) {
                findAndFocusTextField((JPanel) comp);
            }
        }
    }
    
    /**
     * Nhận tin nhắn chat riêng từ một peer
     */
    public void addPrivateChat(long fromSsrc, String fromName, String message) {
        SwingUtilities.invokeLater(() -> {
            // Lưu tên người dùng (tách tên từ format "Name #XXX")
            String cleanName = fromName;
            if (fromName != null && fromName.contains(" #")) {
                cleanName = fromName.substring(0, fromName.indexOf(" #"));
            }
            boolean nameUpdated = false;
            if (cleanName != null && !cleanName.isEmpty()) {
                String oldName = peerNames.get(fromSsrc);
                peerNames.put(fromSsrc, cleanName);
                nameUpdated = !cleanName.equals(oldName);
                // Cập nhật label ngay lập tức
                if (nameUpdated) {
                    updatePeerLabel(fromSsrc, cleanName);
                }
            }
            
            ChatTab chatTab = privateChatTabs.get(fromSsrc);
            if (chatTab == null) {
                // Tạo tab mới nếu chưa có
                chatTab = new ChatTab(fromSsrc);
                privateChatTabs.put(fromSsrc, chatTab);
                
                // Sử dụng tên người dùng nếu có, nếu không thì dùng Peer #XXX
                String displayName = peerNames.getOrDefault(fromSsrc, "Peer #" + (fromSsrc % 1000));
                String tabTitle = "📩 Tin nhắn cho " + displayName;
                int tabIndex = chatTabbedPane.getTabCount();
                chatTabbedPane.addTab(tabTitle, chatTab.panel);
                
                // Thêm nút đóng cho tab
                JPanel tabHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
                tabHeader.setOpaque(false);
                // Hiển thị đầy đủ tên tab với màu sắc cải thiện
                JLabel titleLabel = new JLabel(tabTitle);
                titleLabel.setForeground(new Color(255, 255, 255)); // Text trắng sáng
                titleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
                
                JButton closeButton = new JButton("×");
                closeButton.setFont(new Font("Segoe UI", Font.BOLD, 18));
                closeButton.setForeground(new Color(200, 200, 200)); // Màu xám sáng hơn
                closeButton.setBorderPainted(false);
                closeButton.setContentAreaFilled(false);
                closeButton.setFocusPainted(false);
                closeButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
                closeButton.setPreferredSize(new Dimension(24, 24));
                closeButton.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        closeButton.setForeground(ACCENT_RED);
                        closeButton.setBackground(new Color(ACCENT_RED.getRed(), ACCENT_RED.getGreen(), ACCENT_RED.getBlue(), 30));
                    }
                    
                    @Override
                    public void mouseExited(MouseEvent e) {
                        closeButton.setForeground(new Color(200, 200, 200));
                        closeButton.setBackground(null);
                    }
                });
                final ChatTab finalChatTab2 = chatTab;
                final long finalFromSsrc = fromSsrc;
                closeButton.addActionListener(e -> {
                    int index = chatTabbedPane.indexOfComponent(finalChatTab2.panel);
                    if (index >= 0) {
                        chatTabbedPane.removeTabAt(index);
                        privateChatTabs.remove(finalFromSsrc);
                    }
                });
                
                tabHeader.add(titleLabel);
                tabHeader.add(closeButton);
                chatTabbedPane.setTabComponentAt(tabIndex, tabHeader);
                
                // Chuyển đến tab mới
                chatTabbedPane.setSelectedIndex(tabIndex);
            } else if (nameUpdated) {
                // Cập nhật tên tab nếu tên đã thay đổi
                String displayName = peerNames.getOrDefault(fromSsrc, "Peer #" + (fromSsrc % 1000));
                String newTabTitle = "📩 Tin nhắn cho " + displayName;
                int tabIndex = chatTabbedPane.indexOfComponent(chatTab.panel);
                if (tabIndex >= 0) {
                    chatTabbedPane.setTitleAt(tabIndex, newTabTitle);
                    // Cập nhật tab header component
                    Component tabComponent = chatTabbedPane.getTabComponentAt(tabIndex);
                    if (tabComponent instanceof JPanel) {
                        JPanel tabHeader = (JPanel) tabComponent;
                        for (Component comp : tabHeader.getComponents()) {
                            if (comp instanceof JLabel) {
                                ((JLabel) comp).setText(newTabTitle);
                                break;
                            }
                        }
                    }
                }
            }
            chatTab.addMessage(fromName, message, false);
        });
    }
    
    /**
     * Cập nhật tên tab khi nhận tin nhắn chat chung (để lấy tên người dùng)
     */
    public void updatePeerNameFromChat(String from, long ssrc) {
        SwingUtilities.invokeLater(() -> {
            // Tách tên từ format "Name #XXX"
            String cleanName = from;
            if (from != null && from.contains(" #")) {
                cleanName = from.substring(0, from.indexOf(" #"));
            }
            if (cleanName != null && !cleanName.isEmpty()) {
                String oldName = peerNames.get(ssrc);
                peerNames.put(ssrc, cleanName);
                
                // Cập nhật avatar nếu có VideoRenderer
                VideoRenderer vr = peerVideoViews.get(ssrc);
                if (vr != null) {
                    vr.setUserName(cleanName);
                    // Nếu đang hiển thị avatar, cập nhật ngay
                    if (vr.isShowingAvatar()) {
                        vr.showAvatar();
                    }
                }
                
                // Cập nhật tab nếu đã có
                ChatTab chatTab = privateChatTabs.get(ssrc);
                if (chatTab != null) {
                    String displayName = peerNames.getOrDefault(ssrc, "Peer #" + (ssrc % 1000));
                    String newTabTitle = "📩 Tin nhắn cho " + displayName;
                    int tabIndex = chatTabbedPane.indexOfComponent(chatTab.panel);
                    if (tabIndex >= 0) {
                        chatTabbedPane.setTitleAt(tabIndex, newTabTitle);
                        // Cập nhật tab header component
                        Component tabComponent = chatTabbedPane.getTabComponentAt(tabIndex);
                        if (tabComponent instanceof JPanel) {
                            JPanel tabHeader = (JPanel) tabComponent;
                            for (Component comp : tabHeader.getComponents()) {
                                if (comp instanceof JLabel) {
                                    ((JLabel) comp).setText(newTabTitle);
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        });
    }
    
    /**
     * Gửi tin nhắn chat riêng đến một peer
     */
    public void sendPrivateChat(long toSsrc, String message) {
        if (hooks instanceof ExtendedHooks) {
            ((ExtendedHooks) hooks).onSendPrivateChat(toSsrc, message);
        }
    }
    
    /**
     * Mở file chooser để chọn avatar từ máy
     */
    private void chooseAvatarFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Chọn Avatar");
        fileChooser.setFileFilter(new FileNameExtensionFilter(
            "Image Files (*.jpg, *.jpeg, *.png, *.gif, *.bmp)", 
            "jpg", "jpeg", "png", "gif", "bmp"
        ));
        
        // Set default directory to user's Pictures folder if available
        String userHome = System.getProperty("user.home");
        File picturesDir = new File(userHome, "Pictures");
        if (picturesDir.exists() && picturesDir.isDirectory()) {
            fileChooser.setCurrentDirectory(picturesDir);
        }
        
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            try {
                // Load image from file
                BufferedImage img = ImageIO.read(selectedFile);
                if (img != null) {
                    // Set custom avatar
                    selfView.setCustomAvatar(img);
                    // Nếu đang tắt camera, hiển thị avatar mới ngay
                    if (!camOn) {
                        selfView.showAvatar();
                    }
                    
                    // Hiển thị thông báo thành công
                    JOptionPane.showMessageDialog(
                        this,
                        "Avatar đã được cập nhật!",
                        "Thành công",
                        JOptionPane.INFORMATION_MESSAGE
                    );
                } else {
                    throw new Exception("Không thể đọc file ảnh");
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(
                    this,
                    "Lỗi khi tải avatar: " + ex.getMessage(),
                    "Lỗi",
                    JOptionPane.ERROR_MESSAGE
                );
            }
        }
    }

    // Modern button class with rounded corners, icons, and active state
    private static class ModernButton extends JButton {
        private boolean active = false;
        private final String baseText;
        private final IconType iconType;
        private final boolean isPrimary;
        private boolean isHovered = false;

        public ModernButton(IconType iconType, String text, boolean isPrimary) {
            super(text);
            this.iconType = iconType;
            this.baseText = text;
            this.isPrimary = isPrimary;
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setFont(new Font("Segoe UI", Font.BOLD, 13));
            setPreferredSize(new Dimension(140, 42));
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            
            // Hover effect
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    if (isEnabled()) {
                        isHovered = true;
                        repaint();
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    isHovered = false;
                    repaint();
                }
            });
        }

        public void setActive(boolean active) {
            this.active = active;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            
            int w = getWidth();
            int h = getHeight();
            int arc = 24;
            
            // Determine colors based on state
            Color bgColor, iconColor, textColor;
            if (active) {
                bgColor = isHovered ? ACCENT_RED_LIGHT : ACCENT_RED;
                iconColor = TEXT_PRIMARY;
                textColor = TEXT_PRIMARY;
            } else if (isPrimary) {
                bgColor = isHovered ? ACCENT_BLUE_LIGHT : ACCENT_BLUE;
                iconColor = TEXT_PRIMARY;
                textColor = TEXT_PRIMARY;
            } else {
                bgColor = isHovered ? new Color(BG_TERTIARY.getRed() + 15, BG_TERTIARY.getGreen() + 15, BG_TERTIARY.getBlue() + 15) : BG_TERTIARY;
                iconColor = active ? ACCENT_RED : TEXT_PRIMARY;
                textColor = TEXT_PRIMARY;
            }
            
            // Shadow effect
            if (isHovered || active) {
                g2.setColor(new Color(0, 0, 0, 40));
                g2.fillRoundRect(2, 3, w - 2, h - 2, arc, arc);
            }
            
            // Button background with gradient
            GradientPaint gradient = new GradientPaint(
                0, 0, bgColor,
                0, h, bgColor.darker()
            );
            g2.setPaint(gradient);
            g2.fillRoundRect(0, 0, w, h, arc, arc);
            
            // Border
            g2.setStroke(new BasicStroke(1.5f));
            g2.setColor(isHovered ? bgColor.brighter() : new Color(bgColor.getRed(), bgColor.getGreen(), bgColor.getBlue(), 150));
            g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
            
            // Icon and text
            int iconSize = 18;
            int iconX = 14;
            int iconY = (h - iconSize) / 2;
            
            IconRenderer.drawIcon(g2, iconType, iconX, iconY, iconSize, iconSize, iconColor);
            
            // Text
            FontMetrics fm = g2.getFontMetrics();
            int textX = iconX + iconSize + 8;
            int textY = (h - fm.getHeight()) / 2 + fm.getAscent();
            g2.setColor(textColor);
            g2.setFont(getFont());
            g2.drawString(baseText, textX, textY);
            
            g2.dispose();
        }
    }
    
    // Icon types enum
    private enum IconType {
        CAMERA, MICROPHONE, VIDEO, CHAT, SEND, USER
    }
    
    // Icon renderer class
    private static class IconRenderer {
        public static void drawIcon(Graphics g, IconType type, int x, int y, int w, int h, Color color) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.setColor(color);
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            
            float scaleX = w / 24f;
            float scaleY = h / 24f;
            g2.translate(x, y);
            g2.scale(scaleX, scaleY);
            
            switch (type) {
                case CAMERA:
                    drawCameraIcon(g2);
                    break;
                case MICROPHONE:
                    drawMicrophoneIcon(g2);
                    break;
                case VIDEO:
                    drawVideoIcon(g2);
                    break;
                case CHAT:
                    drawChatIcon(g2);
                    break;
                case SEND:
                    drawSendIcon(g2);
                    break;
                case USER:
                    drawUserIcon(g2);
                    break;
            }
            
            g2.dispose();
        }
        
        public static void drawIcon(Graphics g, IconType type, int w, int h, Color color) {
            drawIcon(g, type, 0, 0, w, h, color);
        }
        
        /**
         * Vẽ icon với state (có đường gạch chéo mỏng nếu tắt - giống Google Meet)
         */
        public static void drawIconWithState(Graphics g, IconType type, int x, int y, int w, int h, Color color, boolean showSlash) {
            // Vẽ icon bình thường
            drawIcon(g, type, x, y, w, h, color);
            
            // Nếu cần hiển thị đường gạch chéo (khi tắt)
            if (showSlash) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                // Đường gạch chéo mỏng (stroke width nhỏ hơn)
                g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                
                // Scale để vẽ trong không gian 24x24
                float scaleX = w / 24f;
                float scaleY = h / 24f;
                g2.translate(x, y);
                g2.scale(scaleX, scaleY);
                
                // Chỉ vẽ 1 đường chéo từ góc trên trái xuống góc dưới phải (giống Google Meet)
                g2.drawLine(5, 5, 19, 19);
                
                g2.dispose();
            }
        }
        
        private static void drawCameraIcon(Graphics2D g2) {
            // Camera body
            g2.drawRoundRect(4, 6, 16, 12, 2, 2);
            // Lens
            g2.drawOval(8, 8, 8, 8);
            // Flash
            g2.fillOval(18, 7, 2, 2);
        }
        
        private static void drawMicrophoneIcon(Graphics2D g2) {
            // Mic body
            g2.drawRoundRect(8, 4, 8, 12, 2, 2);
            // Stand
            g2.drawLine(12, 16, 12, 20);
            // Base
            g2.drawArc(6, 18, 12, 4, 0, 180);
        }
        
        private static void drawVideoIcon(Graphics2D g2) {
            // Video screen
            g2.drawRoundRect(2, 4, 20, 14, 3, 3);
            // Play button
            int[] xPoints = {10, 10, 16};
            int[] yPoints = {8, 14, 11};
            g2.fillPolygon(xPoints, yPoints, 3);
        }
        
        private static void drawChatIcon(Graphics2D g2) {
            // Chat bubble
            g2.drawRoundRect(2, 4, 18, 12, 3, 3);
            // Tail
            int[] xPoints = {8, 12, 10};
            int[] yPoints = {16, 16, 20};
            g2.fillPolygon(xPoints, yPoints, 3);
        }
        
        private static void drawSendIcon(Graphics2D g2) {
            // Arrow
            g2.drawLine(4, 12, 18, 12);
            g2.drawLine(16, 8, 20, 12);
            g2.drawLine(16, 16, 20, 12);
            g2.drawLine(16, 8, 16, 16);
        }
        
        private static void drawUserIcon(Graphics2D g2) {
            // Head
            g2.drawOval(8, 4, 8, 8);
            // Body
            g2.drawArc(6, 12, 12, 10, 0, 180);
        }
    }
    
    /**
     * Class quản lý một tab chat riêng với một peer
     */
    private class ChatTab {
        private final long peerSsrc;
        private final JPanel panel;
        private final JTextArea chatArea;
        private final JTextField messageField;
        
        public ChatTab(long peerSsrc) {
            this.peerSsrc = peerSsrc;
            
            panel = new JPanel(new BorderLayout(8, 8));
            panel.setOpaque(false);
            panel.setBorder(new EmptyBorder(8, 8, 8, 8));
            
            // Chat area - Google Meet style với màu sắc cải thiện
            chatArea = new JTextArea();
            chatArea.setEditable(false);
            chatArea.setBackground(new Color(50, 52, 55)); // Sáng hơn và khác với public chat để dễ phân biệt
            chatArea.setForeground(new Color(255, 255, 255)); // Text trắng sáng
            chatArea.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            chatArea.setBorder(new EmptyBorder(16, 16, 16, 16));
            chatArea.setLineWrap(true);
            chatArea.setWrapStyleWord(true);
            chatArea.setCaretColor(new Color(255, 255, 255));
            
            // Custom scrollbar - Google Meet style
            JScrollPane chatScroll = new JScrollPane(chatArea) {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(50, 52, 55)); // Sáng hơn
                    g2.fillRect(0, 0, getWidth(), getHeight());
                    g2.dispose();
                }
            };
            chatScroll.setBorder(null);
            chatScroll.setBackground(new Color(50, 52, 55));
            chatScroll.getViewport().setBackground(new Color(50, 52, 55));
            
            // Custom scrollbar styling với màu khác để phân biệt
            chatScroll.getVerticalScrollBar().setUI(new javax.swing.plaf.basic.BasicScrollBarUI() {
                @Override
                protected void configureScrollBarColors() {
                    this.thumbColor = new Color(175, 82, 222, 200); // Màu tím để phân biệt với public chat
                    this.trackColor = new Color(50, 52, 55);
                }
                
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
            });
            
            panel.add(chatScroll, BorderLayout.CENTER);
            
            // Send panel - Google Meet style
            JPanel sendPanel = new JPanel(new BorderLayout(12, 0));
            sendPanel.setOpaque(false);
            sendPanel.setBorder(new EmptyBorder(12, 16, 12, 16));
            
            // Input field với rounded corners - Google Meet style
            messageField = new JTextField();
            messageField.setBackground(new Color(60, 64, 67)); // Google Meet input background
            messageField.setForeground(TEXT_PRIMARY);
            messageField.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
            messageField.setFont(new Font("Google Sans", Font.PLAIN, 14));
            messageField.setCaretColor(TEXT_PRIMARY);
            messageField.addActionListener(e -> sendMessage());
            
            // Emoji button cho private chat
            JButton btnEmojiPrivate = new JButton("😀") {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    
                    int w = getWidth();
                    int h = getHeight();
                    int arc = 24;
                    
                    // Background với hover effect
                    if (getModel().isRollover() || getModel().isPressed()) {
                        g2.setColor(new Color(60, 64, 67));
                        g2.fillRoundRect(0, 0, w, h, arc, arc);
                    }
                    
                    // Emoji text
                    g2.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 20));
                    FontMetrics fm = g2.getFontMetrics();
                    String emoji = "😀";
                    int textX = (w - fm.stringWidth(emoji)) / 2;
                    int textY = (h + fm.getAscent() - fm.getDescent()) / 2;
                    g2.setColor(new Color(255, 255, 255));
                    g2.drawString(emoji, textX, textY);
                    
                    g2.dispose();
                }
            };
            btnEmojiPrivate.setPreferredSize(new Dimension(40, 40));
            btnEmojiPrivate.setFocusPainted(false);
            btnEmojiPrivate.setBorderPainted(false);
            btnEmojiPrivate.setContentAreaFilled(false);
            btnEmojiPrivate.setOpaque(false);
            btnEmojiPrivate.setCursor(new Cursor(Cursor.HAND_CURSOR));
            btnEmojiPrivate.setToolTipText("Chọn emoji");
            btnEmojiPrivate.addActionListener(e -> showEmojiPicker(messageField, btnEmojiPrivate));
            
            // File button cho private chat
            JButton btnFilePrivate = new JButton() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    
                    int w = getWidth();
                    int h = getHeight();
                    int arc = 24;
                    
                    // Background với hover effect
                    if (getModel().isRollover() || getModel().isPressed()) {
                        g2.setColor(new Color(60, 64, 67));
                        g2.fillRoundRect(0, 0, w, h, arc, arc);
                    }
                    
                    // File icon (clip icon)
                    g2.setColor(new Color(255, 255, 255));
                    g2.setStroke(new BasicStroke(2));
                    int iconSize = 20;
                    int iconX = (w - iconSize) / 2;
                    int iconY = (h - iconSize) / 2;
                    
                    // Draw clip icon
                    int[] xPoints = {iconX + 4, iconX + iconSize - 4, iconX + iconSize - 2, iconX + 6};
                    int[] yPoints = {iconY + 2, iconY + 2, iconY + iconSize - 2, iconY + iconSize - 2};
                    g2.drawPolygon(xPoints, yPoints, 4);
                    g2.drawArc(iconX + 2, iconY, 6, 6, 0, 180);
                    
                    g2.dispose();
                }
            };
            btnFilePrivate.setPreferredSize(new Dimension(40, 40));
            btnFilePrivate.setFocusPainted(false);
            btnFilePrivate.setBorderPainted(false);
            btnFilePrivate.setContentAreaFilled(false);
            btnFilePrivate.setOpaque(false);
            btnFilePrivate.setCursor(new Cursor(Cursor.HAND_CURSOR));
            btnFilePrivate.setToolTipText("Gửi file");
            btnFilePrivate.addActionListener(e -> chooseAndSendFile(messageField, false));
            
            // Wrapper panel để tạo rounded border
            JPanel inputWrapper = new JPanel(new BorderLayout()) {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    
                    int arc = 24; // Rounded corners
                    Color borderColor = messageField.hasFocus() ? ACCENT_BLUE : new Color(95, 99, 104);
                    int borderWidth = messageField.hasFocus() ? 2 : 1;
                    
                    // Border
                    g2.setStroke(new BasicStroke(borderWidth));
                    g2.setColor(borderColor);
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
                    
                    g2.dispose();
                }
            };
            inputWrapper.setOpaque(false);
            inputWrapper.setBorder(new EmptyBorder(0, 0, 0, 0));
            inputWrapper.add(messageField, BorderLayout.CENTER);
            
            // Panel chứa file button, emoji button và input field cho private chat
            JPanel inputContainerPrivate = new JPanel(new BorderLayout(8, 0));
            inputContainerPrivate.setOpaque(false);
            JPanel buttonPanelPrivate = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            buttonPanelPrivate.setOpaque(false);
            buttonPanelPrivate.add(btnFilePrivate);
            buttonPanelPrivate.add(btnEmojiPrivate);
            inputContainerPrivate.add(buttonPanelPrivate, BorderLayout.WEST);
            inputContainerPrivate.add(inputWrapper, BorderLayout.CENTER);
            
            // Add focus effect
            messageField.addFocusListener(new java.awt.event.FocusAdapter() {
                @Override
                public void focusGained(java.awt.event.FocusEvent e) {
                    inputWrapper.repaint();
                }
                
                @Override
                public void focusLost(java.awt.event.FocusEvent e) {
                    inputWrapper.repaint();
                }
            });
            
            // Send button - Google Meet style
            JButton btnSend = new JButton() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    
                    int w = getWidth();
                    int h = getHeight();
                    int arc = 24;
                    
                    // Background với hover effect
                    Color bgColor = getModel().isRollover() ? ACCENT_BLUE_LIGHT : ACCENT_BLUE;
                    g2.setColor(bgColor);
                    g2.fillRoundRect(0, 0, w, h, arc, arc);
                    
                    // Icon
                    int iconSize = 20;
                    int iconX = (w - iconSize) / 2;
                    int iconY = (h - iconSize) / 2;
                    IconRenderer.drawIcon(g2, IconType.SEND, iconX, iconY, iconSize, iconSize, TEXT_PRIMARY);
                    
                    g2.dispose();
                }
            };
            btnSend.setPreferredSize(new Dimension(48, 48));
            btnSend.setFocusPainted(false);
            btnSend.setBorderPainted(false);
            btnSend.setContentAreaFilled(false);
            btnSend.setOpaque(false);
            btnSend.setCursor(new Cursor(Cursor.HAND_CURSOR));
            btnSend.addActionListener(e -> sendMessage());
            
            sendPanel.add(inputContainerPrivate, BorderLayout.CENTER);
            sendPanel.add(btnSend, BorderLayout.EAST);
            panel.add(sendPanel, BorderLayout.SOUTH);
        }
        
        private void sendMessage() {
            String msg = messageField.getText().trim();
            if (!msg.isEmpty()) {
                messageField.setText("");
                sendPrivateChat(peerSsrc, msg);
                addMessage("Bạn", msg, true);
            }
        }
        
        public void addMessage(String fromName, String message, boolean isSent) {
            SwingUtilities.invokeLater(() -> {
                String timestamp = java.time.LocalTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("HH:mm")
                );
                // Google Meet style message format với màu sắc cải thiện
                String displayName = isSent ? "Bạn" : (fromName != null ? fromName : "Người dùng");
                
                // Kiểm tra xem có phải file không
                boolean isFile = message != null && message.startsWith("[FILE:") && message.endsWith("]");
                
                if (isFile) {
                    // Parse file message
                    try {
                        String fileContent = message.substring(6, message.length() - 1);
                        int lastColon = fileContent.lastIndexOf(':');
                        if (lastColon > 0) {
                            String base64Data = fileContent.substring(0, lastColon);
                            String fileName = fileContent.substring(lastColon + 1);
                            
                            // Lưu file data
                            saveFileData(base64Data, fileName);
                            
                            // Hiển thị file với format đẹp
                            String fileMessage = String.format("[%s] %s: 📎 %s [Click để tải về - ID: %s]\n", 
                                timestamp, displayName, fileName, fileName.hashCode());
                            chatArea.append(fileMessage);
                            
                            // Lưu file ID để download
                            String fileId = "file_" + System.currentTimeMillis() + "_" + fileName.hashCode();
                            fileDataMap.put(fileId, base64Data);
                            fileDataMap.put(fileId + "_name", fileName);
                            
                            // Thêm click listener để download (sử dụng MouseListener trên chatArea)
                            chatArea.addMouseListener(new MouseAdapter() {
                                @Override
                                public void mouseClicked(MouseEvent e) {
                                    int pos = chatArea.viewToModel(e.getPoint());
                                    if (pos >= 0) {
                                        try {
                                            String text = chatArea.getText();
                                            int lineStart = text.lastIndexOf('\n', pos - 1) + 1;
                                            int lineEnd = text.indexOf('\n', pos);
                                            if (lineEnd < 0) lineEnd = text.length();
                                            String line = text.substring(lineStart, lineEnd);
                                            if (line.contains("ID: " + fileName.hashCode())) {
                                                downloadFile(base64Data, fileName);
                                            }
                                        } catch (Exception ex) {
                                            // Ignore
                                        }
                                    }
                                }
                            });
                        } else {
                            String plainMessage = String.format("[%s] %s: %s\n", timestamp, displayName, message);
                            chatArea.append(plainMessage);
                        }
                    } catch (Exception e) {
                        String plainMessage = String.format("[%s] %s: %s\n", timestamp, displayName, message);
                        chatArea.append(plainMessage);
                    }
                } else {
                    // Vì JTextArea không support HTML, ta dùng plain text với format đẹp hơn
                    String plainMessage = String.format("[%s] %s: %s\n", timestamp, displayName, message);
                    chatArea.append(plainMessage);
                }
                chatArea.setCaretPosition(chatArea.getDocument().getLength());
            });
        }
    }
}
