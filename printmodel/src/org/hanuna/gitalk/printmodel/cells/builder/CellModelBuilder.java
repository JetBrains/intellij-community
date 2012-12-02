package org.hanuna.gitalk.printmodel.cells.builder;

import org.hanuna.gitalk.common.generatemodel.CompressedList;
import org.hanuna.gitalk.common.generatemodel.Replace;
import org.hanuna.gitalk.common.generatemodel.RuntimeGenerateCompressedList;
import org.hanuna.gitalk.common.generatemodel.generator.Generator;
import org.hanuna.gitalk.common.readonly.ReadOnlyList;
import org.hanuna.gitalk.graph.GraphModel;
import org.hanuna.gitalk.graph.Node;
import org.hanuna.gitalk.graph.NodeRow;
import org.hanuna.gitalk.printmodel.cells.Cell;
import org.hanuna.gitalk.printmodel.cells.CellModel;
import org.hanuna.gitalk.printmodel.cells.CellRow;
import org.hanuna.gitalk.printmodel.cells.NodeCell;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author erokhins
 */
public class CellModelBuilder {
    private final GraphModel graphModel;
    private CompressedList<CellRow> generateModel;
    private final Generator<CellRow> generator;


    public CellModelBuilder(GraphModel graphModel) {
        this.graphModel = graphModel;
        this.generator = new CellRowGenerator(graphModel);
    }

    public CellModel build() {
        ReadOnlyList<NodeRow> rows = graphModel.getNodeRows();
        assert ! rows.isEmpty();

        NodeRow firstRow = rows.get(0);
        MutableCellRow firstCellRow = new MutableCellRow();
        firstCellRow.setRow(firstRow);
        List<Cell> cells = firstCellRow.getEditableCells();
        for (Node node : firstRow.getNodes()) {
            cells.add(new NodeCell(node));
        }

        generateModel = new RuntimeGenerateCompressedList<CellRow>(generator, firstCellRow, rows.size());
        return new CellModelImpl();
    }

    private class CellModelImpl implements CellModel {

        @NotNull
        @Override
        public ReadOnlyList<CellRow> getCellRows() {
            return generateModel.getList();
        }

        @Override
        public void update(@NotNull Replace replace) {
            generateModel.recalculate(replace);
        }
    }

}
