package org.hanuna.gitalk.swingui;

import org.hanuna.gitalk.controller.UI_Controller;
import org.hanuna.gitalk.controller.EventsController;
import org.hanuna.gitalk.controller.GraphTableCell;
import org.hanuna.gitalk.graph.graph_elements.GraphElement;
import org.hanuna.gitalk.printmodel.PrintCell;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * @author erokhins
 */
public class GitAlkUI extends JFrame {
    private JTable table;
    private final DrawGraphTableCell drawGraph = new SimpleDrawGraphTableCell();
    private final MouseAdapter mouseAdapter = new MyMouseAdapter();
    private final UI_Controller controller;

    public GitAlkUI(UI_Controller controller) {
        this.controller = controller;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setTitle("GitAlk");
        table = new JTable(controller.getTableModel());
        table.setDefaultRenderer(GraphTableCell.class, new GraphTableCellRender(drawGraph, mouseAdapter));
        table.setRowHeight(GraphTableCell.HEIGHT_CELL);
        table.setShowHorizontalLines(false);
        table.setIntercellSpacing(new Dimension(0, 0));

        table.getColumnModel().getColumn(0).setPreferredWidth(800);
        table.getColumnModel().getColumn(1).setMinWidth(80);
        table.getColumnModel().getColumn(2).setMinWidth(80);

        table.addMouseMotionListener(mouseAdapter);
        table.addMouseListener(mouseAdapter);

        controller.addControllerListener(new EventsController.ControllerListener() {
            @Override
            public void jumpToRow(int rowIndex) {
                table.scrollRectToVisible(new Rectangle(table.getCellRect(rowIndex, 0, false)));
                table.setRowSelectionInterval(rowIndex, rowIndex);
            }

            @Override
            public void updateTable() {
                table.updateUI();
            }
        });

        getContentPane().add(new JScrollPane(table));
        pack();
    }

    public void showUi() {
        setVisible(true);
    }

    private class MyMouseAdapter extends MouseAdapter {
        @Nullable
        private GraphElement overCell(MouseEvent e) {
            int rowIndex = e.getY() / GraphTableCell.HEIGHT_CELL;
            int y = e.getY() - rowIndex * GraphTableCell.HEIGHT_CELL;
            int x = e.getX();
            PrintCell row = controller.getGraphPrintCell(rowIndex);
            return drawGraph.mouseOver(row, x, y);
        }

        @Override
        public void mouseClicked(MouseEvent e) {
           controller.click(overCell(e));
        }

        @Override
        public void mouseMoved(MouseEvent e) {
            controller.over(overCell(e));
        }
    }

}
