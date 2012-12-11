package org.hanuna.gitalk.printmodel;

import org.hanuna.gitalk.common.ReadOnlyList;
import org.hanuna.gitalk.common.compressedlist.Replace;
import org.hanuna.gitalk.graph.Graph;
import org.hanuna.gitalk.printmodel.layout.LayoutModel;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public class PrintCellModel {
    private final LayoutModel layoutModel;

    public PrintCellModel(Graph graph) {
        this.layoutModel = new LayoutModel(graph);
    }

    private ReadOnlyList<ShortEdge> getUpEdges(int rowIndex) {
        PrePrintCell prevPre = new PrePrintCell(layoutModel, rowIndex);
        return prevPre.downShortEdges();
    }

    public void recalculate(@NotNull Replace replace) {
        layoutModel.recalculate(replace);
    }


    @NotNull
    public PrintCell getPrintCellRow(final int rowIndex) {
        final PrePrintCell prePrintCell = new PrePrintCell(layoutModel, rowIndex);

        return new PrintCell() {
            @Override
            public int countCell() {
                return prePrintCell.getCountCells();
            }

            @NotNull
            @Override
            public ReadOnlyList<ShortEdge> getUpEdges() {
                return PrintCellModel.this.getUpEdges(rowIndex);
            }

            @NotNull
            @Override
            public ReadOnlyList<ShortEdge> getDownEdges() {
                return prePrintCell.downShortEdges();
            }

            @NotNull
            @Override
            public ReadOnlyList<SpecialCell> getSpecialCell() {
                return prePrintCell.specialCells();
            }
        };
    }
}
