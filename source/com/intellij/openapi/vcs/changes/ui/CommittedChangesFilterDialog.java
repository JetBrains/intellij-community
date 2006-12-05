/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 30.11.2006
 * Time: 19:39:02
 */
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.ChangesBrowserSettingsEditor;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

public class CommittedChangesFilterDialog extends DialogWrapper {
  private final ChangesBrowserSettingsEditor myPanel;
  private ChangeBrowserSettings mySettings;

  public CommittedChangesFilterDialog(Project project, ChangesBrowserSettingsEditor panel, ChangeBrowserSettings settings) {
    super(project, false);
    myPanel = panel;
    //noinspection unchecked
    myPanel.setSettings(settings);
    setTitle(VcsBundle.message("browse.changes.filter.title"));
    init();
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return myPanel.getComponent();
  }

  @Override
  protected void doOKAction() {
    mySettings = myPanel.getSettings();
    super.doOKAction();
  }

  public ChangeBrowserSettings getSettings() {
    return mySettings;
  }

  @Override @NonNls
  protected String getDimensionServiceKey() {
    return "AbstractVcsHelper.FilterDialog";
  }
}