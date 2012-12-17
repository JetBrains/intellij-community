package org.hanuna.gitalk.controller.table_models;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.commitmodel.CommitData;
import org.hanuna.gitalk.controller.DateConverter;
import org.hanuna.gitalk.refs.RefsModel;

import javax.swing.table.AbstractTableModel;
import java.util.Collections;
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
        CommitData data = commit.getData();
        assert data != null;
        switch (columnIndex) {
            case 0:
                return checkedCommits.contains(commit);
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
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        if (columnIndex == 0) {
            Boolean select = (Boolean) aValue;
            Commit commit = orderedRefCommit.get(rowIndex);
            if (select) {
                checkedCommits.add(commit);
            } else {
                checkedCommits.remove(commit);
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
