package org.hanuna.gitalk.swingui;

import org.hanuna.gitalk.commitgraph.CommitRow;
import org.hanuna.gitalk.commitgraph.Edge;
import org.hanuna.gitalk.commitgraph.SpecialNode;
import org.hanuna.gitalk.common.ReadOnlyList;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.geom.Ellipse2D;

import static org.hanuna.gitalk.swingui.GraphCell.*;

/**
 * @author erokhins
 */
public class GraphTableCellRender extends DefaultTableCellRenderer {
    private GraphCell data;

    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus, int row, int column) {
        data = (GraphCell) value;
        String text = data.getCommit().getMessage();
        super.getTableCellRendererComponent(table, text, isSelected, hasFocus, row, column);

        int countNodes = data.getCommitRow().count();
        int padding = countNodes * WIDTH_NODE;
        Border paddingBorder = BorderFactory.createEmptyBorder(0, padding, 0, 0);
        this.setBorder(BorderFactory.createCompoundBorder(this.getBorder(), paddingBorder));
        return this;
    }

    private void paintUpLine(Graphics2D g2, int from, int to, Color color) {
        int x1 = WIDTH_NODE * from + WIDTH_NODE / 2;
        int y1 = HEIGHT_CELL / 2;
        int x2 = WIDTH_NODE * to + WIDTH_NODE / 2;
        int y2 = - HEIGHT_CELL / 2;
        g2.setColor(color);
        g2.drawLine(x1, y1, x2, y2);
    }

    private void paintDownLine(Graphics2D g2, int from, int to, Color color) {
        int x1 = WIDTH_NODE * from + WIDTH_NODE / 2;
        int y1 = HEIGHT_CELL / 2;
        int x2 = WIDTH_NODE * to + WIDTH_NODE / 2;
        int y2 = HEIGHT_CELL + HEIGHT_CELL / 2;
        g2.setColor(color);
        g2.drawLine(x1, y1, x2, y2);
    }


    private void paintCircle(Graphics2D g2, int position, Color color) {
        int x0 = WIDTH_NODE * position + WIDTH_NODE / 2;
        int y0 = HEIGHT_CELL / 2;
        int r = CIRCLE_RADIUS;
        Ellipse2D.Double circle = new Ellipse2D.Double(x0 - r + 0.5, y0 - r + 0.5, 2 * r, 2 * r);
        g2.setColor(color);
        g2.fill(circle);
    }

    private void paintHide(Graphics2D g2, int position, Color color) {
        int x0 = WIDTH_NODE * position + WIDTH_NODE / 2;
        int y0 = HEIGHT_CELL / 2;
        int r = CIRCLE_RADIUS;
        g2.setColor(color);
        g2.drawLine(x0, y0, x0 + r, y0 - r);
        g2.drawLine(x0, y0, x0 - r, y0 - r);
    }

    private void paintShow(Graphics2D g2, int position, Color color) {
        int x0 = WIDTH_NODE * position + WIDTH_NODE / 2;
        int y0 = HEIGHT_CELL / 2;
        int r = CIRCLE_RADIUS;
        g2.setColor(color);
        g2.drawLine(x0, y0, x0 + r, y0 + r);
        g2.drawLine(x0, y0, x0 - r, y0 + r);
    }


    public void paint(Graphics g) {
        super.paint(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setStroke(new BasicStroke(THICK_LINE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_BEVEL));

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        CommitRow commitRow = data.getCommitRow();

        ReadOnlyList<Edge> edges = commitRow.getDownEdges();
        for (Edge edge : edges) {
            paintDownLine(g2, edge.from(), edge.to(), ColorGenerator.getColor(edge.getColorIndex()));
        }

        edges = commitRow.getUpEdges();
        for (Edge edge : edges) {
            paintUpLine(g2, edge.from(), edge.to(), ColorGenerator.getColor(edge.getColorIndex()));
        }

        ReadOnlyList<SpecialNode> nodes = commitRow.getSpecialNodes();
        for (SpecialNode node : nodes) {
            int pos = node.getPosition();
            Color color = ColorGenerator.getColor(node.getColorIndex());
            switch (node.getType()) {
                case Current:
                    paintCircle(g2, pos, color);
                    break;
                case Hide:
                    paintHide(g2, pos, color);
                    break;
                case Show:
                    paintShow(g2, pos, color);
                    break;
                default:
                    throw new IllegalStateException();
            }
        }
    }

}
