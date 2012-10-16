package org.hanuna.gitalk.commitgraph.builder;

import org.hanuna.gitalk.commitgraph.CommitRow;
import org.hanuna.gitalk.commitgraph.CommitRowList;
import org.hanuna.gitalk.commitgraph.Edge;
import org.hanuna.gitalk.commitgraph.SimpleEdge;
import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.commitmodel.CommitList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author erokhins
 */
public class CommitRowListAdapter implements CommitRowList {
    private final List<RowOfNode> rows;
    private final CommitList listOfCommits;

    public CommitRowListAdapter(List<RowOfNode> rows, CommitList listOfCommits) {
        this.rows = rows;
        this.listOfCommits = listOfCommits;
    }

    @Override
    public int size() {
        return rows.size();
    }

    @Override
    public CommitRow get(final int lineIndex) {
        final RowOfNode row = rows.get(lineIndex);
        return new CommitRow() {
            @Override
            public int count() {
                return row.size();
            }

            @Override
            public int getIndexCommit(int position) {
                return row.getGraphNode(position).getIndexCommit();
            }

            @Override
            public int getMainPosition() {
                return row.getMainPosition();
            }

            @Override
            public List<Edge> getUpEdges(int position) {
                if (lineIndex == 0) {
                    return Collections.emptyList();
                }
                List<Edge> edges = new ArrayList<Edge>();
                int indexCommit = getIndexCommit(position);
                RowOfNode prevRow = rows.get(lineIndex - 1);
                for (int i = 0; i < prevRow.size(); i++) {
                    GraphNode node = prevRow.getGraphNode(i);
                    if (node.getIndexCommit() == indexCommit) {
                        edges.add(new SimpleEdge(i, node.getIndexColor()));
                        continue;
                    }
                    if (node.getIndexCommit() == lineIndex - 1) {
                        Commit commit = listOfCommits.get(node.getIndexCommit());
                        Commit mainParent = commit.mainParent();
                        Commit secondParent = commit.secondParent();
                        if (mainParent != null && mainParent.index() == indexCommit) {
                            edges.add(new SimpleEdge(i, node.getIndexColor()));
                        }
                        if (secondParent != null && secondParent.index() == indexCommit) {
                            edges.add(new SimpleEdge(i, prevRow.getAdditionColor()));
                        }
                    }
                }
                return edges;
            }

            @Override
            public List<Edge> getDownEdges(int position) {
                if (lineIndex == rows.size() - 1) {
                    return Collections.emptyList();
                }
                List<Edge> edges = new ArrayList<Edge>();
                RowOfNode nextRow = rows.get(lineIndex + 1);
                GraphNode node = row.getGraphNode(position);
                if (node.getIndexCommit() == lineIndex) {
                    Commit commit = listOfCommits.get(node.getIndexCommit());
                    Commit mainParent = commit.mainParent();
                    Commit secondParent = commit.secondParent();
                    if (mainParent != null) {
                        int pos = nextRow.getPositionOfCommit(mainParent.index());
                        edges.add(new SimpleEdge(pos, node.getIndexColor()));
                    }
                    if (secondParent != null) {
                        int pos = nextRow.getPositionOfCommit(secondParent.index());
                        edges.add(new SimpleEdge(pos, row.getAdditionColor()));
                    }
                } else {
                    int pos = nextRow.getPositionOfCommit(node.getIndexCommit());
                    edges.add(new SimpleEdge(pos, node.getIndexColor()));
                }

                return edges;
            }
        };
    }
}
