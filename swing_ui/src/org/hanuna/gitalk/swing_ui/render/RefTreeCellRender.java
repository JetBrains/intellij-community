package org.hanuna.gitalk.swing_ui.render;

import org.hanuna.gitalk.common.OneElementList;
import org.hanuna.gitalk.swing_ui.frame.TestTableFrame;
import org.hanuna.gitalk.ui.tables.refs.refs.RefTreeTableNode;
import org.hanuna.gitalk.swing_ui.render.painters.RefPainter;

import javax.swing.*;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;

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
                refPainter.draw((Graphics2D) g, OneElementList.buildList(node.getRef()), 0);
            } else {
                super.paint(g);
            }
        }
    };
    private TestTableFrame testTableFrame;

    public RefTreeCellRender(TestTableFrame testTableFrame) {
        this.testTableFrame = testTableFrame;
    }

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        this.node = (RefTreeTableNode) value;
        testTableFrame.setFont(new Font("Arial", Font.BOLD, 14));

        if (node.isRefNode()) {
            label.setText(node.getRef().getName() + "   ");
        } else {
            label.setText(node.getText());
        }
        return label;
    }
}
