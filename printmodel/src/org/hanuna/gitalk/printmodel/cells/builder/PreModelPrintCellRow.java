package org.hanuna.gitalk.printmodel.cells.builder;

import org.hanuna.gitalk.common.readonly.ReadOnlyList;
import org.hanuna.gitalk.graph.Edge;
import org.hanuna.gitalk.graph.Node;
import org.hanuna.gitalk.printmodel.ShortEdge;
import org.hanuna.gitalk.printmodel.SpecialCell;
import org.hanuna.gitalk.printmodel.cells.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author erokhins
 */
public class PreModelPrintCellRow {
    private static final int LONG_EDGE = 16;
    private static final int EDGE_PART_SHOW = 3;

    private final boolean hideEdge;
    private final CellModel cellModel;
    public ReadOnlyList<Cell> visibleCells;
    public int rowIndex;

    public PreModelPrintCellRow(boolean hideEdge, CellModel cellModel) {
        this.hideEdge = hideEdge;
        this.cellModel = cellModel;
    }

    public PreModelPrintCellRow(CellModel cellModel) {
        this(true, cellModel);
    }


    public void prepare(int rowIndex) {
        this.rowIndex = rowIndex;
        visibleCells = visibleCells(rowIndex);
    }

    public int getCountCells() {
        return visibleCells.size();
    }

    /**
     * @return -1 - edge hide in this row, 0 - edge is usual, 1 - edge hide in next row, 2 - edge hide in prev row
     */
    private int visibleEdge(Edge edge, int rowIndex) {
        if (! hideEdge) {
            return 0;
        }
        int upRowIndex = edge.getUpNode().getRowIndex();
        int downRowIndex = edge.getDownNode().getRowIndex();
        if (downRowIndex - upRowIndex < LONG_EDGE) {
            return 0;
        }

        final int upDelta = rowIndex - upRowIndex;
        final int downDelta = downRowIndex - rowIndex;
        if (upDelta < EDGE_PART_SHOW || downDelta < EDGE_PART_SHOW) {
            return 0;
        }
        if (upDelta == EDGE_PART_SHOW) {
            return 1;
        }
        if (downDelta == EDGE_PART_SHOW) {
            return 2;
        }

        return -1;
    }

    private ReadOnlyList<Cell> visibleCells(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= cellModel.getCellRows().size()) {
            return ReadOnlyList.emptyList();
        }
        CellRow cellRow = cellModel.getCellRows().get(rowIndex);
        ReadOnlyList<Cell> cells = cellRow.getCells();
        if (!hideEdge) {
            return cells;
        }

        List<Cell> visibleCells = new ArrayList<Cell>();
        for (Cell cell : cells) {
            if (cell.getClass() == NodeCell.class) {
                visibleCells.add(cell);
            } else {
                if (cell.getClass() != EdgeCell.class) {
                    throw new IllegalStateException();
                }
                Edge edge = ((EdgeCell) cell).getEdge();
                if (visibleEdge(edge, rowIndex) != -1) {
                    visibleCells.add(cell);
                }
            }
        }

        return ReadOnlyList.newReadOnlyList(visibleCells);
    }

    @NotNull
    public ReadOnlyList<SpecialCell> specialCells() {
        List<SpecialCell> specialCells = new ArrayList<SpecialCell>();

        for (int i = 0; i < visibleCells.size(); i++) {
            Cell cell = visibleCells.get(i);
            if (cell.getClass() == NodeCell.class) {
                if (((NodeCell) cell).getNode().getType() == Node.Type.commitNode) {
                    specialCells.add(new SpecialCell(cell, i, SpecialCell.Type.commitNode));
                }
            } else {
                if (cell.getClass() != EdgeCell.class) {
                    throw new IllegalStateException();
                }
                Edge edge = ((EdgeCell) cell).getEdge();
                switch (visibleEdge(edge, rowIndex)) {
                    case -1:
                        // do nothing
                        break;
                    case 0:
                        // do nothing
                        break;
                    case 1:
                        specialCells.add(new SpecialCell(cell, i, SpecialCell.Type.hideEdge));
                        break;
                    case 2:
                        specialCells.add(new SpecialCell(cell, i, SpecialCell.Type.showEdge));
                        break;
                    default:
                        throw new IllegalStateException();
                }
            }
        }
        return ReadOnlyList.newReadOnlyList(specialCells);
    }

    @NotNull
    public ReadOnlyList<ShortEdge> downShortEdges() {
        GetterPosition getter = new GetterPosition();
        getter.prepare(visibleCells(rowIndex + 1));

        List<ShortEdge> shortEdges = new ArrayList<ShortEdge>();
        // start with add shortEdges from NodeCell
        for (int p = 0; p < visibleCells.size(); p++) {
            Cell cell = visibleCells.get(p);
            if (cell.getClass() == NodeCell.class) {
                Node node = ((NodeCell) cell).getNode();
                for (Edge edge : node.getDownEdges()) {
                    int to = getter.getPosition(edge);
                    assert to != -1;
                    shortEdges.add(new ShortEdge(edge, p, to));
                }
            }
        }
        for (int p = 0; p < visibleCells.size(); p++) {
            Cell cell = visibleCells.get(p);
            if (cell.getClass() == EdgeCell.class) {
                final Edge edge = ((EdgeCell) cell).getEdge();
                int to = getter.getPosition(edge);
                if (to >= 0) {
                    shortEdges.add(new ShortEdge(edge, p, to));
                }
            }
        }

        return ReadOnlyList.newReadOnlyList(shortEdges);
    }

    private static class GetterPosition {
        private final Map<Node, Integer> mapNodes = new HashMap<Node, Integer>();

        private Node getDownNode(@NotNull Cell cell) {
            if (cell.getClass() == NodeCell.class) {
                return ((NodeCell) cell).getNode();
            } else {
                if (cell.getClass() != EdgeCell.class) {
                    throw new IllegalStateException();
                }
                Edge edge = ((EdgeCell) cell).getEdge();
                return edge.getDownNode();
            }
        }

        public void prepare(List<Cell> cells) {
            mapNodes.clear();
            for (int p = 0; p < cells.size(); p++) {
                mapNodes.put(getDownNode(cells.get(p)), p);
            }
        }

        public int getPosition(Edge edge) {
            Integer p = mapNodes.get(edge.getDownNode());
            if (p == null) {
                // i.e. hide branch
                return -1;
            }
            return p;
        }

    }

}
