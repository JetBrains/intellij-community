package org.hanuna.gitalk.ui_controller.table_models;

import org.hanuna.gitalk.log.commit.Commit;
import org.hanuna.gitalk.log.commitdata.CommitData;
import org.hanuna.gitalk.log.commitdata.CommitDataGetter;
import org.hanuna.gitalk.refs.Ref;
import org.hanuna.gitalk.refs.RefsModel;
import org.hanuna.gitalk.ui_controller.DateConverter;

import javax.swing.table.AbstractTableModel;
import java.util.*;

/**
 * @author erokhins
 */
public class RefTableModel extends AbstractTableModel {
    private static final String[] COLUMN_NAMES = {"", "Subject", "Author", "Date"};

    private static List<Commit> getOrderedBranchCommit(RefsModel refsModel) {
        List<Commit> orderedCommit = new ArrayList<Commit>();
        for (Commit commit : refsModel.getOrderedLogTrackedCommit()) {
            boolean hasBranchRef = false;
            for (Ref ref : refsModel.refsToCommit(commit.getCommitHash())) {
                if (ref.getType() != Ref.Type.TAG) {
                    hasBranchRef = true;
                }
            }
            if (hasBranchRef) {
                orderedCommit.add(commit);
            }
        }
        return orderedCommit;
    }


    private final RefsModel refsModel;
    private final List<Commit> orderedRefCommit;
    private final Set<Commit> checkedCommits;
    private final CommitDataGetter commitDataGetter;



    public RefTableModel(RefsModel refsModel, CommitDataGetter commitDataGetter) {
        this.refsModel = refsModel;
        this.commitDataGetter = commitDataGetter;
        this.orderedRefCommit = getOrderedBranchCommit(refsModel);
        checkedCommits = new HashSet<Commit>(orderedRefCommit);
    }

    public Set<Commit> getCheckedCommits() {
        return Collections.unmodifiableSet(checkedCommits);
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
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return columnIndex == 0;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Commit commit = orderedRefCommit.get(rowIndex);
        CommitData data = commitDataGetter.getCommitData(commit.getCommitHash());
        switch (columnIndex) {
            case 0:
                return checkedCommits.contains(commit);
            case 1:
                return new CommitCell(data.getMessage(), refsModel.refsToCommit(commit.getCommitHash()));
            case 2:
                return data.getAuthor();
            case 3:
                return DateConverter.getStringOfDate(data.getTimeStamp());
            default:
                throw new IllegalArgumentException("columnIndex > 3");
        }
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        if (columnIndex == 0) {
            Boolean select = (Boolean) aValue;
            Commit commit = orderedRefCommit.get(rowIndex);
            if (select) {
                checkedCommits.add(commit);
            } else {
                checkedCommits.remove(commit);
                if (checkedCommits.size() == 0) {
                    checkedCommits.add(orderedRefCommit.get(0));
                }
            }
        }
    }

    @Override
    public Class<?> getColumnClass(int column) {
        switch (column) {
            case 0:
                return Boolean.class;
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

}
