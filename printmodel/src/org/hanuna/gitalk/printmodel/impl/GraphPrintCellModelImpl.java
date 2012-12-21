package org.hanuna.gitalk.printmodel.impl;

import org.hanuna.gitalk.common.compressedlist.Replace;
import org.hanuna.gitalk.graph.Graph;
import org.hanuna.gitalk.printmodel.*;
import org.hanuna.gitalk.printmodel.layout.LayoutModel;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author erokhins
 */
public class GraphPrintCellModelImpl implements GraphPrintCellModel {
    private final LayoutModel layoutModel;
    private final SelectController selectController;

    public GraphPrintCellModelImpl(Graph graph) {
        this.layoutModel = new LayoutModel(graph);
        this.selectController = new SelectController();
    }

    private List<ShortEdge> getUpEdges(int rowIndex) {
        PrePrintCellModel prevPreModel = new PrePrintCellModel(layoutModel, rowIndex - 1, selectController);
        return prevPreModel.downShortEdges();
    }

    public void recalculate(@NotNull Replace replace) {
        layoutModel.recalculate(replace);
    }

    @NotNull
    public SelectController getSelectController() {
        return selectController;
    }

    @NotNull
    public GraphPrintCell getGraphPrintCell(final int rowIndex) {
        final PrePrintCellModel prePrintCellModel = new PrePrintCellModel(layoutModel, rowIndex, selectController);

        return new GraphPrintCell() {
            @Override
            public int countCell() {
                return prePrintCellModel.getCountCells();
            }

            @NotNull
            @Override
            public List<ShortEdge> getUpEdges() {
                return GraphPrintCellModelImpl.this.getUpEdges(rowIndex);
            }

            @NotNull
            @Override
            public List<ShortEdge> getDownEdges() {
                return prePrintCellModel.downShortEdges();
            }

            @NotNull
            @Override
            public List<SpecialPrintElement> getSpecialPrintElements() {
                return prePrintCellModel.getSpecialPrintElements();
            }
        };
    }
}
