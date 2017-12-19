/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.lang.properties.create;

import com.intellij.icons.AllIcons;
import com.intellij.lang.properties.PropertiesBundle;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.projectView.ResourceBundleAwareNode;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Batkovich
 */
public class AddNewPropertyFileAction extends AnAction {
  protected AddNewPropertyFileAction() {
    super(PropertiesBundle.message("add.property.files.to.resource.bundle.dialog.action.title"), null, AllIcons.FileTypes.Properties);
  }

  @Override
  public void update(AnActionEvent e) {
    ResourceBundle resourceBundle = getResourceBundle(e);
    e.getPresentation().setEnabledAndVisible(resourceBundle != null && CreateResourceBundleDialogComponent.getResourceBundlePlacementDirectory(resourceBundle) != null);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final ResourceBundle resourceBundle = getResourceBundle(e);
    if (resourceBundle == null) return;
    Project project = e.getProject();
    if (project == null) return;
    new CreateResourceBundleDialogComponent.Dialog(project, null, resourceBundle).show();
  }

  @Nullable
  private static ResourceBundle getResourceBundle(AnActionEvent e) {
    final Navigatable[] data = CommonDataKeys.NAVIGATABLE_ARRAY.getData(e.getDataContext());
    if (data == null || data.length != 1) return null;
    if (!(data[0] instanceof ResourceBundleAwareNode)) return null;
    return ((ResourceBundleAwareNode)data[0]).getResourceBundle();
  }
}