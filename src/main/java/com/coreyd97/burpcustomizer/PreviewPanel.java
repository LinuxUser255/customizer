package com.coreyd97.burpcustomizer;

import com.coreyd97.BurpExtenderUtilities.Alignment;
import com.coreyd97.BurpExtenderUtilities.PanelBuilder;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;

/**
 * Shows the colours of the theme which is selected (but not yet applied), above a set of components
 * drawn with the theme that is currently applied.
 *
 * Swing cannot render components with a look and feel that isn't installed, and temporarily installing
 * one deadlocks with Burp's lazily loaded defaults. So the selected theme is drawn by hand from its colours.
 */
public class PreviewPanel extends JPanel {

    private final ThemeSample sample = new ThemeSample();
    private final JLabel noSelected = new JLabel("No theme selected.");

    PreviewPanel() {
        this.setLayout(new BorderLayout());
        this.setBorder(BorderFactory.createLineBorder(Color.BLACK, 1));
        noSelected.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        noSelected.setHorizontalAlignment(JLabel.CENTER);
        this.add(noSelected, BorderLayout.NORTH);
        try {
            this.add(buildPreviewContent(), BorderLayout.CENTER);
        } catch (Exception ignored) { //The component samples are only decorative.
        }
    }

    public void reset() {
        swapHeader(noSelected);
    }

    public void setPreviewTheme(LookAndFeel lookAndFeel) {
        sample.setTheme(lookAndFeel.getName(), lookAndFeel.getDefaults());
        swapHeader(sample);
    }

    private void swapHeader(JComponent header) {
        this.remove(sample);
        this.remove(noSelected);
        this.add(header, BorderLayout.NORTH);
        this.revalidate();
        this.repaint();
    }

    /** A hand drawn sample of a theme's main colours. */
    private static class ThemeSample extends JComponent {
        private String name = "";
        private Color background, foreground, disabled, selectionBackground, selectionForeground,
                buttonBackground, buttonForeground, accent, fieldBackground, border;

        void setTheme(String name, UIDefaults defaults) {
            this.name = name;
            background = color(defaults, Color.WHITE, "Panel.background", "control");
            foreground = color(defaults, Color.BLACK, "Label.foreground", "controlText");
            disabled = color(defaults, Color.GRAY, "Label.disabledForeground");
            selectionBackground = color(defaults, foreground, "List.selectionBackground", "textHighlight");
            selectionForeground = color(defaults, background, "List.selectionForeground", "textHighlightText");
            buttonBackground = color(defaults, background, "Button.background");
            buttonForeground = color(defaults, foreground, "Button.foreground");
            accent = color(defaults, selectionBackground, "Component.accentColor", "TabbedPane.underlineColor", "Component.focusColor");
            fieldBackground = color(defaults, background, "TextField.background", "TextArea.background");
            border = color(defaults, disabled, "Component.borderColor", "Separator.foreground");
            repaint();
        }

        private static Color color(UIDefaults defaults, Color fallback, String... keys) {
            for (String key : keys) {
                try {
                    Color color = defaults.getColor(key);
                    if (color != null) return color;
                } catch (RuntimeException ignored) {
                }
            }
            return fallback;
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics metrics = getFontMetrics(getFont());
            return new Dimension(metrics.stringWidth("Text field") * 6, metrics.getHeight() * 4 + 40);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            if (background == null) return;
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                FontMetrics metrics = g.getFontMetrics();
                int pad = 10, line = metrics.getHeight(), box = line + 10;

                g.setColor(background);
                g.fillRect(0, 0, getWidth(), getHeight());

                //Title, with an accent underline like the selected tab.
                int y = pad;
                g.setFont(getFont().deriveFont(Font.BOLD));
                g.setColor(foreground);
                g.drawString(name, pad, y + metrics.getAscent());
                g.setColor(accent);
                g.fillRect(pad, y + line + 2, g.getFontMetrics().stringWidth(name), 3);
                g.setFont(getFont());

                //A row of samples.
                y += line + 14;
                int x = pad;
                x = drawBox(g, metrics, "Button", x, y, box, buttonBackground, buttonForeground, border);
                x = drawBox(g, metrics, "Accent", x, y, box, accent, contrasting(accent), accent);
                x = drawBox(g, metrics, "Text field", x, y, box, fieldBackground, foreground, border);
                x = drawBox(g, metrics, "Selected", x, y, box, selectionBackground, selectionForeground, selectionBackground);
                g.setColor(foreground);
                g.drawString("Text", x, y + (box + metrics.getAscent() - metrics.getDescent()) / 2);
                x += metrics.stringWidth("Text") + pad;
                g.setColor(disabled);
                g.drawString("Disabled", x, y + (box + metrics.getAscent() - metrics.getDescent()) / 2);
            } finally {
                g.dispose();
            }
        }

        private int drawBox(Graphics2D g, FontMetrics metrics, String text, int x, int y, int height,
                            Color fill, Color textColor, Color outline) {
            int width = metrics.stringWidth(text) + 24;
            g.setColor(fill);
            g.fillRoundRect(x, y, width, height, 8, 8);
            g.setColor(outline);
            g.drawRoundRect(x, y, width, height, 8, 8);
            g.setColor(textColor);
            g.drawString(text, x + 12, y + (height + metrics.getAscent() - metrics.getDescent()) / 2);
            return x + width + 10;
        }

        private static Color contrasting(Color color) {
            double luminance = (0.299 * color.getRed() + 0.587 * color.getGreen() + 0.114 * color.getBlue()) / 255;
            return luminance > 0.6 ? Color.BLACK : Color.WHITE;
        }
    }

    public JComponent buildPreviewContent() {
        JLabel jLabelEnabled = new JLabel("Enabled");
        JLabel jLabelDisabled = new JLabel("Disabled");
        jLabelDisabled.setEnabled(false);

        JButton jbuttonEnabled = new JButton("Enabled");
        JButton jbuttonDisabled = new JButton("Disabled");
        jbuttonDisabled.setEnabled(false);

        JCheckBox JCheckBoxEnabled = new JCheckBox("Enabled");
        JCheckBox JCheckBoxDisabled = new JCheckBox("Disabled");
        JCheckBox JCheckBoxEnabledSelected = new JCheckBox("Selected");
        JCheckBox JCheckBoxDisabledSelected = new JCheckBox("Selected disabled");
        JCheckBoxDisabled.setEnabled(false);
        JCheckBoxDisabledSelected.setEnabled(false);
        JCheckBoxEnabledSelected.setSelected(true);
        JCheckBoxDisabledSelected.setSelected(true);

        JRadioButton JRadioButtonEnabled = new JRadioButton("Enabled");
        JRadioButton JRadioButtonDisabled = new JRadioButton("Disabled");
        JRadioButton JRadioButtonEnabledSelected = new JRadioButton("Selected");
        JRadioButton JRadioButtonDisabledSelected = new JRadioButton("Selected disabled");
        JRadioButtonDisabled.setEnabled(false);
        JRadioButtonDisabledSelected.setEnabled(false);
        JRadioButtonEnabledSelected.setSelected(true);
        JRadioButtonDisabledSelected.setSelected(true);

        JComboBox<String> JComboBoxEnabled = new JComboBox<>(new String[]{"Editable", "Item A", "Item B"});
        JComboBox<String> JComboBoxDisabled = new JComboBox<>(new String[]{"Disabled"});
        JComboBox<String> JComboBoxEnabledNotEditable = new JComboBox<>(new String[]{"Not Editable", "Item A", "Item B"});
        JComboBox<String> JComboBoxDisabledNotEditable = new JComboBox<>(new String[]{"Not Editable Disabled"});
        JComboBoxDisabled.setEnabled(false);
        JComboBoxDisabledNotEditable.setEnabled(false);
        JComboBoxDisabledNotEditable.setEditable(false);
        JComboBoxEnabledNotEditable.setEditable(false);

        JSpinner JSpinnerEnabled = new JSpinner();
        JSpinner JSpinnerDisabled = new JSpinner();
        JSpinnerDisabled.setEnabled(false);

        JTextField JTextFieldEnabled = new JTextField("Editable");
        JTextField JTextFieldDisabled = new JTextField("Disabled");
        JTextField JTextFieldEnabledNotEditable = new JTextField("Not Editable");
        JTextField JTextFieldDisabledNotEditable = new JTextField("Not Editable Disabled");
        JTextFieldDisabled.setEnabled(false);
        JTextFieldDisabledNotEditable.setEnabled(false);
        JTextFieldDisabledNotEditable.setEditable(false);
        JTextFieldEnabledNotEditable.setEditable(false);

        JPasswordField JPasswordFieldEnabled = new JPasswordField("Editable");
        JPasswordField JPasswordFieldDisabled = new JPasswordField("Disabled");
        JPasswordField JPasswordFieldEnabledNotEditable = new JPasswordField("Not Editable");
        JPasswordField JPasswordFieldDisabledNotEditable = new JPasswordField("Not Editable Disabled");
        JPasswordFieldDisabled.setEnabled(false);
        JPasswordFieldDisabledNotEditable.setEnabled(false);
        JPasswordFieldDisabledNotEditable.setEditable(false);
        JPasswordFieldEnabledNotEditable.setEditable(false);

        PanelBuilder panelBuilder = new PanelBuilder();
        panelBuilder.setComponentGrid(new Component[][]{
                new Component[]{new JLabel("JLabel:"), jLabelEnabled, jLabelDisabled, null, null},
                new Component[]{new JLabel("JButton:"), jbuttonEnabled, jbuttonDisabled, null, null},
                new Component[]{new JLabel("JCheckBox:"), JCheckBoxEnabled, JCheckBoxDisabled, JCheckBoxEnabledSelected, JCheckBoxDisabledSelected},
                new Component[]{new JLabel("JRadioButton:"), JRadioButtonEnabled, JRadioButtonDisabled, JRadioButtonEnabledSelected, JRadioButtonDisabledSelected},
                new Component[]{new JLabel("JComboBox:"), JComboBoxEnabled, JComboBoxDisabled, JComboBoxEnabledNotEditable, JComboBoxDisabledNotEditable},
                new Component[]{new JLabel("JSpinner:"), JSpinnerEnabled, JSpinnerDisabled, null, null},
                new Component[]{new JLabel("JTextField:"), JTextFieldEnabled, JTextFieldDisabled, JTextFieldEnabledNotEditable, JTextFieldDisabledNotEditable},
                new Component[]{new JLabel("JPasswordField:"), JPasswordFieldEnabled, JPasswordFieldDisabled, JPasswordFieldEnabledNotEditable, JPasswordFieldDisabledNotEditable},
        });
        panelBuilder.setAlignment(Alignment.FILL);
        JPanel basicComponents = panelBuilder.build();
        basicComponents.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JTextArea jTextArea = new JTextArea("An editable text area");
        jTextArea.setWrapStyleWord(true);
        JScrollPane jScrollPane = new JScrollPane(jTextArea);
        jScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        JTable jTable = new JTable(new Object[][]{new Object[]{false, "A", "1", "?"}, new Object[]{true, "B", "2", "!"}}, new String[]{"Boolean", "Letters", "Numbers", "Symbols"});
        DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode("Items");
        treeNode.add(new DefaultMutableTreeNode("Item A"));
        treeNode.add(new DefaultMutableTreeNode("Item B"));
        DefaultMutableTreeNode childNode = new DefaultMutableTreeNode("Item C");
        childNode.add(new DefaultMutableTreeNode("C - 1"));
        childNode.add(new DefaultMutableTreeNode("C - 2"));
        childNode.add(new DefaultMutableTreeNode("C - 3"));
        treeNode.add(childNode);

        JTree jTree = new JTree(treeNode);
        JList<String> jList = new JList<>(new String[]{"Item A", "Item B", "Item C", "Item D", "Item E", "Item F"});

        panelBuilder = new PanelBuilder();
        panelBuilder.setComponentGrid(new Component[][]{
                new Component[]{new JLabel("JEditor"), null},
                new Component[]{jScrollPane, jScrollPane},
                new Component[]{jScrollPane, jScrollPane},
                new Component[]{new JLabel("JTable"), null},
                new Component[]{jTable, jTable},
                new Component[]{new JLabel("JTree"), new JLabel("JList")},
                new Component[]{new JScrollPane(jTree), new JScrollPane(jList)},
        });
        int[][] weights = new int[][]{
                new int[]{0, 0},
                new int[]{1, 1},
                new int[]{1, 1},
                new int[]{0, 0},
                new int[]{1, 1},
                new int[]{0, 0},
                new int[]{5, 5},
        };
        panelBuilder.setGridWeightsX(weights);
        panelBuilder.setGridWeightsY(weights);
        panelBuilder.setAlignment(Alignment.FILL);
        JPanel otherComponents = panelBuilder.build();

        JTabbedPane jTabbedPane = new JTabbedPane();
        jTabbedPane.add("Basic", basicComponents);
        jTabbedPane.add("Data", otherComponents);

        JPanel jPanel = new JPanel(new BorderLayout());
        jPanel.add(jTabbedPane, BorderLayout.CENTER);
        return jPanel;
    }

}
