package org.hanuna.gitalk.controller;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.commitmodel.CommitData;
import org.hanuna.gitalk.refs.RefsModel;

import javax.swing.table.AbstractTableModel;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author erokhins
 */
public class RefTableModel extends AbstractTableModel {
    private static final String[] COLUMN_NAMES = {"", "Subject", "Author", "Date"};


    private final RefsModel refsModel;
    private final List<Commit> orderedRefCommit;
    private final Set<Commit> checkedCommits;

    public RefTableModel(RefsModel refsModel) {
        this.refsModel = refsModel;
        this.orderedRefCommit = refsModel.getOrderedLogTrackedCommit();
        checkedCommits = new HashSet<Commit>(orderedRefCommit);
    }

    public Set<Commit> getCheckedCommits() {
        return checkedCommits;
    }

    @Override
    public int getRowCount() {
        return orderedRefCommit.size();
    }

    @Override
    public int getColumnCount() {
        return 4;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Commit commit = orderedRefCommit.get(columnIndex);
        CommitData data = commit.getData();
        assert data != null;
        switch (rowIndex) {
            case 0:
                return new CheckCommit(commit);
            case 1:
                return new CommitCell(data.getMessage(), refsModel.refsToCommit(commit.hash()));
            case 2:
                return data.getAuthor();
            case 3:
                return DateConverter.getStringOfDate(data.getTimeStamp());
            default:
                throw new IllegalArgumentException("columnIndex > 3");
        }
    }

    @Override
    public Class<?> getColumnClass(int column) {
        switch (column) {
            case 0:
                return CheckCommit.class;
            case 1:
                return CommitCell.class;
            case 2:
                return String.class;
            case 3:
                return String.class;
            default:
                throw new IllegalArgumentException("column > 2");
        }
    }

    @Override
    public String getColumnName(int column) {
        return COLUMN_NAMES[column];
    }

    public class CheckCommit {
        private final Commit commit;

        public CheckCommit(Commit commit) {
            this.commit = commit;
        }

        public boolean isChecked() {
            return checkedCommits.contains(commit);
        }

        public void setChecked(boolean checked) {
            if (checked) {
                checkedCommits.add(commit);
            } else {
                checkedCommits.remove(commit);
            }
        }
    }
}
