package org.hanuna.gitalk.printmodel;

import org.hanuna.gitalk.printmodel.cells.Cell;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public class SpecialCell {
    private final Cell cell;
    private final int position;
    private final Type type;

    public SpecialCell(@NotNull Cell cell, int position, @NotNull Type type) {
        this.cell = cell;
        this.position = position;
        this.type = type;
    }

    @NotNull
    public Cell getCell() {
        return cell;
    }

    public int getPosition() {
        return position;
    }

    @NotNull
    public Type getType() {
        return type;
    }

    public static enum Type {
        commitNode,
        showNode,
        hideNode
    }
}
