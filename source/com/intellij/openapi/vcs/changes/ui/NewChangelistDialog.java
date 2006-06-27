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

  public NewChangelistDialog(Project project) {
    super(project, true);
    myPanel = new EditChangelistPanel();
    setTitle(VcsBundle.message("changes.dialog.newchangelist.title"));
    init();
  }

  protected JComponent createCenterPanel() {
    return myPanel.getContent();
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
}
