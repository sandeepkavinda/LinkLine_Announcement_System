package com.linkline.fam.ui.components;

import com.linkline.fam.ui.Theme;
import com.linkline.fam.ui.Ui;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;

/** Consistent table styling and the cell renderers shared by the screens. */
public final class Tables {

    private Tables() {
    }

    public static JTable create(TableModel model) {
        JTable t = new JTable(model);
        t.setRowHeight(48);
        t.setShowVerticalLines(false);
        t.setShowHorizontalLines(true);
        t.setGridColor(Theme.BORDER_SOFT);
        t.setIntercellSpacing(new java.awt.Dimension(0, 1));
        t.setFillsViewportHeight(true);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        t.setFont(Theme.font(13f));
        t.setBackground(Theme.SURFACE);
        t.setFocusable(true);
        t.setDefaultRenderer(Object.class, new TextRenderer(false));
        JTableHeader h = t.getTableHeader();
        h.setReorderingAllowed(false);
        h.setFont(Theme.font(11.5f, Font.BOLD));
        h.setDefaultRenderer(new HeaderRenderer());
        return t;
    }

    /** Wraps a table in a card so it matches the rest of the interface. */
    public static JPanel card(JTable table) {
        Card card = new Card(new BorderLayout());
        card.setBorder(Ui.pad(6));
        JScrollPane sp = Ui.scroll(table);
        sp.getViewport().setBackground(Theme.SURFACE);
        sp.getViewport().setOpaque(true);
        card.add(sp, BorderLayout.CENTER);
        return card;
    }

    public static void widths(JTable t, int... widths) {
        for (int i = 0; i < widths.length && i < t.getColumnCount(); i++) {
            if (widths[i] > 0) {
                t.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
            } else if (widths[i] < 0) {
                int w = -widths[i];
                t.getColumnModel().getColumn(i).setMinWidth(w);
                t.getColumnModel().getColumn(i).setMaxWidth(w);
                t.getColumnModel().getColumn(i).setPreferredWidth(w);
            }
        }
    }

    public static void onDoubleClick(JTable t, Consumer<Integer> action) {
        t.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
                    int row = t.rowAtPoint(e.getPoint());
                    if (row >= 0) {
                        action.accept(row);
                    }
                }
            }
        });
    }

    /** Selects the row under the pointer on right click, so context menus act on what the user sees. */
    public static void selectOnRightClick(JTable t) {
        t.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (javax.swing.SwingUtilities.isRightMouseButton(e)) {
                    int row = t.rowAtPoint(e.getPoint());
                    if (row >= 0) {
                        t.setRowSelectionInterval(row, row);
                    }
                }
            }
        });
    }

    // ---- renderers --------------------------------------------------------------------------

    /** Value of a cell that shows an icon next to text. */
    public record IconText(Icon icon, String text, Color color, String tooltip) {
        public IconText(Icon icon, String text) {
            this(icon, text, null, null);
        }
    }

    /** Value of a two-line cell: a title and a smaller muted line under it. */
    public record TwoLine(Icon icon, String title, String subtitle) {
    }

    public static final class TextRenderer extends DefaultTableCellRenderer {
        private final boolean muted;

        public TextRenderer(boolean muted) {
            this.muted = muted;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus,
                                                       int row, int column) {
            String text = value instanceof IconText it ? it.text() : value == null ? "" : value.toString();
            super.getTableCellRendererComponent(table, text, selected, false, row, column);
            setBorder(Ui.pad(0, 12, 0, 8));
            setIcon(null);
            setToolTipText(null);
            setFont(Theme.font(13f));
            if (value instanceof IconText it) {
                setIcon(it.icon());
                setIconTextGap(8);
                if (it.color() != null && !selected) {
                    setForeground(it.color());
                }
                setToolTipText(it.tooltip());
            } else if (muted && !selected) {
                setForeground(Theme.TEXT_MUTED);
            }
            if (value instanceof String s && s.length() > 40) {
                setToolTipText(s);
            }
            return this;
        }
    }

    public static final class TwoLineRenderer implements TableCellRenderer {
        private final JPanel panel = new JPanel();
        private final JLabel title = new JLabel();
        private final JLabel sub = new JLabel();
        private final JLabel icon = new JLabel();

        public TwoLineRenderer() {
            panel.setLayout(new BorderLayout(10, 0));
            JPanel texts = new JPanel(new java.awt.GridLayout(2, 1, 0, 1));
            texts.setOpaque(false);
            texts.add(title);
            texts.add(sub);
            panel.add(icon, BorderLayout.WEST);
            panel.add(texts, BorderLayout.CENTER);
            panel.setBorder(Ui.pad(5, 12, 5, 8));
            title.setFont(Theme.font(13.5f, Font.BOLD));
            sub.setFont(Theme.font(12f));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus,
                                                       int row, int column) {
            TwoLine v = value instanceof TwoLine t ? t : new TwoLine(null, String.valueOf(value), "");
            panel.setBackground(selected ? table.getSelectionBackground() : table.getBackground());
            panel.setOpaque(true);
            title.setText(v.title());
            title.setForeground(Theme.TEXT);
            sub.setText(v.subtitle() == null || v.subtitle().isBlank() ? " " : v.subtitle());
            sub.setForeground(Theme.TEXT_MUTED);
            icon.setIcon(v.icon());
            icon.setVisible(v.icon() != null);
            return panel;
        }
    }

    /** Centers an icon in its cell; used for the "active" toggle and selection markers. */
    public static final class IconRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus,
                                                       int row, int column) {
            super.getTableCellRendererComponent(table, "", selected, false, row, column);
            setIcon(value instanceof Icon i ? i : null);
            setHorizontalAlignment(SwingConstants.CENTER);
            setBorder(null);
            return this;
        }
    }

    private static final class HeaderRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus,
                                                       int row, int column) {
            super.getTableCellRendererComponent(table, value == null ? "" : value.toString().toUpperCase(), false,
                    false, row, column);
            setBackground(Theme.SURFACE);
            setForeground(Theme.TEXT_FAINT);
            setFont(Theme.font(11f, Font.BOLD));
            setBorder(javax.swing.BorderFactory.createCompoundBorder(
                    javax.swing.BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                    Ui.pad(0, 12, 0, 8)));
            setHorizontalAlignment(SwingConstants.LEFT);
            return this;
        }
    }
}
