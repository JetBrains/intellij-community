package org.hanuna.gitalk.printmodel.layout;

import org.hanuna.gitalk.common.compressedlist.CompressedList;
import org.hanuna.gitalk.common.compressedlist.Replace;
import org.hanuna.gitalk.common.compressedlist.RuntimeGenerateCompressedList;
import org.hanuna.gitalk.common.compressedlist.generator.Generator;
import org.hanuna.gitalk.graph.NewGraph;
import org.hanuna.gitalk.graph.elements.GraphElement;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.graph.elements.NodeRow;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author erokhins
 */
public class LayoutModel {
    private final NewGraph graph;
    private CompressedList<LayoutRow> layoutRowCompressedList;
    private final Generator<LayoutRow> generator;


    public LayoutModel(@NotNull NewGraph graph) {
        this.graph = graph;
        this.generator = new LayoutRowGenerator(graph);
        build();
    }

    private void build() {
        List<NodeRow> rows = graph.getNodeRows();
        assert ! rows.isEmpty();

        NodeRow firstRow = rows.get(0);
        MutableLayoutRow firstCellRow = new MutableLayoutRow();
        firstCellRow.setNodeRow(firstRow);
        List<GraphElement> editableLayoutRow = firstCellRow.getModifiableOrderedGraphElements();
        for (Node node : firstRow.getNodes()) {
            editableLayoutRow.add(node);
        }
        layoutRowCompressedList = new RuntimeGenerateCompressedList<LayoutRow>(generator, firstCellRow, rows.size(), 100);
    }


    @NotNull
    public List<LayoutRow> getLayoutRows() {
        return layoutRowCompressedList.getList();
    }

    public void recalculate(@NotNull Replace replace) {
        layoutRowCompressedList.recalculate(replace);
    }
}
