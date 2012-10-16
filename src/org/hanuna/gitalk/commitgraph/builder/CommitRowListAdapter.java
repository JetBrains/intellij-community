package org.hanuna.gitalk.commitgraph.builder;

import org.hanuna.gitalk.commitgraph.CommitRow;
import org.hanuna.gitalk.commitgraph.Edge;
import org.hanuna.gitalk.commitgraph.SimpleEdge;
import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.common.ReadOnlyList;
import org.hanuna.gitalk.common.SimpleReadOnlyList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * @author erokhins
 */
public class CommitRowListAdapter implements ReadOnlyList<CommitRow> {
    private final SimpleReadOnlyList<RowOfNode> rows;
    private final ReadOnlyList<Commit> listOfCommits;

    public CommitRowListAdapter(SimpleReadOnlyList<RowOfNode> rows, ReadOnlyList<Commit> listOfCommits) {
        this.rows = rows;
        this.listOfCommits = listOfCommits;
    }

    @Override
    public int size() {
        return rows.size();
    }

    @Override
    public CommitRow get(final int rowIndex) {
        final RowOfNode row = rows.get(rowIndex);
        return new CommitRow() {
            @Override
            public int count() {
                return row.size();
            }

            @Override
            public int getIndexCommit(int position) {
                return row.getNode(position).getCommitIndex();
            }

            @Override
            public int getMainPosition() {
                return row.getMainPosition();
            }

            @Override
            public List<Edge> getUpEdges(int position) {
                if (rowIndex == 0) {
                    return Collections.emptyList();
                }
                List<Edge> edges = new ArrayList<Edge>();
                int searchCommitIndex = getIndexCommit(position);
                RowOfNode prevRow = rows.get(rowIndex - 1);
                for (int pos = 0; pos < prevRow.size(); pos++) {
                    Node node = prevRow.getNode(pos);
                    if (node.getCommitIndex() == searchCommitIndex) {
                        edges.add(new SimpleEdge(pos, node.getColorIndex()));
                        continue;
                    }
                    if (node.getCommitIndex() == rowIndex - 1) {
                        Commit commit = listOfCommits.get(node.getCommitIndex());
                        ReadOnlyList<Commit> parents = commit.getParents();
                        if (parents.size() > 0 && parents.get(0).index() == searchCommitIndex) {
                            edges.add(new SimpleEdge(pos, node.getColorIndex()));
                        }
                        for (int i = 1; i < parents.size(); i++) {
                            if (parents.get(i).index() == searchCommitIndex) {
                                edges.add(new SimpleEdge(pos, prevRow.getStartIndexColor() + i - 1));
                            }
                        }
                    }
                }
                return edges;
            }

            @Override
            public List<Edge> getDownEdges(int position) {
                if (rowIndex == rows.size() - 1) {
                    return Collections.emptyList();
                }
                List<Edge> edges = new ArrayList<Edge>();
                RowOfNode nextRow = rows.get(rowIndex + 1);
                Node node = row.getNode(position);

                if (node.getCommitIndex() == rowIndex) {
                    Commit commit = listOfCommits.get(node.getCommitIndex());
                    ReadOnlyList<Commit> parents = commit.getParents();
                    if (parents.size() > 0) {
                        int pos = nextRow.getPositionOfCommit(parents.get(0).index());
                        edges.add(new SimpleEdge(pos, node.getColorIndex()));
                    }
                    for (int i = 1; i < parents.size(); i++) {
                        int startColor = row.getStartIndexColor();
                        int pos = nextRow.getPositionOfCommit(parents.get(i).index());
                        edges.add(new SimpleEdge(pos, startColor + i - 1));
                    }
                /*    Commit mainParent = commit.mainParent();
                    Commit secondParent = commit.secondParent();
                    if (mainParent != null) {
                        int pos = nextRow.getPositionOfCommit(mainParent.index());
                        edges.add(new SimpleEdge(pos, node.getColorIndex()));
                    }
                    if (secondParent != null) {
                        int pos = nextRow.getPositionOfCommit(secondParent.index());
                        edges.add(new SimpleEdge(pos, row.getStartIndexColor()));
                    }
                    */
                } else {
                    int pos = nextRow.getPositionOfCommit(node.getCommitIndex());
                    edges.add(new SimpleEdge(pos, node.getColorIndex()));
                }
                return edges;
            }
        };
    }

    @Override
    public Iterator<CommitRow> iterator() {
        return null;
    }
}
