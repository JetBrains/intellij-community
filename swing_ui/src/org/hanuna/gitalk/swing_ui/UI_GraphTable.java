package org.hanuna.gitalk.swing_ui;

import org.hanuna.gitalk.graph.graph_elements.GraphElement;
import org.hanuna.gitalk.printmodel.GraphPrintCell;
import org.hanuna.gitalk.swing_ui.render.GraphCommitCellRender;
import org.hanuna.gitalk.swing_ui.render.painters.GraphCellPainter;
import org.hanuna.gitalk.swing_ui.render.painters.SimpleGraphCellPainter;
import org.hanuna.gitalk.ui_controller.EventsController;
import org.hanuna.gitalk.ui_controller.UI_Controller;
import org.hanuna.gitalk.ui_controller.table_models.GraphCommitCell;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.plaf.BorderUIResource;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * @author erokhins
 */
public class UI_GraphTable extends JTable {
    private final UI_Controller ui_controller;
    private final GraphCellPainter graphPainter = new SimpleGraphCellPainter();
    private final MouseAdapter mouseAdapter = new MyMouseAdapter();

    public UI_GraphTable(UI_Controller ui_controller) {
        super(ui_controller.getGraphTableModel());
        UIManager.put("Table.focusCellHighlightBorder", new BorderUIResource(
                new LineBorder(new Color(255,0,0, 0))));
        this.ui_controller = ui_controller;
        prepare();
    }

    private void prepare() {
        setDefaultRenderer(GraphCommitCell.class, new GraphCommitCellRender(graphPainter));
        setRowHeight(GraphCommitCell.HEIGHT_CELL);
        setShowHorizontalLines(false);
        setIntercellSpacing(new Dimension(0, 0));

        getColumnModel().getColumn(0).setPreferredWidth(700);
        getColumnModel().getColumn(1).setMinWidth(90);
        getColumnModel().getColumn(2).setMinWidth(90);

        addMouseMotionListener(mouseAdapter);
        addMouseListener(mouseAdapter);

        ui_controller.addControllerListener(new EventsController.ControllerListener() {
            @Override
            public void jumpToRow(int rowIndex) {
                scrollRectToVisible(getCellRect(rowIndex, 0, false));
                setRowSelectionInterval(rowIndex, rowIndex);
                scrollRectToVisible(getCellRect(rowIndex, 0, false));
            }

            @Override
            public void updateTable() {
                updateUI();
            }
        });

    }

    private class MyMouseAdapter extends MouseAdapter {
        @Nullable
        private GraphElement overCell(MouseEvent e) {
            int rowIndex = e.getY() / GraphCommitCell.HEIGHT_CELL;
            int y = e.getY() - rowIndex * GraphCommitCell.HEIGHT_CELL;
            int x = e.getX();
            GraphCommitCell commitCell = (GraphCommitCell) UI_GraphTable.this.getModel().getValueAt(rowIndex, 0);
            GraphPrintCell row = commitCell.getPrintCell();
            return graphPainter.mouseOver(row, x, y);
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            ui_controller.click(overCell(e));
        }

        @Override
        public void mouseMoved(MouseEvent e) {
            ui_controller.over(overCell(e));
        }
    }

}
