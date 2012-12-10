package org.hanuna.gitalk.printmodel.layout;

import org.hanuna.gitalk.common.ReadOnlyList;
import org.hanuna.gitalk.graph.graph_elements.GraphElement;
import org.hanuna.gitalk.graph.graph_elements.NodeRow;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedList;
import java.util.List;

/**
 * @author erokhins
 */
public class MutableLayoutRow implements LayoutRow {
    private final List<GraphElement> graphElements;
    private NodeRow row;

    public MutableLayoutRow() {
        graphElements = new LinkedList<GraphElement>();
    }

    public MutableLayoutRow(LayoutRow layoutRow) {
        this.graphElements = new LinkedList<GraphElement>(layoutRow.getOrderedGraphElements());
        this.row = layoutRow.getGraphRow();
    }

    public List<GraphElement> getEditableLayoutRow() {
        return graphElements;
    }

    public void setRow(NodeRow row) {
        this.row = row;
    }

    @NotNull
    @Override
    public ReadOnlyList<GraphElement> getOrderedGraphElements() {
        return ReadOnlyList.newReadOnlyList(graphElements);
    }

    @Override
    public NodeRow getGraphRow() {
        return row;
    }
}
