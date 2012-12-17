package org.hanuna.gitalk.swing_ui;

import org.hanuna.gitalk.ui_controller.UI_Controller;
import org.hanuna.gitalk.ui_controller.table_models.CommitCell;
import org.hanuna.gitalk.ui_controller.table_models.GraphCommitCell;
import org.hanuna.gitalk.swing_ui.render.CommitCellRender;

import javax.swing.*;
import java.awt.*;

/**
 * @author erokhins
 */
public class UI_RefTable extends JTable {
    private final UI_Controller ui_controller;

    public UI_RefTable(UI_Controller ui_controller) {
        super(ui_controller.getRefTableModel());
        this.ui_controller = ui_controller;
        prepare();
    }

    private void prepare() {
        setDefaultRenderer(CommitCell.class, new CommitCellRender());

        setRowHeight(GraphCommitCell.HEIGHT_CELL);
        setShowHorizontalLines(false);
        setIntercellSpacing(new Dimension(0, 0));


        getColumnModel().getColumn(0).setMinWidth(24);
        getColumnModel().getColumn(0).setMaxWidth(24);
        getColumnModel().getColumn(1).setPreferredWidth(800);
        getColumnModel().getColumn(2).setMinWidth(80);
        getColumnModel().getColumn(3).setMinWidth(80);

    }

}
