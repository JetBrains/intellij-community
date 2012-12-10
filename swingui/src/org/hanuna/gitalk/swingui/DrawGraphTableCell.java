package org.hanuna.gitalk.swingui;

import org.hanuna.gitalk.printmodel.PrintCell;
import org.hanuna.gitalk.printmodel.cells.Cell;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author erokhins
 */
public interface DrawGraphTableCell {

    public void draw(Graphics2D g2, PrintCell row);

    @Nullable
    public Cell mouseOver(PrintCell row, int x, int y);
}

