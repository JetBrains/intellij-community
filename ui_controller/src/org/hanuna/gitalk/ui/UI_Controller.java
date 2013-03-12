package org.hanuna.gitalk.ui;

import org.hanuna.gitalk.graph.elements.GraphElement;
import org.jdesktop.swingx.treetable.TreeTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.TableModel;

/**
 * @author erokhins
 */
public interface UI_Controller {

    public TableModel getGraphTableModel();
    public TreeTableModel getRefsTreeTableModel();

    public void addControllerListener(@NotNull ControllerListener listener);
    public void removeAllListeners();

    public void click(@Nullable GraphElement graphElement);
    public void doubleClick(int rowIndex);
    public void over(@Nullable GraphElement graphElement);

    public void hideAll();

    public void setLongEdgeVisibility(boolean visibility);
    public void updateVisibleBranches();

    public void readNextPart();

    public void showAll();
}
