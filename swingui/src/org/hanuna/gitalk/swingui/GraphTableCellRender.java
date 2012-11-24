package org.hanuna.gitalk.swingui;

import org.hanuna.gitalk.controller.GraphTableCell;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;

/**
 * @author erokhins
 */
public class GraphTableCellRender implements TableCellRenderer {
    private final DrawGraphTableCell drawGraph;
    private final ExtDefaultCellRender cellRender = new ExtDefaultCellRender();
    private final MouseAdapter mouseAdapter;

    public GraphTableCellRender(DrawGraphTableCell drawGraph, MouseAdapter mouseAdapter) {
        this.drawGraph = drawGraph;
        this.mouseAdapter = mouseAdapter;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        Component component =  cellRender.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        component.addMouseListener(mouseAdapter);
        component.addMouseMotionListener(mouseAdapter);
        return component;
    }

    public class ExtDefaultCellRender extends DefaultTableCellRenderer {
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

        public GraphTableCell getCell() {
            return cell;
        }

        @Override
        public void paint(Graphics g) {
            super.paint(g);
            Graphics2D g2 = (Graphics2D) g;
            drawGraph.draw(g2, cell.getRow());
        }
    }


}
