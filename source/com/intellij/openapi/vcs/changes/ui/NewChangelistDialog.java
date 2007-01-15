package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.VcsBundle;

import javax.swing.*;

/**
 * @author max
 */
public class NewChangelistDialog extends DialogWrapper {
  private EditChangelistPanel myPanel;
  private JPanel myTopPanel;
  private JCheckBox myMakeActiveCheckBox;

  public NewChangelistDialog(Project project) {
    super(project, true);
    setTitle(VcsBundle.message("changes.dialog.newchangelist.title"));
    init();
  }

  protected JComponent createCenterPanel() {
    return myTopPanel;
  }

  public String getName() {
    return myPanel.getName();
  }

  public String getDescription() {
    return myPanel.getDescription();
  }

  public JComponent getPreferredFocusedComponent() {
    return myPanel.getPrefferedFocusedComponent();
  }

  protected String getDimensionServiceKey() {
    return "VCS.NewChangelistDialog";
  }

  public boolean isNewChangelistActive() {
    return myMakeActiveCheckBox.isSelected();
  }
}
