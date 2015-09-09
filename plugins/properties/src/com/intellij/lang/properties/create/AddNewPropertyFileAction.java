/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lang.properties.create;

import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.lang.properties.PropertiesBundle;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.projectView.CustomResourceBundlePropertiesFileNode;
import com.intellij.lang.properties.projectView.ResourceBundleNode;
import com.intellij.lang.properties.psi.impl.PropertiesFileImpl;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Batkovich
 */
public class AddNewPropertyFileAction extends AnAction {
  private final static Logger LOG = Logger.getInstance(AddNewPropertyFileAction.class);

  protected AddNewPropertyFileAction() {
    super(PropertiesBundle.message("add.property.files.to.resource.bundle.dialog.action.title"), null, AllIcons.FileTypes.Properties);
  }

  @Override
  public void update(AnActionEvent e) {
    final Navigatable[] data = CommonDataKeys.NAVIGATABLE_ARRAY.getData(e.getDataContext());
    if (data != null && data.length == 1) {
      if (data[0] instanceof ResourceBundleNode || data[0] instanceof CustomResourceBundlePropertiesFileNode) {
        final ResourceBundle resourceBundle = (ResourceBundle)((ProjectViewNode)data[0]).getValue();
        LOG.assertTrue(resourceBundle != null);
        if (CreateResourceBundleDialogComponent.getResourceBundlePlacementDirectory(resourceBundle) != null) {
          e.getPresentation().setEnabledAndVisible(true);
          return;
        }
      }
    }
    e.getPresentation().setEnabledAndVisible(false);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final ResourceBundle resourceBundle = getResourceBundle(e);
    new CreateResourceBundleDialogComponent.Dialog(e.getProject(), null, resourceBundle).show();
  }

  @NotNull
  private static ResourceBundle getResourceBundle(AnActionEvent e) {
    final Navigatable[] data = CommonDataKeys.NAVIGATABLE_ARRAY.getData(e.getDataContext());
    LOG.assertTrue(data != null &&
                   data.length == 1 &&
                   (data[0] instanceof ResourceBundleNode || data[0] instanceof CustomResourceBundlePropertiesFileNode));

    final Object value = ((ProjectViewNode)data[0]).getValue();
    LOG.assertTrue(value != null);
    return (ResourceBundle)value;
  }
}