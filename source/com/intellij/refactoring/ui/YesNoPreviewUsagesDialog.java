/**
 * created at Oct 8, 2001
 * @author Jeka
 */
package com.intellij.refactoring.ui;

import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MultiLineLabelUI;

import javax.swing.*;
import java.awt.*;

public class YesNoPreviewUsagesDialog extends DialogWrapper {
  private JCheckBox myCbPreviewResults;
  private boolean myToPreviewUsages;
  private final String myMessage;
  private String myHelpID;

  public YesNoPreviewUsagesDialog(String title, String message, boolean previewUsages,
                                  String helpID, Project project) {
    super(project, false);
    myHelpID = helpID;
    setTitle(title);
    myMessage = message;
    myToPreviewUsages = previewUsages;
    setOKButtonText("&Yes");
    setCancelButtonText("&No");
    setButtonsAlignment(SwingUtilities.CENTER);
    init();
  }

  protected JComponent createNorthPanel() {
    JLabel label = new JLabel(myMessage);
    label.setUI(new MultiLineLabelUI());
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(label, BorderLayout.CENTER);
    Icon icon = UIManager.getIcon("OptionPane.questionIcon");
    if (icon != null) {
      label.setIcon(icon);
      label.setIconTextGap(7);
    }
    return panel;
  }

  protected JComponent createCenterPanel() {
    return null;
  }

  public boolean isPreviewUsages() {
    return myCbPreviewResults.isSelected();
  }

  protected JComponent createSouthPanel() {
    myCbPreviewResults = new JCheckBox("Preview usages to be changed");
    myCbPreviewResults.setSelected(myToPreviewUsages);
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(super.createSouthPanel(), BorderLayout.CENTER);
    myCbPreviewResults.setMnemonic('P');
    panel.add(myCbPreviewResults, BorderLayout.WEST);
    return panel;
  }

  protected Action[] createActions() {
    if(myHelpID != null){
      return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
    }
    else {
      return new Action[]{getOKAction(), getCancelAction()};
    }
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(myHelpID);
  }
}
