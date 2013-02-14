package org.hanuna.gitalk.ui;

import org.hanuna.gitalk.graph.elements.GraphElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.TableModel;

/**
 * @author erokhins
 */
public interface UI_Controller {

    public TableModel getGraphTableModel();
    public TableModel getRefsTableModel();

    public void addControllerListener(@NotNull ControllerListener listener);
    public void removeAllListeners();

    public void click(@Nullable GraphElement graphElement);
    public void over(@Nullable GraphElement graphElement);

    public void hideAll();

    public void setLongEdgeVisibility(boolean visibility);
    public void updateVisibleBranches();

    public void readNextPart();
}
