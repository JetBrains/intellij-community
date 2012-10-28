package org.hanuna.gitalk.commitgraph.builder;

import org.hanuna.gitalk.commitgraph.Edge;
import org.hanuna.gitalk.common.ReadOnlyList;

import java.util.Iterator;

/**
* @author erokhins
*/
class InverseEdges implements ReadOnlyList<Edge> {
    private static Edge inverse(Edge edge) {
        return new Edge(edge.to(), edge.from(), edge.getColorIndex());
    }

    private final ReadOnlyList<Edge> edges;
    InverseEdges(ReadOnlyList<Edge> edges) {
        this.edges = edges;
    }

    @Override
    public int size() {
        return edges.size();
    }

    @Override
    public Edge get(int index) {
        return inverse(edges.get(index));
    }

    @Override
    public Iterator<Edge> iterator() {
        return new IteratorInverse(edges.iterator());
    }

    private static class IteratorInverse implements Iterator<Edge> {
        private final Iterator<Edge> iterator;

        private IteratorInverse(Iterator<Edge> iterator) {
            this.iterator = iterator;
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public Edge next() {
            return inverse(iterator.next());
        }

        @Override
        public void remove() {
            iterator.remove();
        }
    }
}
