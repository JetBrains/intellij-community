package org.hanuna.gitalk.swing_ui.frame;

import org.hanuna.gitalk.commit.Hash;
import org.hanuna.gitalk.refs.Ref;
import org.hanuna.gitalk.swing_ui.render.Print_Parameters;
import org.hanuna.gitalk.swing_ui.render.RefTreeCellRender;
import org.hanuna.gitalk.ui.tables.refs.refs.RefTreeModel;
import org.hanuna.gitalk.ui.tables.refs.refs.RefTreeTableNode;
import org.jdesktop.swingx.JXTreeTable;
import org.jdesktop.swingx.treetable.TreeTableModel;
import org.jetbrains.annotations.Nullable;

import java.awt.event.KeyEvent;
import java.util.HashSet;
import java.util.Set;

/**
 * @author erokhins
 */
public class UI_RefTable extends JXTreeTable {
    private final RefTreeModel refTreeModel;

    public UI_RefTable(TreeTableModel treeModel, RefTreeModel refTreeModel) {
        super(treeModel);
        this.refTreeModel = refTreeModel;
        prepare();
    }

    private void prepare() {
        setRootVisible(false);

        getColumnModel().getColumn(0).setMaxWidth(20);

        setTreeCellRenderer(new RefTreeCellRender());

        setRowHeight(Print_Parameters.HEIGHT_CELL);

        setLeafIcon(null);
        setClosedIcon(null);
        setOpenIcon(null);
    }

    private RefTreeTableNode getNode(int row) {
        return (RefTreeTableNode) getPathForRow(row).getLastPathComponent();
    }


    private void addRefsToSet(Set<Ref> refs, RefTreeTableNode node) {
        if (node.isRefNode()) {
            refs.add(node.getRef());
        } else {
            for (int i = 0; i < node.getChildCount(); i++) {
                addRefsToSet(refs, (RefTreeTableNode) node.getChildAt(i));
            }
        }
    }


    private void changeSelectOfRow(int[] rows) {
        Set<Ref> selectedRefs = new HashSet<Ref>();
        for (int row : rows) {
            addRefsToSet(selectedRefs, getNode(row));
        }
        Set<Hash> selectHash = new HashSet<Hash>();
        for (Ref ref : selectedRefs) {
            selectHash.add(ref.getCommitHash());
        }
        refTreeModel.inverseSelectCommit(selectHash);
    }

    private int getParentRow(int row) {
        int parentRow = getRowForPath(getPathForRow(row).getParentPath());
        if (parentRow >= 0) {
            return parentRow;
        } else {
            return 0;
        }
    }

    // true, if something changes
    public boolean keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_SPACE:
                int[] rows = getSelectedRows();
                changeSelectOfRow(rows);
                return true;
            case KeyEvent.VK_LEFT:
                int row = getSelectedRow();
                if (isCollapsed(row)) {
                    int parentRow = getParentRow(row);
                    setRowSelectionInterval(parentRow, parentRow);
                    scrollRowToVisible(parentRow);
                }
                collapseRow(row);
                return true;
            case KeyEvent.VK_RIGHT:
                row = getSelectedRow();
                expandRow(row);
        }

        return false;
    }

    @Nullable
    public Hash getCommitHashInRow(int row) {
        RefTreeTableNode node = getNode(row);
        if (node.isRefNode()) {
            return node.getRef().getCommitHash();
        } else {
            return null;
        }
    }
}
