package org.hanuna.gitalk.swing_ui.render;

import org.hanuna.gitalk.ui_controller.table_models.GraphCommitCell;
import org.hanuna.gitalk.swing_ui.render.painters.GraphCellPainter;
import org.hanuna.gitalk.swing_ui.render.painters.RefPainter;

import javax.swing.*;
import java.awt.*;
import java.awt.font.FontRenderContext;

import static org.hanuna.gitalk.ui_controller.table_models.GraphCommitCell.WIDTH_NODE;

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

        int countCells = cell.getRow().countCell();
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
        Graphics2D g2 = (Graphics2D) g;
        graphPainter.draw(g2, cell.getRow());

        int countCells = cell.getRow().countCell();
        int padding = countCells * WIDTH_NODE;
        refPainter.draw(g2, cell.getRefsToThisCommit(), padding);
    }



}
