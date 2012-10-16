package org.hanuna.gitalk.swingui;

import org.hanuna.gitalk.commitgraph.CommitRow;
import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.common.ReadOnlyList;

import javax.swing.*;
import java.awt.*;

/**
 * @author erokhins
 */
public class GitAlkUI extends JFrame {

    public GitAlkUI(ReadOnlyList<CommitRow> commitRows, ReadOnlyList<Commit> commits) {
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        JTable table = new JTable(new CommitTableModel(commitRows, commits));
        table.setDefaultRenderer(GraphCell.class, new GraphTableCellRender());
        table.setRowHeight(GraphCell.HEIGHT_CELL);
        table.setShowHorizontalLines(false);
        table.setIntercellSpacing(new Dimension(0, 0));


        getContentPane().add(new JScrollPane(table));
        pack();
        setVisible(true);
    }

}
