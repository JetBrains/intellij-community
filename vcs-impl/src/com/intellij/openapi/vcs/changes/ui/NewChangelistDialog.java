package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.Consumer;

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
  private JPanel myBottomPanel;
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
    myPanel.installSupport(project);
    for (EditChangelistSupport support : Extensions.getExtensions(EditChangelistSupport.EP_NAME, project)) {
      support.addControls(myBottomPanel);
    }
    myMakeActiveCheckBox.setSelected(VcsConfiguration.getInstance(myProject).MAKE_NEW_CHANGELIST_ACTIVE);
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

  @Override
  protected void doOKAction() {
    super.doOKAction();
    VcsConfiguration.getInstance(myProject).MAKE_NEW_CHANGELIST_ACTIVE = myMakeActiveCheckBox.isSelected();
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

  private void createUIComponents() {
    myPanel = new EditChangelistPanel(null, new Consumer<Boolean>() {
      public void consume(final Boolean aBoolean) {
        setOKActionEnabled(aBoolean);
      }
    });
  }
}
