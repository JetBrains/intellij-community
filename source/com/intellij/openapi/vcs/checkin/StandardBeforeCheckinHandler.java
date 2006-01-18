package com.intellij.openapi.vcs.checkin;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class StandardBeforeCheckinHandler extends CheckinHandler {

  protected final Project myProject;


  public StandardBeforeCheckinHandler(final Project project) {
    myProject = project;
  }

  @Nullable
  public RefreshableOnComponent getConfigurationPanel() {
    final JCheckBox optimizeBox = new JCheckBox(VcsBundle.message("checkbox.checkin.options.optimize.imports"));
    final JCheckBox reformatBox = new JCheckBox(VcsBundle.message("checkbox.checkin.options.reformat.code"));

    return new RefreshableOnComponent() {
      public JComponent getComponent() {
        final JPanel panel = new JPanel(new GridLayout(2, 0));
        panel.add(optimizeBox);
        panel.add(reformatBox);
        return panel;
      }

      public void refresh() {
      }

      public void saveState() {
        getSettings().OPTIMIZE_IMPORTS_BEFORE_PROJECT_COMMIT = optimizeBox.isSelected();
        getSettings().REFORMAT_BEFORE_PROJECT_COMMIT = reformatBox.isSelected();
      }

      public void restoreState() {
        optimizeBox.setSelected(getSettings().OPTIMIZE_IMPORTS_BEFORE_PROJECT_COMMIT);
        reformatBox.setSelected(getSettings().REFORMAT_BEFORE_PROJECT_COMMIT);
      }
    };
  }

  protected VcsConfiguration getSettings() {
    return VcsConfiguration.getInstance(myProject);
  }

}
