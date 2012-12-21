package org.hanuna.gitalk.printmodel.layout;

import org.hanuna.gitalk.graph.graph_elements.GraphElement;
import org.hanuna.gitalk.graph.graph_elements.NodeRow;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author erokhins
 */
public interface LayoutRow {
    @NotNull
    public List<GraphElement> getOrderedGraphElements();

    public NodeRow getGraphNodeRow();
}
