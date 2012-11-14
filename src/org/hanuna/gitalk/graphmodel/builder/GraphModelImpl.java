package org.hanuna.gitalk.graphmodel.builder;

import org.hanuna.gitalk.common.readonly.ReadOnlyIterator;
import org.hanuna.gitalk.common.readonly.ReadOnlyList;
import org.hanuna.gitalk.common.Interval;
import org.hanuna.gitalk.graphmodel.Edge;
import org.hanuna.gitalk.graphmodel.GraphModel;
import org.hanuna.gitalk.graphmodel.Node;
import org.hanuna.gitalk.graphmodel.NodeRow;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;

/**
 * @author erokhins
 */
public class GraphModelImpl implements GraphModel {
    private final RemoveIntervalArrayList<MutableNodeRow> rows;
    private final RowsAdapter adapterRows;

    public GraphModelImpl(RemoveIntervalArrayList<MutableNodeRow> rows) {
        this.rows = rows;
        adapterRows = new RowsAdapter(rows);
    }


    @NotNull
    @Override
    public ReadOnlyList<NodeRow> getNodeRows() {
        return adapterRows;
    }

    @NotNull
    @Override
    public Interval showBranch(Edge edge) {
        return null;
    }

    @NotNull
    @Override
    public Interval hideBranch(Node upNode, Node downNode) {
        return null;
    }


    private static class RowsAdapter implements ReadOnlyList<NodeRow> {
        private final RemoveIntervalArrayList<MutableNodeRow> rows;

        private RowsAdapter(RemoveIntervalArrayList<MutableNodeRow> rows) {
            this.rows = rows;
        }

        @Override
        public int size() {
            return rows.size();
        }

        @Override
        public NodeRow get(int index) {
            return rows.get(index);
        }

        @Override
        public Iterator<NodeRow> iterator() {
            return new ReadOnlyIterator<NodeRow>(rows.iterator());
        }
    }

}
