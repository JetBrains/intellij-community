package org.hanuna.gitalk.swing_ui.frame;



import org.hanuna.gitalk.swing_ui.frame.refs.RefTreeModel;
import org.jdesktop.swingx.JXTreeTable;
import org.jdesktop.swingx.treetable.DefaultTreeTableModel;
import org.jdesktop.swingx.treetable.TreeTableModel;

import javax.swing.*;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;

/**
 * @author erokhins
 */
public class TestTableFrame extends JFrame {


    public static class Ren extends DefaultTreeCellRenderer {



        @Override
        public void paint(Graphics g) {
            super.paint(g);
            g.drawOval(0,0,5,5);
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

        //treeTable.setCollapsedIcon(null);
        treeTable.setLeafIcon(null);
        treeTable.setClosedIcon(null);
        treeTable.setOpenIcon(null);
        treeTable.setToggleClickCount(2);
        treeTable.setTreeCellRenderer(new Ren());


        setContentPane(new JScrollPane(treeTable));
        pack();
    }


}
