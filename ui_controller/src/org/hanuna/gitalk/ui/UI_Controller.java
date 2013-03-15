package org.hanuna.gitalk.ui;

import org.hanuna.gitalk.graph.elements.GraphElement;
import org.hanuna.gitalk.ui.tables.refs.refs.RefTreeModel;
import org.jdesktop.swingx.treetable.TreeTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.TableModel;

/**
 * @author erokhins
 */
public interface UI_Controller {

    public TableModel getGraphTableModel();

    public void click(@Nullable GraphElement graphElement);
    public void over(@Nullable GraphElement graphElement);
    public void hideAll();

    public void showAll();
    public void setLongEdgeVisibility(boolean visibility);
    public void updateVisibleBranches();

    public TreeTableModel getRefsTreeTableModel();
    public RefTreeModel getRefTreeModel();

    public void addControllerListener(@NotNull ControllerListener listener);


    public void removeAllListeners();

    public void doubleClick(int rowIndex);
    public void readNextPart();
}
