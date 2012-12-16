package org.hanuna.gitalk.swing_ui.render;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

/**
 * @author erokhins
 */
public abstract class AbstractPaddingCellRender implements TableCellRenderer {
    private ExtDefaultCellRender cellRender = new ExtDefaultCellRender();

    protected abstract int getLeftPadding(JTable table, Object value);

    protected abstract String getCellText(JTable table, Object value);

    protected abstract void additionPaint(Graphics g, JTable table, Object value);

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                   boolean hasFocus, int row, int column) {
        return cellRender.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
    }

    public class ExtDefaultCellRender extends DefaultTableCellRenderer {
        private JTable table;
        private Object value;

        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
            this.table = table;
            this.value = value;
            super.getTableCellRendererComponent(table, getCellText(table, value), isSelected, hasFocus, row, column);
            Border paddingBorder = BorderFactory.createEmptyBorder(0, getLeftPadding(table, value), 0, 0);
            this.setBorder(BorderFactory.createCompoundBorder(this.getBorder(), paddingBorder));
            return this;
        }


        @Override
        public void paint(Graphics g) {
            super.paint(g);
            additionPaint(g, table, value);
        }
    }


}
