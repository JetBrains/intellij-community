package org.hanuna.gitalk.swingui;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.common.readonly.ReadOnlyList;
import org.hanuna.gitalk.printgraph.PrintGraphRow;

import javax.swing.*;
import java.awt.*;

/**
 * @author erokhins
 */
public class GitAlkUI extends JFrame {
    private JTable table;

    public GitAlkUI(ReadOnlyList<PrintGraphRow> commitRows, ReadOnlyList<Commit> commits) {
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setTitle("GitAlk");
        table = new JTable(new CommitTableModel(commitRows, commits));
        table.setDefaultRenderer(GraphCell.class, new GraphTableCellRender());
        table.setRowHeight(GraphCell.HEIGHT_CELL);
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

    public void update(ReadOnlyList<PrintGraphRow> commitRows, ReadOnlyList<Commit> commits) {
        table.setModel(new CommitTableModel(commitRows, commits));
    }
}
