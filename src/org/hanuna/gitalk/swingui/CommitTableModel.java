package org.hanuna.gitalk.swingui;

import org.hanuna.gitalk.commitgraph.CommitRow;
import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.common.ReadOnlyList;

import javax.swing.table.AbstractTableModel;

/**
 * @author erokhins
 */
public class CommitTableModel extends AbstractTableModel {
    private final ReadOnlyList<CommitRow> commitRows;
    private final ReadOnlyList<Commit> commits;
    private final String[] columnNames = {"Subject", "Author", "Date"};

    public CommitTableModel(ReadOnlyList<CommitRow> commitRows, ReadOnlyList<Commit> commits) {
        this.commitRows = commitRows;
        this.commits = commits;
    }

    @Override
    public Class getColumnClass(int column) {
        switch (column) {
            case 0:
                return GraphCell.class;
            case 1:
                return String.class;
            case 2:
                return String.class;
            default:
                throw new IllegalArgumentException("column > 2");
        }
    }

    @Override
    public int getRowCount() {
        return this.commits.size();
    }

    @Override
    public String getColumnName(int column) {
        return columnNames[column];
    }

    @Override
    public int getColumnCount() {
        return 3;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Commit commit = commits.get(rowIndex);
        switch (columnIndex) {
            case 0:
                return new GraphCell(commit, commitRows.get(rowIndex));
            case 1:
                return commit.getData().getAuthor();
            case 2:
                return commit.getData().getTimeStamp();
            default:
                throw new IllegalArgumentException("columnIndex > 2");
        }
    }
}
