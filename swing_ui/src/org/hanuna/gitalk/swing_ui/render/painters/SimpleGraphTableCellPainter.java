package org.hanuna.gitalk.swing_ui.render.painters;

import org.hanuna.gitalk.graph.graph_elements.Edge;
import org.hanuna.gitalk.graph.graph_elements.GraphElement;
import org.hanuna.gitalk.graph.graph_elements.Node;
import org.hanuna.gitalk.printmodel.PrintCell;
import org.hanuna.gitalk.printmodel.ShortEdge;
import org.hanuna.gitalk.printmodel.SpecialCell;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.geom.Ellipse2D;

import static org.hanuna.gitalk.controller.GraphCommitCell.*;

/**
 * @author erokhins
 */
public class SimpleGraphTableCellPainter implements GraphTableCellPainter {
    private Graphics2D g2;
    private final Stroke usual = new BasicStroke(THICK_LINE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_BEVEL);
    private final Stroke hide = new BasicStroke(THICK_LINE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_BEVEL, 0, new float[]{7}, 0);
    private final Stroke selectUsual = new BasicStroke(SELECT_THICK_LINE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_BEVEL);
    private final Stroke selectHide = new BasicStroke(SELECT_THICK_LINE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_BEVEL, 0, new float[]{7}, 0);


    private void paintUpLine(int from, int to, Color color) {
        int x1 = WIDTH_NODE * from + WIDTH_NODE / 2;
        int y1 = HEIGHT_CELL / 2;
        int x2 = WIDTH_NODE * to + WIDTH_NODE / 2;
        int y2 = - HEIGHT_CELL / 2;
        g2.setColor(color);
        g2.drawLine(x2, y2, x1, y1);
    }

    private void paintDownLine(int from, int to, Color color) {
        int x1 = WIDTH_NODE * from + WIDTH_NODE / 2;
        int y1 = HEIGHT_CELL / 2;
        int x2 = WIDTH_NODE * to + WIDTH_NODE / 2;
        int y2 = HEIGHT_CELL + HEIGHT_CELL / 2;
        g2.setColor(color);
        g2.drawLine(x1, y1, x2, y2);
    }


    private void paintCircle(int position, Color color, boolean select) {
        int x0 = WIDTH_NODE * position + WIDTH_NODE / 2;
        int y0 = HEIGHT_CELL / 2;
        int r = CIRCLE_RADIUS;
        if (select) {
            r = SELECT_CIRCLE_RADIUS;
        }
        Ellipse2D.Double circle = new Ellipse2D.Double(x0 - r + 0.5, y0 - r + 0.5, 2 * r, 2 * r);
        g2.setColor(color);
        g2.fill(circle);
    }

    private void paintHide(int position, Color color) {
        int x0 = WIDTH_NODE * position + WIDTH_NODE / 2;
        int y0 = HEIGHT_CELL / 2;
        int r = CIRCLE_RADIUS;
        g2.setColor(color);
        g2.drawLine(x0, y0, x0, y0 + r);
        g2.drawLine(x0, y0 + r, x0 + r, y0 );
        g2.drawLine(x0, y0 + r, x0 - r, y0 );
    }

    private void paintShow(int position, Color color) {
        int x0 = WIDTH_NODE * position + WIDTH_NODE / 2;
        int y0 = HEIGHT_CELL / 2;
        int r = CIRCLE_RADIUS;
        g2.setColor(color);
        g2.drawLine(x0, y0, x0, y0 - r);
        g2.drawLine(x0, y0 - r, x0 + r, y0);
        g2.drawLine(x0, y0 - r, x0 - r, y0);
    }

    private void setStroke(boolean usual, boolean select) {
        if (usual) {
            if (select) {
                g2.setStroke(selectUsual);
            } else {
                g2.setStroke(this.usual);
            }
        } else {
            if (select) {
                g2.setStroke(selectHide);
            } else {
                g2.setStroke(hide);
            }
        }
    }

    @Override
    public void draw(Graphics2D g2, PrintCell row) {
        this.g2 = g2;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (ShortEdge edge : row.getUpEdges()) {
            setStroke(edge.isUsual(), edge.isSelected());
            paintUpLine(edge.getDownPosition(), edge.getUpPosition(), ColorGenerator.getColor(edge.getEdge().getBranch()));
        }
        for (ShortEdge edge : row.getDownEdges()) {
            setStroke(edge.isUsual(), edge.isSelected());
            paintDownLine(edge.getUpPosition(), edge.getDownPosition(), ColorGenerator.getColor(edge.getEdge().getBranch()));
        }
        for (SpecialCell cell : row.getSpecialCell()) {
            Edge edge;
            switch (cell.getType()) {
                case COMMIT_NODE:
                    Node node = cell.getGraphElement().getNode();
                    assert node != null;
                    paintCircle(cell.getPosition(), ColorGenerator.getColor(node.getBranch()), cell.isSelected());
                    break;
                case SHOW_EDGE:
                    edge = cell.getGraphElement().getEdge();
                    assert edge != null;
                    setStroke(edge.getType() == Edge.Type.USUAL, cell.isSelected());
                    paintShow(cell.getPosition(), ColorGenerator.getColor(edge.getBranch()));
                    break;
                case HIDE_EDGE:
                    edge = cell.getGraphElement().getEdge();
                    assert edge != null;
                    setStroke(edge.getType() == Edge.Type.USUAL, cell.isSelected());
                    paintHide(cell.getPosition(), ColorGenerator.getColor(edge.getBranch()));
                    break;
                default:
                    throw new IllegalStateException();
            }
        }

    }
    private float distance(int x1, int y1, int x2, int y2) {
        return (float) Math.sqrt((x1-x2)*(x1-x2) + (y1-y2)*(y1-y2));
    }

    private boolean overUpEdge(ShortEdge edge, int x, int y) {
        float thick = THICK_LINE;
        int x1 = WIDTH_NODE * edge.getDownPosition() + WIDTH_NODE / 2;
        int y1 = HEIGHT_CELL / 2;
        int x2 = WIDTH_NODE * edge.getUpPosition() + WIDTH_NODE / 2;
        int y2 = - HEIGHT_CELL / 2;
        //return true;
        return (distance(x1, y1, x, y) + distance(x2, y2, x, y) < distance(x1, y1, x2, y2) + thick);
    }

    private boolean overDownEdge(ShortEdge edge, int x, int y) {
        float thick = THICK_LINE;
        int x1 = WIDTH_NODE * edge.getUpPosition() + WIDTH_NODE / 2;
        int y1 = HEIGHT_CELL / 2;
        int x2 = WIDTH_NODE * edge.getDownPosition() + WIDTH_NODE / 2;
        int y2 = HEIGHT_CELL + HEIGHT_CELL / 2;
        return distance(x1, y1, x, y) + distance(x2, y2, x, y) < distance(x1, y1, x2, y2) + thick;
    }

    private boolean overNode(int position, int x, int y) {
        int x0 = WIDTH_NODE * position + WIDTH_NODE / 2;
        int y0 = HEIGHT_CELL / 2;
        int r = CIRCLE_RADIUS;
        return distance(x0, y0, x, y) <= r;
    }

    @Nullable
    @Override
    public GraphElement mouseOver(PrintCell row, int x, int y) {
        for (SpecialCell cell : row.getSpecialCell()) {
            if (cell.getType() == SpecialCell.Type.COMMIT_NODE) {
                if (overNode(cell.getPosition(), x, y)) {
                    return cell.getGraphElement();
                }
            }
        }
        for (ShortEdge edge : row.getUpEdges()) {
            if (overUpEdge(edge, x, y)) {
                return edge.getEdge();
            }
        }
        for (ShortEdge edge : row.getDownEdges()) {
            if (overDownEdge(edge, x, y)) {
                return edge.getEdge();
            }
        }

        return null;
    }
}
