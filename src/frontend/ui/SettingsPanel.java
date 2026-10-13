package frontend.ui;

import frontend.GameSettings;
import frontend.Resolution;
import frontend.SoundManager;
import frontend.style.Style;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JToggleButton;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;

/** 설정 조작과 저장 결과를 한 화면에서 확인하는 패널. */
public final class SettingsPanel extends JPanel {
    private final JLabel status = text("변경 사항은 이 기기에 자동 저장됩니다.", false);
    private final SettingsColumns sections = new SettingsColumns();

    public SettingsPanel() {
        super(new BorderLayout(0, 12));
        setOpaque(false);
        JScrollPane scroll = new JScrollPane(sections);
        Style.applyScrollStyle(scroll);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        add(scroll, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout(12, 0));
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Style.BORDER));
        status.setPreferredSize(new Dimension(0, 40));
        footer.add(status, BorderLayout.CENTER);
        JButton reset = new JButton("기본값 복원");
        Style.applyButtonStyle(reset, Style.ACCENT, Style.PANEL);
        reset.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));
        reset.addActionListener(event -> {
            GameSettings.resetDefaults();
            SoundManager.refreshSettings();
            report("화면·사운드·키 설정을 기본값으로 되돌렸습니다.");
            rebuild();
            reset.requestFocusInWindow();
        });
        footer.add(reset, BorderLayout.EAST);
        add(footer, BorderLayout.SOUTH);
        rebuild();
    }

    private void rebuild() {
        sections.removeAll();
        JPanel general = new JPanel();
        general.setLayout(new BoxLayout(general, BoxLayout.Y_AXIS));
        general.setOpaque(false);
        general.getAccessibleContext().setAccessibleName("화면과 사운드 설정");
        JPanel sizes = new JPanel(new GridLayout(1, 3, 6, 0));
        sizes.setOpaque(false);
        ButtonGroup group = new ButtonGroup();
        for (Resolution resolution : Resolution.values()) {
            JToggleButton option = new JToggleButton(resolution.toString());
            Style.applyButtonStyle(option, Style.ACCENT, Style.PANEL);
            option.setBorder(BorderFactory.createEmptyBorder(7, 8, 7, 8));
            option.setSelected(GameSettings.getResolution() == resolution);
            option.getAccessibleContext().setAccessibleDescription("다음 게임의 화면 크기");
            revealOnFocus(option);
            option.addActionListener(event -> {
                GameSettings.setResolution(resolution);
                report("화면 크기: " + GameSettings.getResolution() + " · 다음 게임부터 적용됩니다.");
                rebuild();
                restoreFocus(GameSettings.getResolution().toString());
            });
            group.add(option);
            sizes.add(option);
        }
        stack(general, section("화면 크기", "다음 게임부터 적용됩니다.", sizes));
        general.add(Box.createVerticalStrut(12));

        JPanel sound = new JPanel(new BorderLayout(0, 10));
        sound.setOpaque(false);
        JToggleButton mute = new JToggleButton(GameSettings.isMuted() ? "음소거 켜짐" : "음소거 꺼짐");
        Style.applyButtonStyle(mute, Style.ACCENT, Style.PANEL);
        mute.setFont(Style.SMALL_FONT);
        mute.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        mute.setSelected(GameSettings.isMuted());
        mute.getAccessibleContext().setAccessibleName("전체 음소거");
        revealOnFocus(mute);
        mute.addActionListener(event -> {
            SoundManager.setMuted(mute.isSelected());
            mute.setSelected(GameSettings.isMuted());
            mute.setText(GameSettings.isMuted() ? "음소거 켜짐" : "음소거 꺼짐");
            report("소리 설정을 저장했습니다.");
        });
        JPanel soundHeading = new JPanel(new BorderLayout());
        soundHeading.setOpaque(false);
        soundHeading.add(text("사운드", true), BorderLayout.WEST);
        soundHeading.add(mute, BorderLayout.EAST);
        sound.add(soundHeading, BorderLayout.NORTH);
        JPanel volumes = new JPanel(new GridLayout(2, 1, 0, 6));
        volumes.setOpaque(false);
        volumes.add(volume("배경음악", GameSettings::getMusicVolume, GameSettings::setMusicVolume));
        volumes.add(volume("효과음", GameSettings::getEffectsVolume, GameSettings::setEffectsVolume));
        sound.add(volumes, BorderLayout.CENTER);
        JPanel soundCard = card();
        soundCard.add(sound, BorderLayout.CENTER);
        stack(general, soundCard);
        general.add(Box.createVerticalStrut(10));
        JLabel scope = text("<html>1PC 2인은 고정 조작키를 사용합니다.<br>온라인 대전에서는 일시정지할 수 없습니다.</html>", false);
        scope.setAlignmentX(LEFT_ALIGNMENT);
        general.add(scope);

        JPanel keys = new JPanel(new GridLayout(0, 1, 0, 4));
        keys.setOpaque(false);
        addKey(keys, "왼쪽 이동", GameSettings::getLeftKey, GameSettings::setLeftKey);
        addKey(keys, "오른쪽 이동", GameSettings::getRightKey, GameSettings::setRightKey);
        addKey(keys, "왼쪽 회전", GameSettings::getRotateLeftKey, GameSettings::setRotateLeftKey);
        addKey(keys, "오른쪽 회전", GameSettings::getRotateRightKey, GameSettings::setRotateRightKey);
        addKey(keys, "한 칸 하강", GameSettings::getSoftDropKey, GameSettings::setSoftDropKey);
        addKey(keys, "즉시 낙하", GameSettings::getHardDropKey, GameSettings::setHardDropKey);
        addKey(keys, "일시정지", GameSettings::getPauseKey, GameSettings::setPauseKey);
        JPanel keyboard = section("조작키", "키 선택 → 새 키 입력 · Esc로 취소", keys);
        keyboard.getAccessibleContext().setAccessibleName("조작키 설정");
        sections.add(general);
        sections.add(keyboard);
        if (GameSettings.getSaveError() != null) report("");
        sections.revalidate();
        sections.repaint();
    }

    private JPanel volume(String name, IntSupplier getter, IntConsumer setter) {
        JPanel row = new JPanel(new BorderLayout(0, 2));
        row.setOpaque(false);
        JLabel label = text(name, false);
        JSlider slider = new JSlider(0, 100, getter.getAsInt());
        slider.setOpaque(false);
        slider.setForeground(Style.ACCENT);
        slider.getAccessibleContext().setAccessibleName(name + " 음량");
        revealOnFocus(slider, row);
        slider.setMajorTickSpacing(25);
        slider.setPaintTicks(true);
        slider.setPreferredSize(new Dimension(100, 26));
        label.setLabelFor(slider);
        JLabel value = text(getter.getAsInt() + "%", false);
        value.setHorizontalAlignment(SwingConstants.RIGHT);
        boolean[] restoring = { false };
        slider.addChangeListener(event -> {
            if (restoring[0]) return;
            value.setText(slider.getValue() + "%");
            if (!slider.getValueIsAdjusting()) {
                setter.accept(slider.getValue());
                SoundManager.refreshSettings();
                int saved = getter.getAsInt();
                if (slider.getValue() != saved) {
                    // 저장 실패 시 조절 중인 슬라이더와 포커스를 유지하며 이전 값만 복원합니다.
                    restoring[0] = true;
                    try { slider.setValue(saved); } finally { restoring[0] = false; }
                    value.setText(saved + "%");
                }
                report(name + " " + saved + "% · 저장했습니다.");
            }
        });
        JPanel labels = new JPanel(new BorderLayout());
        labels.setOpaque(false);
        labels.add(label, BorderLayout.WEST);
        labels.add(value, BorderLayout.EAST);
        row.add(labels, BorderLayout.NORTH);
        row.add(slider, BorderLayout.CENTER);
        return row;
    }

    private void addKey(JPanel keys, String name, IntSupplier getter, IntConsumer setter) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        JLabel label = text(name, false);
        JButton button = new JButton(GameSettings.keyName(getter.getAsInt()));
        Style.applyButtonStyle(button, Style.ACCENT, Style.PANEL);
        button.setPreferredSize(new Dimension(132, 30));
        button.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        button.getAccessibleContext().setAccessibleName(name + " 키");
        button.getAccessibleContext().setAccessibleDescription("현재 키: " + button.getText() + ". 변경하려면 누르세요.");
        revealOnFocus(button, row);
        label.setLabelFor(button);
        boolean[] capturing = { false };
        Runnable cancel = () -> {
            capturing[0] = false;
            button.setFocusTraversalKeysEnabled(true);
            button.setText(GameSettings.keyName(getter.getAsInt()));
            button.getAccessibleContext().setAccessibleDescription("현재 키: " + button.getText() + ". 변경하려면 누르세요.");
        };
        button.addActionListener(event -> {
            capturing[0] = true;
            button.setFocusTraversalKeysEnabled(false);
            button.setText("새 키 입력…");
            button.getAccessibleContext().setAccessibleDescription("새 키를 입력하세요. Esc로 취소합니다.");
            button.requestFocusInWindow();
            status.setForeground(Style.ACCENT);
            status.setText(name + "에 사용할 키를 누르세요. Esc로 취소합니다.");
        });
        button.addKeyListener(new KeyAdapter() {
            private boolean consumeRelease;
            @Override public void keyPressed(KeyEvent event) {
                if (!capturing[0]) return;
                event.consume();
                consumeRelease = true;
                if (event.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    cancel.run();
                    report("키 변경을 취소했습니다.");
                    return;
                }
                try {
                    if ((event.getModifiersEx() & (KeyEvent.SHIFT_DOWN_MASK | KeyEvent.CTRL_DOWN_MASK
                            | KeyEvent.ALT_DOWN_MASK | KeyEvent.META_DOWN_MASK | KeyEvent.ALT_GRAPH_DOWN_MASK)) != 0)
                        throw new IllegalArgumentException("조합키 대신 하나의 키만 눌러 주세요.");
                    setter.accept(event.getKeyCode());
                    cancel.run();
                    report(name + ": " + GameSettings.keyName(getter.getAsInt()) + " · 저장했습니다.");
                } catch (IllegalArgumentException error) {
                    status.setText(error.getMessage());
                    status.setForeground(Style.PINK);
                }
            }
            @Override public void keyReleased(KeyEvent event) {
                if (consumeRelease) { event.consume(); consumeRelease = false; }
            }
        });
        button.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent event) {
                if (capturing[0]) { cancel.run(); report("키 변경을 취소했습니다."); }
            }
        });
        row.add(label, BorderLayout.WEST);
        row.add(button, BorderLayout.EAST);
        keys.add(row);
    }

    private JPanel section(String title, String description, JPanel content) {
        JPanel section = card();
        JPanel heading = new JPanel(new BorderLayout(0, 3));
        heading.setOpaque(false);
        heading.add(text(title, true), BorderLayout.NORTH);
        heading.add(text(description, false), BorderLayout.SOUTH);
        section.add(heading, BorderLayout.NORTH);
        section.add(content, BorderLayout.CENTER);
        return section;
    }

    private JPanel card() {
        JPanel card = Style.framedPanel(new BorderLayout(0, 10), Style.PANEL);
        card.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        return card;
    }

    private void stack(JPanel container, JPanel section) {
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, section.getPreferredSize().height));
        section.setAlignmentX(LEFT_ALIGNMENT);
        container.add(section);
    }

    private void report(String message) {
        String error = GameSettings.getSaveError();
        status.setText(error == null ? message : error);
        status.setToolTipText(status.getText());
        status.setForeground(error == null ? Style.GREEN : Style.PINK);
    }

    private static void revealOnFocus(javax.swing.JComponent component) {
        revealOnFocus(component, component);
    }

    private static void revealOnFocus(javax.swing.JComponent component, javax.swing.JComponent region) {
        component.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent event) {
                region.scrollRectToVisible(new Rectangle(0, 0, region.getWidth(), region.getHeight()));
            }
        });
    }

    private void restoreFocus(String name) {
        javax.swing.SwingUtilities.invokeLater(() -> focusNamed(sections, name));
    }

    private boolean focusNamed(java.awt.Container container, String name) {
        for (java.awt.Component component : container.getComponents()) {
            if (component instanceof javax.swing.AbstractButton
                    && name.equals(((javax.swing.AbstractButton) component).getText())) {
                component.requestFocusInWindow();
                return true;
            }
            if (component instanceof java.awt.Container && focusNamed((java.awt.Container) component, name)) return true;
        }
        return false;
    }

    private static JLabel text(String value, boolean emphasis) {
        JLabel label = new JLabel(value);
        label.setFont(emphasis ? Style.BUTTON_FONT : Style.SMALL_FONT);
        label.setForeground(emphasis ? Style.TEXT : Style.MUTED_TEXT);
        return label;
    }

    /** 기본 창에서는 두 열, 좁은 화면에서는 한 열로 배치하고 필요한 때만 스크롤합니다. */
    private static final class SettingsColumns extends JPanel implements Scrollable {
        private static final int GAP = 14;
        SettingsColumns() { super(null); setOpaque(false); }
        private boolean twoColumns() { return availableWidth() >= 660; }
        private int availableWidth() { return getParent() == null ? 800 : getParent().getWidth(); }
        @Override public Dimension getPreferredSize() {
            if (getComponentCount() < 2) return new Dimension(720, 320);
            int left = getComponent(0).getPreferredSize().height;
            int right = getComponent(1).getPreferredSize().height;
            return new Dimension(720, twoColumns() ? Math.max(left, right) : left + GAP + right);
        }
        @Override public void doLayout() {
            if (getComponentCount() < 2) return;
            java.awt.Component left = getComponent(0), right = getComponent(1);
            if (twoColumns()) {
                int width = (int) ((getWidth() - GAP) * .43);
                left.setBounds(0, 0, width, left.getPreferredSize().height);
                right.setBounds(width + GAP, 0, getWidth() - width - GAP, right.getPreferredSize().height);
            } else {
                int height = left.getPreferredSize().height;
                left.setBounds(0, 0, getWidth(), height);
                right.setBounds(0, height + GAP, getWidth(), right.getPreferredSize().height);
            }
        }
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() {
            return getParent() != null && getParent().getHeight() >= getPreferredSize().height;
        }
        @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return 24; }
        @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) { return Math.max(24, visible.height - 24); }
    }
}
