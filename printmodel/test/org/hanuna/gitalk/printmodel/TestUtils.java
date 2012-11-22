package org.hanuna.gitalk.printmodel;

import org.hanuna.gitalk.common.readonly.ReadOnlyList;
import org.hanuna.gitalk.graph.Edge;
import org.hanuna.gitalk.graph.Node;
import org.hanuna.gitalk.printmodel.cells.*;

/**
 * @author erokhins
 */
public class TestUtils {
    public static String toShortStr(Node node) {
        return node.getCommit().hash().toStrHash();
    }

    public static String toShortStr(Cell cell) {
        if (cell.getClass() == NodeCell.class) {
            Node node = ((NodeCell) cell).getNode();
            return toShortStr(node);
        } else {
            if (cell.getClass() != EdgeCell.class) {
                throw new IllegalStateException();
            }
            Edge edge = ((EdgeCell) cell).getEdge();
            return toShortStr(edge.getUpNode()) + ":" + toShortStr(edge.getDownNode());
        }
    }

    public static String toStr(CellRow row) {
        ReadOnlyList<Cell> cells = row.getCells();
        if (cells.isEmpty()) {
            return "";
        }
        StringBuilder s = new StringBuilder();
        s.append(toShortStr(cells.get(0)));
        for (int i = 1; i < cells.size(); i++) {
            s.append(" ").append(toShortStr(cells.get(i)));
        }
        return s.toString();
    }

    public static String toStr(CellModel cellModel) {
        ReadOnlyList<CellRow> cells = cellModel.getCellRows();
        if (cells.isEmpty()) {
            return "";
        }
        StringBuilder s = new StringBuilder();
        s.append(toStr(cells.get(0)));
        for (int i = 1; i < cells.size(); i++) {
            s.append("\n").append(toStr(cells.get(i)));
        }

        return s.toString();
    }
}
