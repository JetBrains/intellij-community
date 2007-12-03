package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.ui.DocumentAdapter;

import javax.swing.*;
import javax.swing.event.DocumentEvent;

/**
 * @author max
 */
public class NewChangelistDialog extends DialogWrapper {
  private EditChangelistPanel myPanel;
  private JPanel myTopPanel;
  private JLabel myErrorLabel;
  private JCheckBox myMakeActiveCheckBox;
  private final Project myProject;

  public NewChangelistDialog(Project project) {
    super(project, true);
    myProject = project;
    setTitle(VcsBundle.message("changes.dialog.newchangelist.title"));
    init();
    myPanel.addNameDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        updateControls();
      }
    });
  }

  private void updateControls() {
    if (ChangeListManager.getInstance(myProject).findChangeList(getName()) != null) {
      setOKActionEnabled(false);
      myErrorLabel.setText(VcsBundle.message("new.changelist.duplicate.name.error"));
    }
    else {
      setOKActionEnabled(true);
      myErrorLabel.setText(" ");
    }
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
