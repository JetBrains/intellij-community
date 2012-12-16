package org.hanuna.gitalk.swing_ui.render.painters;

import org.hanuna.gitalk.graph.graph_elements.GraphElement;
import org.hanuna.gitalk.printmodel.PrintCell;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author erokhins
 */
public interface GraphTableCellPainter {

    public void draw(Graphics2D g2, PrintCell row);

    @Nullable
    public GraphElement mouseOver(PrintCell row, int x, int y);
}

