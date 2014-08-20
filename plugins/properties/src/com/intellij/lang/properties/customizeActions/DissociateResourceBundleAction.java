/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.lang.properties.customizeActions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.lang.properties.PropertiesImplUtil;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.ResourceBundleManager;
import com.intellij.lang.properties.editor.ResourceBundleAsVirtualFile;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Batkovich
 */
public class DissociateResourceBundleAction extends AnAction {
  private static final String PRESENTATION_TEXT_TEMPLATE = "Dissociate Resource Bundle '%s'";

  public DissociateResourceBundleAction() {
    super(null, null, AllIcons.FileTypes.Properties);
  }

  @Override
  public void actionPerformed(final AnActionEvent e) {
    final ResourceBundle resourceBundle = extractResourceBundle(e);
    assert resourceBundle != null;
    final Project project = resourceBundle.getProject();
    final FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
    fileEditorManager.closeFile(new ResourceBundleAsVirtualFile(resourceBundle));
    for (final PropertiesFile propertiesFile : resourceBundle.getPropertiesFiles()) {
      fileEditorManager.closeFile(propertiesFile.getVirtualFile());
    }
    ResourceBundleManager.getInstance(e.getProject()).dissociateResourceBundle(resourceBundle);
    ProjectView.getInstance(project).refresh();
  }

  @Override
  public void update(final AnActionEvent e) {
    final ResourceBundle resourceBundle = extractResourceBundle(e);
    if (resourceBundle != null) {
      e.getPresentation().setText(String.format(PRESENTATION_TEXT_TEMPLATE, resourceBundle.getBaseName()), false);
      e.getPresentation().setVisible(true);
    } else {
      e.getPresentation().setVisible(false);
    }
  }

  @Nullable
  private static ResourceBundle extractResourceBundle(final AnActionEvent event) {
    final ResourceBundle[] data = event.getData(ResourceBundle.ARRAY_DATA_KEY);
    if (data != null && data.length == 1 && data[0].getPropertiesFiles().size() > 1) {
      return data[0];
    }
    final PropertiesFile propertiesFile = PropertiesImplUtil.getPropertiesFile(event.getData(PlatformDataKeys.PSI_FILE));
    if (propertiesFile == null) {
      return null;
    }
    final ResourceBundle resourceBundle = propertiesFile.getResourceBundle();
    return resourceBundle.getPropertiesFiles().size() > 1 ? resourceBundle : null;
  }
}
