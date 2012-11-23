package org.hanuna.gitalk.swingui;

import org.hanuna.gitalk.printmodel.PrintCellRow;

import java.awt.*;

/**
 * @author erokhins
 */
public interface DrawGraphTableCell {

    public void draw(Graphics2D g2, PrintCellRow row);
}



/*
private void paintUpLine(Graphics2D g2, int from, int to, Color color) {
int x1 = GraphCell.WIDTH_NODE * from + GraphCell.WIDTH_NODE / 2;
int y1 = GraphCell.HEIGHT_CELL / 2;
int x2 = GraphCell.WIDTH_NODE * to + GraphCell.WIDTH_NODE / 2;
int y2 = - GraphCell.HEIGHT_CELL / 2;
g2.setColor(color);
g2.drawLine(x1, y1, x2, y2);
}

private void paintDownLine(Graphics2D g2, int from, int to, Color color) {
int x1 = GraphCell.WIDTH_NODE * from + GraphCell.WIDTH_NODE / 2;
int y1 = GraphCell.HEIGHT_CELL / 2;
int x2 = GraphCell.WIDTH_NODE * to + GraphCell.WIDTH_NODE / 2;
int y2 = GraphCell.HEIGHT_CELL + GraphCell.HEIGHT_CELL / 2;
g2.setColor(color);
g2.drawLine(x1, y1, x2, y2);
}


private void paintCircle(Graphics2D g2, int position, Color color) {
int x0 = GraphCell.WIDTH_NODE * position + GraphCell.WIDTH_NODE / 2;
int y0 = GraphCell.HEIGHT_CELL / 2;
int r = GraphCell.CIRCLE_RADIUS;
Ellipse2D.Double circle = new Ellipse2D.Double(x0 - r + 0.5, y0 - r + 0.5, 2 * r, 2 * r);
g2.setColor(color);
g2.fill(circle);
}

private void paintHide(Graphics2D g2, int position, Color color) {
int x0 = GraphCell.WIDTH_NODE * position + GraphCell.WIDTH_NODE / 2;
int y0 = GraphCell.HEIGHT_CELL / 2;
int r = GraphCell.CIRCLE_RADIUS;
g2.setColor(color);
g2.drawLine(x0, y0, x0 + r, y0 - r);
g2.drawLine(x0, y0, x0 - r, y0 - r);
}

private void paintShow(Graphics2D g2, int position, Color color) {
int x0 = GraphCell.WIDTH_NODE * position + GraphCell.WIDTH_NODE / 2;
int y0 = GraphCell.HEIGHT_CELL / 2;
int r = GraphCell.CIRCLE_RADIUS;
g2.setColor(color);
g2.drawLine(x0, y0, x0 + r, y0 + r);
g2.drawLine(x0, y0, x0 - r, y0 + r);
}


public void paint(Graphics g) {
super.paint(g);
Graphics2D g2 = (Graphics2D) g;
g2.setStroke(new BasicStroke(GraphCell.THICK_LINE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_BEVEL));

g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
PrintGraphRow commitRow = data.getCommitRow();
/*
ReadOnlyList<ShortEdge> edges = commitRow.getDownEdges(0);
for (ShortEdge edge : edges) {
    paintDownLine(g2, edge.getFrom(), edge.getTo(), ColorGenerator.getColor(edge.getColorIndex()));
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
*/
