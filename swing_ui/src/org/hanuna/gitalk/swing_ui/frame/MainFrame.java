package org.hanuna.gitalk.swing_ui.frame;

import org.hanuna.gitalk.swing_ui.UI_Utilities;
import org.hanuna.gitalk.ui.UI_Controller;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * @author erokhins
 */
public class MainFrame extends JFrame {
    private final UI_Controller ui_controller;
    private final UI_GraphTable graphTable;
    private final UI_RefTable refTable;

    private final JPanel topPanel = new JPanel();
    private final JPanel tablePanel = new JPanel();
    private final JPanel mainPanel = new JPanel();


    public MainFrame(final UI_Controller ui_controller) {
        this.ui_controller = ui_controller;
        this.graphTable = new UI_GraphTable(ui_controller);
        this.refTable = new UI_RefTable(ui_controller.getRefsTreeTableModel());
        packElements();
    }

    public UI_GraphTable getGraphTable() {
        return graphTable;
    }


    private void packTables() {
        JScrollPane graphScroll = new JScrollPane(graphTable);
        tablePanel.setLayout(new BoxLayout(tablePanel, BoxLayout.X_AXIS));
        tablePanel.add(graphScroll);

        JScrollPane branchScroll = new JScrollPane(refTable);
        refTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                ui_controller.updateVisibleBranches();
            }
        });
        tablePanel.add(branchScroll);
    }

    private void packTopGraphPanel() {
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.LINE_AXIS));
        topPanel.setMaximumSize(new Dimension(10000, 10));

        JButton hideButton = new JButton("Hide");
        hideButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                ui_controller.hideAll();
            }
        });
        topPanel.add(hideButton);

        JButton showButton = new JButton("Show");
        showButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                ui_controller.showAll();
            }
        });
        topPanel.add(showButton);


        final JCheckBox visibleLongEdges = new JCheckBox("Show full patch", false);
        visibleLongEdges.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                ui_controller.setLongEdgeVisibility(visibleLongEdges.isSelected());
            }
        });
        topPanel.add(visibleLongEdges);

        topPanel.add(Box.createHorizontalGlue());
    }

    private void packMainPanel() {
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

        packTopGraphPanel();
        mainPanel.add(topPanel);
        mainPanel.add(tablePanel);


        setContentPane(mainPanel);
        pack();
    }

    private void packElements() {
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setTitle("GitAlk");

        packTables();
        packMainPanel();

        Dimension screenDimension = Toolkit.getDefaultToolkit().getScreenSize();
        int height = screenDimension.height * 4 / 5;
        int width = screenDimension.width * 3 / 4;

        setSize(new Dimension(width, height));
        UI_Utilities.setCenterLocation(this);
    }

    public void showUi() {
        setVisible(true);
    }

}
