
package com.intellij.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.find.FindManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

public class ReplacePromptDialog extends DialogWrapper {

  private boolean myIsMultiple;

  public ReplacePromptDialog(boolean isMultipleFiles, String title, Project project) {
    super(project, true);
    myIsMultiple = isMultipleFiles;
    setButtonsAlignment(SwingUtilities.CENTER);
    setTitle(title);
    init();
  }

  protected Action[] createActions(){
    DoAction replaceAction = new DoAction("&Replace",FindManager.PromptResult.OK);
    replaceAction.putValue(DEFAULT_ACTION,Boolean.TRUE);
    if (myIsMultiple){
      return new Action[]{
        replaceAction,
        new DoAction("&Skip",FindManager.PromptResult.SKIP),
        new DoAction("All in This &File",FindManager.PromptResult.ALL_IN_THIS_FILE),
        new DoAction("&All Files", FindManager.PromptResult.ALL_FILES),
        getCancelAction()
      };
    }else{
      return new Action[]{
        replaceAction,
        new DoAction("&Skip",FindManager.PromptResult.SKIP),
        new DoAction("&All",FindManager.PromptResult.ALL),
        getCancelAction()
      };
    }
  }

  public JComponent createNorthPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
    Icon icon = UIManager.getIcon("OptionPane.questionIcon");
    if (icon != null){
      JLabel iconLabel = new JLabel(UIManager.getIcon("OptionPane.questionIcon"));
      panel.add(iconLabel, BorderLayout.WEST);
    }
    JLabel label = new JLabel("Do you want to replace this occurrence?");
    label.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 10));
    label.setForeground(Color.black);
    panel.add(label, BorderLayout.CENTER);
    return panel;
  }

  public JComponent createCenterPanel() {
    return null;
  }

  private class DoAction extends AbstractAction {
    private int myExitCode;

    public DoAction(String name,int exitCode) {
      putValue(Action.NAME, name);
      myExitCode = exitCode;
    }

    public void actionPerformed(ActionEvent e) {
      close(myExitCode);
    }
  }
}

