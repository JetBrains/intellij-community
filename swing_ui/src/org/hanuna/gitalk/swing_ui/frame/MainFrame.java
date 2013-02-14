package org.hanuna.gitalk.swing_ui.frame;

import org.hanuna.gitalk.swing_ui.UI_Utilities;
import org.hanuna.gitalk.ui.UI_Controller;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
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

    private final JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP);
    private final JPanel leftPanel = new JPanel();
    private final JPanel mainPanel = new JPanel();

    public MainFrame(final UI_Controller ui_controller) {
        this.ui_controller = ui_controller;
        this.graphTable = new UI_GraphTable(ui_controller);
        this.refTable = new UI_RefTable(ui_controller.getRefsTableModel());
        packElements();
    }

    public UI_GraphTable getGraphTable() {
        return graphTable;
    }

    public UI_RefTable getRefTable() {
        return refTable;
    }

    private void packTabs() {
        JScrollPane graphScroll = new JScrollPane(graphTable);
        tabs.add("graph", graphScroll);

        JScrollPane branchScroll = new JScrollPane(refTable);
        tabs.add("branches", branchScroll);

        tabs.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                if (tabs.getSelectedIndex() == 0) {
                    ui_controller.updateVisibleBranches();
                }
            }
        });
    }

    private void packLeftPanel() {
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.PAGE_AXIS));
        leftPanel.setMaximumSize(new Dimension(10, 10000));

        JButton hideButton = new JButton("H");
        hideButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                ui_controller.hideAll();
            }
        });
        leftPanel.add(hideButton);


        JButton addButton = new JButton("A");
        addButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                ui_controller.readNextPart();
            }
        });
        leftPanel.add(addButton);

        leftPanel.add(Box.createVerticalGlue());
    }

    private void packMainPanel() {
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.X_AXIS));
        mainPanel.add(leftPanel);
        mainPanel.add(tabs);

        setContentPane(mainPanel);
        pack();
    }

    private void packElements() {
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setTitle("GitAlk");

        packTabs();
        packLeftPanel();
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
