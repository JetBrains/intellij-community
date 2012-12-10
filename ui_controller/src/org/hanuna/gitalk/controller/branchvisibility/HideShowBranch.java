package org.hanuna.gitalk.controller.branchvisibility;

import org.hanuna.gitalk.common.ReadOnlyList;
import org.hanuna.gitalk.graph.graph_elements.Edge;
import org.hanuna.gitalk.graph.graph_elements.Node;
import org.hanuna.gitalk.printmodel.cells.Cell;
import org.hanuna.gitalk.printmodel.cells.EdgeCell;
import org.hanuna.gitalk.printmodel.cells.NodeCell;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author erokhins
 */
public class HideShowBranch {


    public boolean isSimpleNode(Node node) {
        return node.getDownEdges().size() <= 1 && node.getUpEdges().size() <= 1;
    }

    /**
     * @return if node has only 1 upNode & upNode is simple
     */
    @Nullable
    public Node upNodeStep(Node node) {
        ReadOnlyList<Edge> upEdges = node.getUpEdges();
        if (upEdges.size() == 1) {
            Node upNode = upEdges.get(0).getUpNode();
            if (isSimpleNode(upNode)) {
                return upNode;
            }
        }
        return null;
    }

    @NotNull
    public Node upNode(@NotNull Node node) {
        Node n = upNodeStep(node);
        while (n != null) {
            node = n;
            n = upNodeStep(node);
        }
        return node;
    }

    @Nullable
    public Node downNodeStep(Node node) {
        ReadOnlyList<Edge> downEdges = node.getDownEdges();
        if (downEdges.size() == 1) {
            Node downNode = downEdges.get(0).getDownNode();
            if (isSimpleNode(downNode)) {
                return downNode;
            }
        }
        return null;
    }


    @NotNull
    public Node downNode(@NotNull Node node) {
        Node n = downNodeStep(node);
        while (n != null) {
            node = n;
            n = downNodeStep(node);
        }
        return node;
    }

    @Nullable
    public NodeInterval branchInterval(@Nullable Cell cell) {
        if (cell == null) {
            return null;
        }
        Node up = null, down = null;
        if (cell.getClass() == EdgeCell.class) {
            Edge edge = ((EdgeCell) cell).getEdge();
            if (isSimpleNode(edge.getUpNode())) {
                up = upNode(edge.getUpNode());
            }
            if (isSimpleNode(edge.getDownNode())) {
                down = downNode(edge.getDownNode());
            }

            if (up == null || down == null) {
                return null;
            }
        } else {
            if (cell.getClass() != NodeCell.class) {
                throw new IllegalStateException();
            }
            Node node = ((NodeCell) cell).getNode();
            if (!isSimpleNode(node)) {
                return null;
            }
            up = upNode(node);
            down = downNode(node);
        }


        if (up == down) {
            return null;
        }
        if (up.getDownEdges().get(0).getDownNode() == down) {
            return null;
        }

        return new NodeInterval(up, down);
    }

    @Nullable
    public Edge hideBranchOver(Cell cell) {
        if (cell == null) {
            return null;
        }
        if (cell.getClass() == EdgeCell.class) {
            Edge edge = ((EdgeCell) cell).getEdge();
            if (edge.getType() == Edge.Type.HIDE_FRAGMENT) {
                return edge;
            }
        }
        return null;
    }


}
