package org.hanuna.gitalk.swing_ui.render;

import org.hanuna.gitalk.common.OneElementList;
import org.hanuna.gitalk.swing_ui.render.painters.RefPainter;
import org.hanuna.gitalk.ui.tables.refs.refs.RefTreeTableNode;

import javax.swing.*;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;
import java.awt.image.BufferedImage;

import static org.hanuna.gitalk.swing_ui.render.Print_Parameters.HEIGHT_CELL;

/**
* @author erokhins
*/
public class RefTreeCellRender implements TreeCellRenderer {
    private final RefPainter refPainter = new RefPainter();
    private RefTreeTableNode node;
    private final JLabel label = new JLabel() {
        @Override
        public void paint(Graphics g) {
            if (node.isRefNode()) {
                BufferedImage image = new BufferedImage(1000, HEIGHT_CELL, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2 = image.createGraphics();
                g2.setBackground(new Color(0,0,0,0));
                refPainter.draw(g2, OneElementList.buildList(node.getRef()), 0);
                g.drawImage(image,0, 0, null);
            } else {
                super.paint(g);
            }
        }
    };

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        this.node = (RefTreeTableNode) value;
        label.setFont(new Font("Arial", Font.BOLD, 14));

        if (node.isRefNode()) {
            label.setText(node.getRef().getName() + "   ");
        } else {
            label.setText(node.getText());
        }
        return label;
    }
}
