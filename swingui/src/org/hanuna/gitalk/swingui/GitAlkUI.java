package org.hanuna.gitalk.swingui;

import org.hanuna.gitalk.controller.Controller;
import org.hanuna.gitalk.controller.GraphTableCell;

import javax.swing.*;
import java.awt.*;

/**
 * @author erokhins
 */
public class GitAlkUI extends JFrame {
    private JTable table;
    private final DrawGraphTableCell drawGraph = new SimpleDrawGraphTableCell();

    public GitAlkUI(Controller controller) {
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setTitle("GitAlk");
        table = new JTable(controller.getTableModel());
        table.setDefaultRenderer(GraphTableCell.class, new GraphTableCellRender(drawGraph));
        table.setRowHeight(GraphTableCell.HEIGHT_CELL);
        table.setShowHorizontalLines(false);
        table.setIntercellSpacing(new Dimension(0, 0));

        table.getColumnModel().getColumn(0).setPreferredWidth(800);
        table.getColumnModel().getColumn(1).setMinWidth(80);
        table.getColumnModel().getColumn(2).setMinWidth(80);

        getContentPane().add(new JScrollPane(table));
        pack();
    }

    public void showUi() {
        setVisible(true);
    }

}
