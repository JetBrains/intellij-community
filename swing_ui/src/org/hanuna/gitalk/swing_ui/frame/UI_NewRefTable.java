package org.hanuna.gitalk.swing_ui.frame;

import org.hanuna.gitalk.swing_ui.render.Print_Parameters;
import org.hanuna.gitalk.swing_ui.render.RefTreeCellRender;
import org.jdesktop.swingx.JXTreeTable;
import org.jdesktop.swingx.treetable.TreeTableModel;

/**
 * @author erokhins
 */
public class UI_NewRefTable extends JXTreeTable {

    public UI_NewRefTable(TreeTableModel treeModel) {
        super(treeModel);
        prepare();
    }

    private void prepare() {
        setRootVisible(false);
        expandAll();

        getColumnModel().getColumn(0).setMaxWidth(20);

        setTreeCellRenderer(new RefTreeCellRender());

        setRowHeight(Print_Parameters.HEIGHT_CELL);

        setLeafIcon(null);
        setClosedIcon(null);
        setOpenIcon(null);
    }
}
