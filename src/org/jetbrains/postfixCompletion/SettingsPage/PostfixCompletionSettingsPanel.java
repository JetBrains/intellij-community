package org.jetbrains.postfixCompletion.SettingsPage;

import com.intellij.openapi.*;
import com.intellij.ui.*;
import com.intellij.ui.table.*;

import javax.swing.*;
import java.awt.*;

public class PostfixCompletionSettingsPanel extends JPanel implements Disposable {
  private final JBTable myTable;

  public PostfixCompletionSettingsPanel() {
    //this.add(new JBTextField() {{ setText("Buu"); }});



    String[] columns = {
      "", "Shortcut", "Description", "Example"
    };



    myTable = new JBTable(new PostfixTemplatesModel(columns));
    myTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myTable.setPreferredScrollableViewportSize(new Dimension(500, myTable.getRowHeight() * 8));
    myTable.getColumn(columns[0]).setPreferredWidth(20);
    myTable.getColumn(columns[1]).setPreferredWidth(80);
    myTable.getColumn(columns[2]).setPreferredWidth(300);
    myTable.getColumn(columns[3]).setPreferredWidth(100);

    myTable.setRowHeight(0, 40);
    myTable.setShowGrid(false);
    myTable.getSelectionModel().setSelectionInterval(0, 0);

    ToolbarDecorator decorator = ToolbarDecorator.createDecorator(myTable)
      .disableAddAction()
      .disableRemoveAction()
      .disableUpDownActions();

    add(decorator.createPanel());
  }

  @Override public void dispose() {

  }

  public void apply() {

  }

  public void reset() {

  }
}
