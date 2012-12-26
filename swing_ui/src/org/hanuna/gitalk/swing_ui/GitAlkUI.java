package org.hanuna.gitalk.swing_ui;

import org.hanuna.gitalk.ui_controller.UI_Controller;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;

/**
 * @author erokhins
 */
public class GitAlkUI extends JFrame {
    private final UI_GraphTable graphTable;
    private final UI_RefTable refTable;
    private final UI_Controller controller;


    public GitAlkUI(final UI_Controller ui_controller) {
        this.controller = ui_controller;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setTitle("GitAlk");

        graphTable = new UI_GraphTable(ui_controller);
        final JScrollPane scrollPane = new JScrollPane(graphTable);

        final JTabbedPane tabsTwo = new JTabbedPane(JTabbedPane.TOP);
        tabsTwo.add("graph", scrollPane);

        refTable = new UI_RefTable(ui_controller);
        JScrollPane branches = new JScrollPane(refTable);
        tabsTwo.add("branches", branches);

        tabsTwo.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                if (tabsTwo.getSelectedIndex() == 0) {
                    ui_controller.runUpdateShowBranches();
                }
            }
        });



        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.X_AXIS));

        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.PAGE_AXIS));

        leftPanel.add(Box.createVerticalGlue());

        mainPanel.add(leftPanel);
        mainPanel.add(tabsTwo);
        setContentPane(mainPanel);
        pack();
        Dimension screenDimension = Toolkit.getDefaultToolkit().getScreenSize();
        int height = screenDimension.height * 4 / 5;
        int width = screenDimension.width * 3 / 4;

        setSize(new Dimension(width, height));
        updateLocation();
    }

    public void updateLocation() {
        Dimension screenDimension = Toolkit.getDefaultToolkit().getScreenSize();
        Dimension frameDimension = this.getSize();
        int x = screenDimension.width / 2 - frameDimension.width / 2;
        int y = screenDimension.height / 2 - frameDimension.height / 2;
        setLocation(x, y);
    }

    public void showUi() {
        setVisible(true);
    }



}
