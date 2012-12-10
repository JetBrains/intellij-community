package org.hanuna.gitalk.printmodel.layout;

import org.hanuna.gitalk.common.ReadOnlyList;
import org.hanuna.gitalk.common.compressedlist.CompressedList;
import org.hanuna.gitalk.common.compressedlist.Replace;
import org.hanuna.gitalk.common.compressedlist.RuntimeGenerateCompressedList;
import org.hanuna.gitalk.common.compressedlist.generator.Generator;
import org.hanuna.gitalk.graph.Graph;
import org.hanuna.gitalk.graph.graph_elements.GraphElement;
import org.hanuna.gitalk.graph.graph_elements.Node;
import org.hanuna.gitalk.graph.graph_elements.NodeRow;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author erokhins
 */
public class LayoutModel {
    private final Graph graph;
    private CompressedList<LayoutRow> generateModel;
    private final Generator<LayoutRow> generator;


    public LayoutModel(Graph graph) {
        this.graph = graph;
        this.generator = new LayoutRowGenerator(graph);
        build();
    }

    private void build() {
        ReadOnlyList<NodeRow> rows = graph.getNodeRows();
        assert ! rows.isEmpty();

        NodeRow firstRow = rows.get(0);
        MutableLayoutRow firstCellRow = new MutableLayoutRow();
        firstCellRow.setRow(firstRow);
        List<GraphElement> editableLayoutRow = firstCellRow.getEditableLayoutRow();
        for (Node node : firstRow.getVisibleNodes()) {
            editableLayoutRow.add(node);
        }
        generateModel = new RuntimeGenerateCompressedList<LayoutRow>(generator, firstCellRow, rows.size());
    }


    @NotNull
    public ReadOnlyList<LayoutRow> getLayoutRows() {
        return generateModel.getList();
    }

    public void recalculate(@NotNull Replace replace) {
        generateModel.recalculate(replace);
    }
}
