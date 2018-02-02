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
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.lang.properties.PropertiesImplUtil;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.ResourceBundleManager;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Set;

/**
 * @author Dmitry Batkovich
 */
public class DissociateResourceBundleAction extends AnAction {
  private static final String SINGLE_RB_PRESENTATION_TEXT_TEMPLATE = "Dissociate Resource Bundle '%s'";
  private static final String MULTIPLE_RB_PRESENTATION_TEXT_TEMPLATE = "Dissociate %s Resource Bundles";

  public DissociateResourceBundleAction() {
    super(null, null, AllIcons.FileTypes.Properties);
  }

  @Override
  public void actionPerformed(final AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) {
      return;
    }
    final Collection<ResourceBundle> resourceBundles = extractResourceBundles(e);
    assert resourceBundles.size() > 0;
    dissociate(resourceBundles, project);
  }

  @Override
  public void update(final AnActionEvent e) {
    final Collection<ResourceBundle> resourceBundles = extractResourceBundles(e);
    if (!resourceBundles.isEmpty()) {
      final String actionText = resourceBundles.size() == 1 ?
                                String.format(SINGLE_RB_PRESENTATION_TEXT_TEMPLATE, ContainerUtil.getFirstItem(resourceBundles).getBaseName()) :
                                String.format(MULTIPLE_RB_PRESENTATION_TEXT_TEMPLATE, resourceBundles.size());
      e.getPresentation().setText(actionText, false);
      e.getPresentation().setVisible(true);
    } else {
      e.getPresentation().setVisible(false);
    }
  }

  public static void dissociate(final Collection<ResourceBundle> resourceBundles, final Project project) {
    final Set<PsiFileSystemItem> toUpdateInProjectView = new HashSet<>();
    for (ResourceBundle resourceBundle : resourceBundles) {
      for (final PropertiesFile propertiesFile : resourceBundle.getPropertiesFiles()) {
        PsiDirectory containingDirectory = propertiesFile.getContainingFile().getContainingDirectory();
        if (containingDirectory != null) {
          toUpdateInProjectView.add(containingDirectory);
        }
      }
      ResourceBundleManager.getInstance(project).dissociateResourceBundle(resourceBundle);
    }
    AbstractProjectViewPane currentProjectViewPane = ProjectView.getInstance(project).getCurrentProjectViewPane();
    if (currentProjectViewPane != null) {
      for (PsiFileSystemItem item : toUpdateInProjectView) {
        currentProjectViewPane.updateFrom(item, false, true);
      }
    }
  }

  @NotNull
  private static Collection<ResourceBundle> extractResourceBundles(final AnActionEvent event) {
    final Set<ResourceBundle> targetResourceBundles = new HashSet<>();
    final ResourceBundle[] chosenResourceBundles = event.getData(ResourceBundle.ARRAY_DATA_KEY);
    if (chosenResourceBundles != null) {
      for (ResourceBundle resourceBundle : chosenResourceBundles) {
        if (resourceBundle.getPropertiesFiles().size() > 1) {
          targetResourceBundles.add(resourceBundle);
        }
      }
    }
    final PsiElement[] psiElements = event.getData(LangDataKeys.PSI_ELEMENT_ARRAY);
    if (psiElements != null) {
      for (PsiElement element : psiElements) {
        final PropertiesFile propertiesFile = PropertiesImplUtil.getPropertiesFile(element);
        if (propertiesFile != null) {
          final ResourceBundle bundle = propertiesFile.getResourceBundle();
          if (bundle.getPropertiesFiles().size() > 1) {
            targetResourceBundles.add(bundle);
          }
        }
      }
    }
    return targetResourceBundles;
  }
}
