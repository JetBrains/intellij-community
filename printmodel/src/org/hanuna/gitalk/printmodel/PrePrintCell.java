package org.hanuna.gitalk.printmodel;

import org.hanuna.gitalk.graph.graph_elements.Edge;
import org.hanuna.gitalk.graph.graph_elements.GraphElement;
import org.hanuna.gitalk.graph.graph_elements.Node;
import org.hanuna.gitalk.printmodel.layout.LayoutModel;
import org.hanuna.gitalk.printmodel.layout.LayoutRow;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author erokhins
 */
class PrePrintCell {
    private static final int LONG_EDGE = 16;
    private static final int EDGE_PART_SHOW = 3;

    private final boolean hideLongEdge;
    private final LayoutModel layoutModel;
    private List<GraphElement> visibleElements;
    private final int rowIndex;
    private final SelectController selectController;

    public PrePrintCell(boolean hideLongEdge, LayoutModel layoutModel, int rowIndex, SelectController selectController) {
        this.hideLongEdge = hideLongEdge;
        this.layoutModel = layoutModel;
        this.rowIndex = rowIndex;
        this.selectController = selectController;
        visibleElements = visibleElements(rowIndex);
    }

    public PrePrintCell(LayoutModel layoutModel, int rowIndex, SelectController selectController) {
        this(true, layoutModel, rowIndex, selectController);
    }

    public int getCountCells() {
        return visibleElements.size();
    }

    /**
     * @return -1 - edge hide in this row, 0 - edge is USUAL, 1 - edge hide in next row, 2 - edge hide in prev row
     */
    private int visibleEdge(Edge edge, int rowIndex) {
        if (!hideLongEdge) {
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

    private List<GraphElement> visibleElements(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= layoutModel.getLayoutRows().size()) {
            return Collections.emptyList();
        }
        LayoutRow cellRow = layoutModel.getLayoutRows().get(rowIndex);
        List<GraphElement> cells = cellRow.getOrderedGraphElements();
        if (!hideLongEdge) {
            return cells;
        }

        List<GraphElement> visibleElements = new ArrayList<GraphElement>();
        for (GraphElement cell : cells) {
            if (cell.getNode() != null) {
                visibleElements.add(cell);
            } else {
                Edge edge = cell.getEdge();
                if (edge == null) {
                    throw new IllegalStateException();
                }
                if (visibleEdge(edge, rowIndex) != -1) {
                    visibleElements.add(cell);
                }
            }
        }

        return Collections.unmodifiableList(visibleElements);
    }

    @NotNull
    public List<SpecialCell> specialCells() {
        List<SpecialCell> specialCells = new ArrayList<SpecialCell>();

        for (int i = 0; i < visibleElements.size(); i++) {
            GraphElement element = visibleElements.get(i);
            Node node = element.getNode();
            if (node != null) {
                if (node.getType() == Node.Type.COMMIT_NODE) {
                    specialCells.add(new SpecialCell(node, i, SpecialCell.Type.COMMIT_NODE,
                            selectController.selected(node)));
                }
            } else {
                Edge edge = element.getEdge();
                if (edge == null) {
                    throw new IllegalStateException();
                }
                switch (visibleEdge(edge, rowIndex)) {
                    case -1:
                        // do nothing
                        break;
                    case 0:
                        // do nothing
                        break;
                    case 1:
                        specialCells.add(new SpecialCell(edge, i, SpecialCell.Type.HIDE_EDGE,
                                selectController.selected(edge)));
                        break;
                    case 2:
                        specialCells.add(new SpecialCell(edge, i, SpecialCell.Type.SHOW_EDGE,
                                selectController.selected(edge)));
                        break;
                    default:
                        throw new IllegalStateException();
                }
            }
        }
        return Collections.unmodifiableList(specialCells);
    }

    @NotNull
    public List<ShortEdge> downShortEdges() {
        GetterPosition getter = new GetterPosition(visibleElements(rowIndex + 1));

        List<ShortEdge> shortEdges = new ArrayList<ShortEdge>();
        // start with add shortEdges from Node
        for (int p = 0; p < visibleElements.size(); p++) {
            Node node = visibleElements.get(p).getNode();
            if (node != null) {
                for (Edge edge : node.getDownEdges()) {
                    int to = getter.getPosition(edge);
                    assert to != -1;
                    shortEdges.add(new ShortEdge(edge, p, to, selectController.selected(edge)));
                }
            }
        }
        for (int p = 0; p < visibleElements.size(); p++) {
            Edge edge = visibleElements.get(p).getEdge();
            if (edge != null) {
                int to = getter.getPosition(edge);
                if (to >= 0) {
                    shortEdges.add(new ShortEdge(edge, p, to, selectController.selected(edge)));
                }
            }
        }

        return Collections.unmodifiableList(shortEdges);
    }

    private static class GetterPosition {
        private final Map<Node, Integer> mapNodes = new HashMap<Node, Integer>();

        public GetterPosition(List<GraphElement> graphElements) {
            mapNodes.clear();
            for (int p = 0; p < graphElements.size(); p++) {
                mapNodes.put(getDownNode(graphElements.get(p)), p);
            }
        }

        private Node getDownNode(@NotNull GraphElement element) {
            Node node = element.getNode();
            if (node != null) {
                return node;
            } else {
                Edge edge = element.getEdge();
                if (edge == null) {
                    throw new IllegalStateException();
                }
                return edge.getDownNode();
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
