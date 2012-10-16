package org.hanuna.gitalk.swingui;

import org.hanuna.gitalk.commitgraph.CommitRowList;
import org.hanuna.gitalk.commitmodel.CommitList;

import javax.swing.*;
import java.awt.*;

/**
 * @author erokhins
 */
public class GitAlkUI extends JFrame {

    public GitAlkUI(CommitRowList commitRows, CommitList commits) {
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
