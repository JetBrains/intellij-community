package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;

import javax.swing.*;

/**
 * @author yole
 */
public class CacheSettingsDialog extends DialogWrapper {
  private JSpinner myCountSpinner;
  private JPanel myTopPanel;

  public CacheSettingsDialog(Project project) {
    super(project, false);
    setTitle("VCS History Cache Settings");
    myCountSpinner.setModel(new SpinnerNumberModel(500, 1, 100000, 10));
    init();
  }

  protected JComponent createCenterPanel() {
    return myTopPanel;
  }

  public int getInitialCount() {
    return ((SpinnerNumberModel) myCountSpinner.getModel()).getNumber().intValue();
  }
}
