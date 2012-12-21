package org.hanuna.gitalk.swing_ui.render.painters;

import org.hanuna.gitalk.graph.graph_elements.GraphElement;
import org.hanuna.gitalk.printmodel.GraphPrintCell;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author erokhins
 */
public interface GraphCellPainter {

    public void draw(Graphics2D g2, GraphPrintCell row);

    @Nullable
    public GraphElement mouseOver(GraphPrintCell row, int x, int y);
}

