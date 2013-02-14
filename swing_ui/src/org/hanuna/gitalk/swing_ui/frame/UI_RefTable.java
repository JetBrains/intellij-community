package org.hanuna.gitalk.swing_ui.frame;

import org.hanuna.gitalk.swing_ui.render.CommitCellRender;
import org.hanuna.gitalk.ui.tables.CommitCell;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.TableModel;
import java.awt.*;

import static org.hanuna.gitalk.swing_ui.render.Print_Parameters.HEIGHT_CELL;

/**
 * @author erokhins
 */
public class UI_RefTable extends JTable {

    public UI_RefTable(@NotNull TableModel refsTableModel) {
        super(refsTableModel);
        prepare();
    }

    private void prepare() {
        setDefaultRenderer(CommitCell.class, new CommitCellRender());

        setRowHeight(HEIGHT_CELL);
        setShowHorizontalLines(false);
        setIntercellSpacing(new Dimension(0, 0));


        getColumnModel().getColumn(0).setMinWidth(24);
        getColumnModel().getColumn(0).setMaxWidth(24);
        getColumnModel().getColumn(1).setPreferredWidth(800);
        getColumnModel().getColumn(2).setMinWidth(80);
        getColumnModel().getColumn(3).setMinWidth(80);

    }

}
