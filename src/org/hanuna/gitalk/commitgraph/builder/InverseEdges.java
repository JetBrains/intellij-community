package org.hanuna.gitalk.commitgraph.builder;

import org.hanuna.gitalk.commitgraph.Edge;
import org.hanuna.gitalk.common.ReadOnlyList;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;

/**
* @author erokhins
*/
class InverseEdges implements ReadOnlyList<Edge> {
    @NotNull
    private static Edge inverse(@NotNull Edge edge) {
        return new Edge(edge.to(), edge.from(), edge.getColorIndex());
    }

    private final ReadOnlyList<Edge> edges;
    InverseEdges(@NotNull ReadOnlyList<Edge> edges) {
        this.edges = edges;
    }

    @Override
    public int size() {
        return edges.size();
    }

    @NotNull
    @Override
    public Edge get(int index) {
        return inverse(edges.get(index));
    }

    @NotNull
    @Override
    public Iterator<Edge> iterator() {
        return new IteratorInverse(edges.iterator());
    }

    private static class IteratorInverse implements Iterator<Edge> {
        private final Iterator<Edge> iterator;

        private IteratorInverse(@NotNull Iterator<Edge> iterator) {
            this.iterator = iterator;
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @NotNull
        @Override
        public Edge next() {
            return inverse(iterator.next());
        }

        @NotNull
        @Override
        public void remove() {
            iterator.remove();
        }
    }
}
