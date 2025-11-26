package com.example.rtpav.client;

import com.example.rtpav.client.media.VideoRenderer;
import com.example.rtpav.rmi.ConferenceService;
import com.example.rtpav.rmi.PeerInfo;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.net.InetSocketAddress;
import java.util.Set;
import java.util.Vector;

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
    private final JTextArea chatArea = new JTextArea(6, 20);
    private JLabel selfLabel;
    private JPanel selfContainer;
    private final JTextField chatBox = new JTextField();
    private final JList<String> peerList = new JList<>();
    private final VideoRenderer selfView = new VideoRenderer();
    private final VideoRenderer remoteView = new VideoRenderer();
    // Map để lưu video từ nhiều peers: SSRC -> VideoRenderer
    private final java.util.Map<Long, VideoRenderer> peerVideoViews = new java.util.concurrent.ConcurrentHashMap<>();
    // Map để lưu peer panels: SSRC -> JPanel
    private final java.util.Map<Long, JPanel> peerPanels = new java.util.concurrent.ConcurrentHashMap<>();

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

    private Hooks hooks;

    public ClientUI(String name, String roomId, long selfSsrc, ConferenceService svc, InetSocketAddress myRtp) {
        super("Video Conference - " + name + " @ " + roomId);
        this.name = name; 
        this.roomId = roomId; 
        this.selfSsrc = selfSsrc; 
        this.svc = svc; 
        this.myRtp = myRtp;
        
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

    private void build() {
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        getContentPane().setBackground(BG_PRIMARY);
        setLayout(new BorderLayout(12, 12));
        ((JComponent)getContentPane()).setBorder(new EmptyBorder(12, 12, 12, 12));

        // Top control bar with modern styling
        JPanel topBar = createTopBar();
        add(topBar, BorderLayout.NORTH);

        // Center split: left = video; right = chat (hoàn toàn độc lập)
        JSplitPane center = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        center.setResizeWeight(0.75);
        center.setDividerSize(4);
        center.setBackground(BG_PRIMARY);
        center.setBorder(null);
        center.setDividerLocation(960); // Set divider position để đảm bảo không che nhau
        add(center, BorderLayout.CENTER);

        // Video panel với grid view
        JPanel videoPanel = createVideoPanel();
        videoPanel.setPreferredSize(new Dimension(960, 540));
        center.setLeftComponent(videoPanel);

        // Chat panel bên phải - hoàn toàn độc lập
        JPanel chatPanel = createChatPanel();
        chatPanel.setPreferredSize(new Dimension(320, 540));
        center.setRightComponent(chatPanel);

        // Window listener
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override 
            public void windowClosed(java.awt.event.WindowEvent e) {
                if (hooks != null) hooks.onClose();
            }
        });

        setSize(1200, 800);
        setLocationRelativeTo(null);
        setMinimumSize(new Dimension(900, 600));
    }

    private JPanel createTopBar() {
        JPanel topBar = new JPanel(new BorderLayout(12, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                // Gradient background
                GradientPaint gradient = new GradientPaint(
                    0, 0, new Color(30, 30, 30),
                    getWidth(), 0, new Color(25, 25, 30)
                );
                g2.setPaint(gradient);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                
                // Subtle border glow
                g2.setStroke(new BasicStroke(1.5f));
                g2.setColor(new Color(ACCENT_BLUE.getRed(), ACCENT_BLUE.getGreen(), ACCENT_BLUE.getBlue(), 30));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
                
                g2.dispose();
            }
        };
        topBar.setOpaque(false);
        topBar.setBorder(new EmptyBorder(14, 20, 14, 20));

        // Left: Title with icon
        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        titlePanel.setOpaque(false);
        
        JLabel iconLabel = new JLabel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                IconRenderer.drawIcon(g, IconType.VIDEO, getWidth(), getHeight(), ACCENT_BLUE);
            }
        };
        iconLabel.setPreferredSize(new Dimension(24, 24));
        
        JLabel titleLabel = new JLabel(name + " @ " + roomId);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 17));
        titleLabel.setForeground(TEXT_PRIMARY);
        
        titlePanel.add(iconLabel);
        titlePanel.add(titleLabel);
        topBar.add(titlePanel, BorderLayout.WEST);

        // Right: Control buttons
        JPanel controlsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        controlsPanel.setOpaque(false);
        controlsPanel.add(btnCam);
        controlsPanel.add(btnMic);
        topBar.add(controlsPanel, BorderLayout.EAST);

        // Camera button action
        btnCam.addActionListener(e -> {
            camOn = !camOn;
            btnCam.setActive(camOn);
            btnCam.setToolTipText(camOn ? "Click to turn OFF camera" : "Click to turn ON camera");
            
            // Cập nhật label và border
            if (selfLabel != null) {
                selfLabel.setText("  You");
                selfLabel.setForeground(camOn ? ACCENT_GREEN : TEXT_SECONDARY);
            }
            if (selfContainer != null) {
                selfContainer.setBorder(new CompoundBorder(
                    new LineBorder(camOn ? ACCENT_GREEN : BG_TERTIARY, 2, true),
                    new EmptyBorder(4, 4, 4, 4)
                ));
                selfContainer.repaint();
            }
            
            // Nếu tắt camera, hiển thị avatar ngay
            if (!camOn) {
                showSelfAvatar();
            }
            
            if (hooks != null) hooks.onToggleCamera(camOn);
        });
        btnCam.setToolTipText("Click to turn ON camera");

        // Mic button action
        btnMic.addActionListener(e -> {
            micOn = !micOn;
            btnMic.setActive(micOn);
            btnMic.setToolTipText(micOn ? "Click to turn OFF microphone" : "Click to turn ON microphone");
            if (hooks != null) hooks.onToggleMic(micOn);
        });
        btnMic.setToolTipText("Click to turn ON microphone");

        return topBar;
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

        // Main grid view để hiển thị tất cả peers - chiếm toàn bộ không gian
        JPanel mainGridPanel = new JPanel(new java.awt.GridLayout(0, 2, 8, 8)); // 2 cột, tự động số hàng
        mainGridPanel.setBackground(BG_PRIMARY);
        mainGridPanel.setBorder(new EmptyBorder(0, 0, 0, 0));
        
        // Store reference để có thể cập nhật
        this.mainGridPanel = mainGridPanel;
        
        // Container cho grid với padding để tránh self view
        JPanel gridContainer = new JPanel(new BorderLayout());
        gridContainer.setBackground(BG_PRIMARY);
        gridContainer.setBorder(new EmptyBorder(0, 0, 0, 0));
        gridContainer.add(mainGridPanel, BorderLayout.CENTER);
        
        // Self view container - đặt ở góc trên bên trái, độc lập với grid
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
        selfView.setPreferredSize(new Dimension(240, 135));
        selfView.setBackground(BG_TERTIARY);
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
        selfContainer.setPreferredSize(new Dimension(240, 160));
        
        // Đặt self view ở góc trên bên trái, không che grid
        // Dùng JLayeredPane để overlay self view lên grid nhưng không che các peer cells
        JLayeredPane layeredPane = new JLayeredPane();
        layeredPane.setPreferredSize(new Dimension(960, 540));
        layeredPane.setBackground(BG_PRIMARY);
        
        // Grid container chiếm toàn bộ không gian
        gridContainer.setBounds(0, 0, 960, 540);
        layeredPane.add(gridContainer, JLayeredPane.DEFAULT_LAYER);
        
        // Self view ở góc trên bên trái, overlay nhưng không che grid cells
        selfContainer.setBounds(8, 8, 240, 160);
        selfContainer.setOpaque(true);
        layeredPane.add(selfContainer, JLayeredPane.PALETTE_LAYER);
        
        videoPanel.add(layeredPane, BorderLayout.CENTER);

        return videoPanel;
    }
    
    private JPanel mainGridPanel; // Panel chứa grid của tất cả peers

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
        chatPanel.setPreferredSize(new Dimension(320, 0));

        // Header with icon
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        headerPanel.setOpaque(false);
        
        JLabel iconLabel = new JLabel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                IconRenderer.drawIcon(g, IconType.CHAT, getWidth(), getHeight(), ACCENT_PURPLE);
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

        // Chat area (chiếm phần lớn không gian)
        chatArea.setEditable(false);
        chatArea.setBackground(BG_TERTIARY);
        chatArea.setForeground(TEXT_PRIMARY);
        chatArea.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        chatArea.setBorder(new EmptyBorder(8, 12, 8, 12));
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        
        JScrollPane chatScroll = new JScrollPane(chatArea);
        chatScroll.setBorder(null);
        chatScroll.setBackground(BG_TERTIARY);
        chatScroll.getViewport().setBackground(BG_TERTIARY);
        chatPanel.add(chatScroll, BorderLayout.CENTER);

        // Send panel ở dưới
        JPanel sendPanel = new JPanel(new BorderLayout(8, 0));
        sendPanel.setOpaque(false);
        
        chatBox.setBackground(BG_TERTIARY);
        chatBox.setForeground(TEXT_PRIMARY);
        chatBox.setBorder(new CompoundBorder(
            new LineBorder(new Color(BG_TERTIARY.getRed() + 20, BG_TERTIARY.getGreen() + 20, BG_TERTIARY.getBlue() + 20), 1, true),
            new EmptyBorder(10, 14, 10, 14)
        ));
        chatBox.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        chatBox.addActionListener(e -> sendChat());
        
        // Add focus effect
        chatBox.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                chatBox.setBorder(new CompoundBorder(
                    new LineBorder(ACCENT_BLUE, 2, true),
                    new EmptyBorder(9, 13, 9, 13)
                ));
            }
            
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                chatBox.setBorder(new CompoundBorder(
                    new LineBorder(new Color(BG_TERTIARY.getRed() + 20, BG_TERTIARY.getGreen() + 20, BG_TERTIARY.getBlue() + 20), 1, true),
                    new EmptyBorder(10, 14, 10, 14)
                ));
            }
        });
        
        sendPanel.add(chatBox, BorderLayout.CENTER);

        ModernButton btnSend = new ModernButton(IconType.SEND, "Send", true);
        btnSend.setPreferredSize(new Dimension(90, 38));
        btnSend.addActionListener(e -> sendChat());
        sendPanel.add(btnSend, BorderLayout.EAST);

        chatPanel.add(sendPanel, BorderLayout.SOUTH);

        return chatPanel;
    }

    private void sendChat() {
        String msg = chatBox.getText().trim();
        if (!msg.isEmpty()) {
            chatBox.setText("");
            if (hooks != null) hooks.onSendChat(msg);
        }
    }

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
                // Xóa tất cả components cũ
                mainGridPanel.removeAll();
                
                // Tạo cell cho mỗi peer (trừ self)
                for (PeerInfo peer : peers) {
                    if (peer.getSsrc() == selfSsrc) continue; // Bỏ qua self
                    
                    // Tạo hoặc lấy VideoRenderer cho peer này
                    VideoRenderer vr = peerVideoViews.computeIfAbsent(peer.getSsrc(), k -> {
                        VideoRenderer renderer = new VideoRenderer();
                        // Mặc định hiển thị avatar
                        renderer.showAvatar();
                        return renderer;
                    });
                    
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
                    
                    // Peer label with icon
                    JPanel peerLabelPanel = new JPanel(new BorderLayout(6, 0));
                    peerLabelPanel.setOpaque(false);
                    peerLabelPanel.setBorder(new EmptyBorder(4, 8, 4, 8));
                    
                    JLabel iconLabel = new JLabel() {
                        @Override
                        protected void paintComponent(Graphics g) {
                            super.paintComponent(g);
                            IconRenderer.drawIcon(g, IconType.USER, getWidth(), getHeight(), ACCENT_BLUE);
                        }
                    };
                    iconLabel.setPreferredSize(new Dimension(16, 16));
                    
                    JLabel peerLabel = new JLabel("Peer " + peer.getSsrc());
                    peerLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
                    peerLabel.setForeground(TEXT_PRIMARY);
                    
                    peerLabelPanel.add(iconLabel, BorderLayout.WEST);
                    peerLabelPanel.add(peerLabel, BorderLayout.CENTER);
                    peerPanel.add(peerLabelPanel, BorderLayout.NORTH);
                    peerPanel.add(vr, BorderLayout.CENTER);
                    
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
            String timestamp = java.time.LocalTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("HH:mm")
            );
            chatArea.append("[" + timestamp + "] " + from + ": " + msg + "\n");
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
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
}
