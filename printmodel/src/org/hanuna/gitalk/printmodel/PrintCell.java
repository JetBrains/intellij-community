package org.hanuna.gitalk.printmodel;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author erokhins
 */
public interface PrintCell {
    public int countCell();

    @NotNull
    public List<ShortEdge> getUpEdges();

    @NotNull
    public List<ShortEdge> getDownEdges();

    @NotNull
    public List<SpecialCell> getSpecialCell();
}
