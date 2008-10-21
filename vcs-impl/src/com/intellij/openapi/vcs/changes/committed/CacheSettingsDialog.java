package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.VcsBundle;

import javax.swing.*;

/**
 * @author yole
 */
public class CacheSettingsDialog extends DialogWrapper {
  private final CacheSettingsPanel myPanel;

  public CacheSettingsDialog(Project project) {
    super(project, false);
    setTitle(VcsBundle.message("cache.settings.dialog.title"));
    myPanel = new CacheSettingsPanel(project);
    myPanel.reset();
    init();
  }
  protected JComponent createCenterPanel() {
    return myPanel.getPanel();
  }

  protected void doOKAction() {
    try {
      myPanel.apply();
    }
    catch (ConfigurationException e) {
      //ignore
    }
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
