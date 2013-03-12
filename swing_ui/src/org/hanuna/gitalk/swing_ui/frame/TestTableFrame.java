package org.hanuna.gitalk.swing_ui.frame;



import org.hanuna.gitalk.common.OneElementList;
import org.hanuna.gitalk.ui.tables.refs.refs.RefTreeModel;
import org.hanuna.gitalk.ui.tables.refs.refs.RefTreeTableNode;
import org.hanuna.gitalk.swing_ui.render.Print_Parameters;
import org.hanuna.gitalk.swing_ui.render.RefTreeCellRender;
import org.hanuna.gitalk.swing_ui.render.painters.RefPainter;
import org.jdesktop.swingx.JXTreeTable;
import org.jdesktop.swingx.treetable.DefaultTreeTableModel;
import org.jdesktop.swingx.treetable.TreeTableModel;

import javax.swing.*;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;
import java.util.Arrays;

/**
 * @author erokhins
 */
public class TestTableFrame extends JFrame {


    public class Ren extends DefaultTreeCellRenderer {
        private RefTreeTableNode node;
        private final RefPainter refPainter = new RefPainter();
        private final String LONG_STRING = generateLongString(239);


        private String generateLongString(int length) {
            char[] chars = new char[length];
            Arrays.fill(chars, ' ');
            return new String(chars);
        }
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            this.node = (RefTreeTableNode) value;

            if (node.isRefNode()) {
                setText(LONG_STRING);
            } else {
                setText(node.getText());
            }
            return this;
        }

        @Override
        public void paint(Graphics g) {
//            super.paint(g);
            if (node.isRefNode()) {
                refPainter.draw((Graphics2D) g, OneElementList.buildList(node.getRef()), 0);
            }
        }
    }

    public TestTableFrame(RefTreeModel refTreeModel) throws HeadlessException {
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        JPanel mainPanel = new JPanel();
        setMinimumSize(new Dimension(700, 400));

        TreeTableModel treeModel = new DefaultTreeTableModel(refTreeModel.getRootNode()) {
            @Override
            public Class<?> getColumnClass(int i) {
                if (i == 0) {
                    return Boolean.class;
                }
                return Object.class;
            }

            @Override
            public int getColumnCount() {
                return 2;
            }



            @Override
            public int getHierarchicalColumn() {
                return 1;
            }

            @Override
            public boolean isCellEditable(Object node, int column) {
                return column == 0;
            }
        };




        JXTreeTable treeTable = new JXTreeTable(treeModel);
        //treeTable.setDefaultRenderer(MyOb.class, new Render());
        treeTable.packAll();
        treeTable.setRootVisible(false);
        treeTable.expandAll();

        treeTable.getColumnModel().getColumn(0).setMaxWidth(20);

        //treeTable.setCollapsedIcon(null);
        treeTable.setTreeCellRenderer(new RefTreeCellRender());

        treeTable.setRowHeight(Print_Parameters.HEIGHT_CELL);

        treeTable.setLeafIcon(null);
        treeTable.setClosedIcon(null);
        treeTable.setOpenIcon(null);
        treeTable.setToggleClickCount(2);


        setContentPane(new JScrollPane(treeTable));
        pack();
    }


}
