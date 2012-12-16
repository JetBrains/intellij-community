package org.hanuna.gitalk.swingui;

import org.hanuna.gitalk.controller.UI_Controller;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * @author erokhins
 */
public class GitAlkUI extends JFrame {
    private final UI_GraphTable table;
    private final UI_Controller controller;


    public GitAlkUI(final UI_Controller ui_controller) {
        this.controller = ui_controller;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setTitle("GitAlk");
        table = new UI_GraphTable(ui_controller);
        final JScrollPane scrollPane = new JScrollPane(table);

        JTabbedPane tabsTwo = new JTabbedPane(JTabbedPane.TOP);
        tabsTwo.add("graph", scrollPane);
        JPanel branches = new JPanel();
        branches.add(new JButton("button!"));
        tabsTwo.add("branches", branches);


        JButton b1 = new JButton("B");
        b1.setPreferredSize(new Dimension(40, 40));
        b1.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                scrollPane.setVisible(! scrollPane.isVisible());
                GitAlkUI.this.validate();
            }
        });

        JButton b2 = new JButton("B2");

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.X_AXIS));

        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.PAGE_AXIS));

        leftPanel.add(b1);
        leftPanel.add(b2);
        leftPanel.add(new  JCheckBox());
        leftPanel.add(Box.createVerticalGlue());

        mainPanel.add(leftPanel);
        //mainPanel.add(Box.createVerticalStrut(2));
        mainPanel.add(tabsTwo);
        setContentPane(mainPanel);
        pack();
    }

    public void showUi() {
        setVisible(true);
    }



}
