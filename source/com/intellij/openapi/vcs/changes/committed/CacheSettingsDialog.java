package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.VcsBundle;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

/**
 * @author yole
 */
public class CacheSettingsDialog extends DialogWrapper {
  private Project myProject;
  private JSpinner myCountSpinner;
  private JPanel myTopPanel;
  private JSpinner myRefreshSpinner;
  private JCheckBox myRefreshCheckbox;

  public CacheSettingsDialog(Project project) {
    super(project, false);
    myProject = project;
    setTitle(VcsBundle.message("cache.settings.dialog.title"));
    final CommittedChangesCache.State state = CommittedChangesCache.getInstance(project).getState();
    myCountSpinner.setModel(new SpinnerNumberModel(state.getInitialCount(), 1, 100000, 10));
    myRefreshSpinner.setModel(new SpinnerNumberModel(state.getRefreshInterval(), 1, 60*24, 1));
    myRefreshCheckbox.setSelected(state.isRefreshEnabled());
    init();
    myRefreshCheckbox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateControls();
      }
    });
    updateControls();
  }

  private void updateControls() {
    myRefreshSpinner.setEnabled(myRefreshCheckbox.isSelected());
  }

  protected JComponent createCenterPanel() {
    return myTopPanel;
  }

  protected void doOKAction() {
    final CommittedChangesCache.State state = new CommittedChangesCache.State();
    state.setInitialCount(((SpinnerNumberModel) myCountSpinner.getModel()).getNumber().intValue());
    state.setRefreshInterval(((SpinnerNumberModel) myRefreshSpinner.getModel()).getNumber().intValue());
    state.setRefreshEnabled(myRefreshCheckbox.isSelected());
    CommittedChangesCache.getInstance(myProject).loadState(state);
    super.doOKAction();
  }

  public static boolean showSettingsDialog(final Project project) {
    CacheSettingsDialog dialog = new CacheSettingsDialog(project);
    dialog.show();
    if (!dialog.isOK()) {
      return false;
    }
    return true;
  }
}
