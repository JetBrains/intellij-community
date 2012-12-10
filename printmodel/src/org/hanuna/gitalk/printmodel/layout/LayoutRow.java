package org.hanuna.gitalk.printmodel.layout;

import org.hanuna.gitalk.common.ReadOnlyList;
import org.hanuna.gitalk.graph.graph_elements.GraphElement;
import org.hanuna.gitalk.graph.graph_elements.NodeRow;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public interface LayoutRow {
    @NotNull
    public ReadOnlyList<GraphElement> getOrderedGraphElements();
    public NodeRow getGraphRow();

}
