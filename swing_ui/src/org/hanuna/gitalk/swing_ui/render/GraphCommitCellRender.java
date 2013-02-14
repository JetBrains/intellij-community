package org.hanuna.gitalk.swing_ui.render;

import org.hanuna.gitalk.ui.tables.GraphCommitCell;
import org.hanuna.gitalk.swing_ui.render.painters.GraphCellPainter;
import org.hanuna.gitalk.swing_ui.render.painters.RefPainter;

import javax.swing.*;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.image.BufferedImage;

import static org.hanuna.gitalk.swing_ui.render.Print_Parameters.*;

/**
 * @author erokhins
 */
public class GraphCommitCellRender extends AbstractPaddingCellRender {
    private final GraphCellPainter graphPainter;
    private final RefPainter refPainter = new RefPainter();

    public GraphCommitCellRender(GraphCellPainter graphPainter) {
        this.graphPainter = graphPainter;
    }

    private GraphCommitCell getAssertGraphCommitCell(Object value) {
        assert value instanceof GraphCommitCell;
        return (GraphCommitCell) value;
    }

    @Override
    protected int getLeftPadding(JTable table, Object value) {
        GraphCommitCell cell = getAssertGraphCommitCell(value);

        FontRenderContext fontContext = ((Graphics2D) table.getGraphics()).getFontRenderContext();
        int refPadding = refPainter.padding(cell.getRefsToThisCommit(), fontContext);

        int countCells = cell.getPrintCell().countCell();
        int graphPadding = countCells * WIDTH_NODE;

        return refPadding + graphPadding;
    }

    @Override
    protected String getCellText(JTable table, Object value) {
        GraphCommitCell cell = getAssertGraphCommitCell(value);
        return cell.getText();
    }

    @Override
    protected void additionPaint(Graphics g, JTable table, Object value) {
        GraphCommitCell cell = getAssertGraphCommitCell(value);

        BufferedImage image = new BufferedImage(1000, HEIGHT_CELL, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        g2.setBackground(new Color(0,0,0,0));

        graphPainter.draw(g2, cell.getPrintCell());

        int countCells = cell.getPrintCell().countCell();
        int padding = countCells * WIDTH_NODE;
        refPainter.draw(g2, cell.getRefsToThisCommit(), padding);

        g.drawImage(image,0, 0, null);
    }



}
