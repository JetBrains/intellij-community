package org.hanuna.gitalk.swingui;

import org.hanuna.gitalk.commitgraph.CommitRow;
import org.hanuna.gitalk.commitgraph.Edge;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.List;

import static org.hanuna.gitalk.swingui.GraphCell.CIRCLE_RADIUS;
import static org.hanuna.gitalk.swingui.GraphCell.HEIGHT_CELL;
import static org.hanuna.gitalk.swingui.GraphCell.WIDTH_NODE;

/**
 * @author erokhins
 */
public class GraphTableCellRender extends DefaultTableCellRenderer {
    private GraphCell data;

    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus, int row, int column) {
        data = (GraphCell) value;
        String text = data.getCommit().getData().getCommitMessage();
        super.getTableCellRendererComponent(table, text, isSelected, hasFocus, row, column);

        int countNodes = data.getCommitRow().count();
        int padding = countNodes * WIDTH_NODE;
        Border paddingBorder = BorderFactory.createEmptyBorder(0, padding, 0, 0);
        this.setBorder(BorderFactory.createCompoundBorder(this.getBorder(), paddingBorder));
        return this;
    }

    private void paintUpLine(Graphics g, int from, int to, Color color) {
        int x1 = WIDTH_NODE * from + WIDTH_NODE / 2;
        int y1 = HEIGHT_CELL / 2;
        int x2 = WIDTH_NODE * to + WIDTH_NODE / 2;
        int y2 = - HEIGHT_CELL / 2;
        g.setColor(color);
        g.drawLine(x1, y1, x2, y2);
    }

    private void paintDownLine(Graphics g, int from, int to, Color color) {
        int x1 = WIDTH_NODE * from + WIDTH_NODE / 2;
        int y1 = HEIGHT_CELL / 2;
        int x2 = WIDTH_NODE * to + WIDTH_NODE / 2;
        int y2 = HEIGHT_CELL + HEIGHT_CELL / 2;
        g.setColor(color);
        g.drawLine(x1, y1, x2, y2);
    }

    private void paintCircle(Graphics2D g, int position, Color color) {
        int x0 = WIDTH_NODE * position + WIDTH_NODE / 2;
        int y0 = HEIGHT_CELL / 2;
        g.setColor(color);
        int r = CIRCLE_RADIUS;

        g.setStroke(new BasicStroke(3F));
        g.drawOval(x0 - r, y0 - r, 2 * r, 2 * r);
    }

    public void paint(Graphics g) {
        super.paint(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setStroke(new BasicStroke(2F));
        CommitRow commitRow = data.getCommitRow();
        for (int i = 0; i < commitRow.count(); i++) {
            List<Edge> edges = commitRow.getDownEdges(i);
            for (int j = edges.size() - 1; j >= 0; j--) {
                Edge edge = edges.get(j);
                paintDownLine(g, i, edge.to(), ColorGenerator.getColor(edge.getIndexColor()));
            }

            edges = commitRow.getUpEdges(i);
            for (int j = edges.size() - 1; j >= 0; j--) {
                Edge edge = edges.get(j);
                paintUpLine(g, i, edge.to(), ColorGenerator.getColor(edge.getIndexColor()));
            }
        }
        int mainPos = commitRow.getMainPosition();
        paintCircle(g2, mainPos, Color.black);
    }

}
