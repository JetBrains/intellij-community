package org.hanuna.gitalk.ui_controller.table_models;

import org.hanuna.gitalk.log.commit.Hash;
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

    private static List<Hash> getOrderedBranchCommit(RefsModel refsModel) {
        List<Hash> orderedCommit = new ArrayList<Hash>();
        for (Hash hash : refsModel.getTrackedCommitHashes()) {
            boolean hasBranchRef = false;
            for (Ref ref : refsModel.refsToCommit(hash)) {
                if (ref.getType() != Ref.Type.TAG) {
                    hasBranchRef = true;
                }
            }
            if (hasBranchRef) {
                orderedCommit.add(hash);
            }
        }
        return orderedCommit;
    }


    private final RefsModel refsModel;
    private final List<Hash> orderedRefCommit;
    private final Set<Hash> checkedCommits;
    private final CommitDataGetter commitDataGetter;



    public RefTableModel(RefsModel refsModel, CommitDataGetter commitDataGetter) {
        this.refsModel = refsModel;
        this.commitDataGetter = commitDataGetter;
        this.orderedRefCommit = getOrderedBranchCommit(refsModel);
        checkedCommits = new HashSet<Hash>(orderedRefCommit);
    }

    public Set<Hash> getCheckedCommits() {
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
        Hash hash = orderedRefCommit.get(rowIndex);
        CommitData data = commitDataGetter.getCommitData(hash);
        switch (columnIndex) {
            case 0:
                return checkedCommits.contains(hash);
            case 1:
                return new CommitCell(data.getMessage(), refsModel.refsToCommit(hash));
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
            Hash hash = orderedRefCommit.get(rowIndex);
            if (select) {
                checkedCommits.add(hash);
            } else {
                checkedCommits.remove(hash);
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
