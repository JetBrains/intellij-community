package org.hanuna.gitalk.ui.tables.refs.refs;

import org.jdesktop.swingx.treetable.DefaultTreeTableModel;

/**
 * @author erokhins
 */
public class RefTreeTableModel extends DefaultTreeTableModel {

    public RefTreeTableModel(RefTreeModel refTreeModel) {
        super(refTreeModel.getRootNode());
    }

    @Override
    public String getColumnName(int column) {
        switch (column) {
            case 0:
                return "";
            case 1:
                return "label";
            default:
                throw new IllegalArgumentException("bad number of column: " + column);
        }
    }

    @Override
    public Class<?> getColumnClass(int i) {
        if (i == 0) {
            return Boolean.class;
        }
        return Object.class;
    }

    @Override
    public boolean isCellEditable(Object node, int column) {
        return column == 0;
    }

    @Override
    public int getColumnCount() {
        return 2;
    }

    @Override
    public int getHierarchicalColumn() {
        return 1;
    }
}
