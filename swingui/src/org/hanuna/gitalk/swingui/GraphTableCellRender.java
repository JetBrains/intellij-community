package org.hanuna.gitalk.swingui;

import org.hanuna.gitalk.controller.GraphTableCell;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

/**
 * @author erokhins
 */
public class GraphTableCellRender implements TableCellRenderer {
    private final DrawGraphTableCell drawGraph;
    private final ExtDefaultCellRender cellRender = new ExtDefaultCellRender();
    public GraphTableCellRender(DrawGraphTableCell drawGraph) {
        this.drawGraph = drawGraph;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        return cellRender.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
    }

    private class ExtDefaultCellRender extends DefaultTableCellRenderer {
        private GraphTableCell cell;

        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
            cell = (GraphTableCell) value;
            super.getTableCellRendererComponent(table, cell.getText(), isSelected, hasFocus, row, column);

            int countCells = cell.getRow().countCell();
            int padding = countCells * GraphTableCell.WIDTH_NODE;
            Border paddingBorder = BorderFactory.createEmptyBorder(0, padding, 0, 0);
            this.setBorder(BorderFactory.createCompoundBorder(this.getBorder(), paddingBorder));
            return this;
        }

        @Override
        public void paint(Graphics g) {
            super.paint(g);
            Graphics2D g2 = (Graphics2D) g;
            drawGraph.draw(g2, cell.getRow());
        }
    }


}
