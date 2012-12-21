package org.hanuna.gitalk.printmodel;

import org.hanuna.gitalk.graph.graph_elements.GraphFragment;
import org.hanuna.gitalk.graph.graph_elements.Edge;
import org.hanuna.gitalk.graph.graph_elements.GraphElement;
import org.hanuna.gitalk.graph.graph_elements.Node;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * @author erokhins
 */
public class SelectController {
    private final Set<GraphElement> selectedElements = new HashSet<GraphElement>();

    public void select(@Nullable GraphFragment graphFragment) {
        deselectAll();
        if (graphFragment == null) {
            return;
        }
        selectedElements.add(graphFragment.getUpNode());
        selectedElements.add(graphFragment.getDownNode());
        graphFragment.intermediateWalker(new GraphFragment.GraphElementRunnable() {
            @Override
            public void edgeRun(@NotNull Edge edge) {
                selectedElements.add(edge);
            }

            @Override
            public void nodeRun(@NotNull Node node) {
                selectedElements.add(node);
            }
        });
    }

    public void deselectAll() {
        selectedElements.clear();
    }

    public boolean isSelected(@NotNull GraphElement element) {
        return selectedElements.contains(element);
    }


}
