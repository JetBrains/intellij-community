// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.customizeActions;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.lang.properties.PropertiesBundle;
import com.intellij.lang.properties.PropertiesImplUtil;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.ResourceBundleManager;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Dmitry Batkovich
 */
public class DissociateResourceBundleAction extends AnAction {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) {
      return;
    }
    final Collection<ResourceBundle> resourceBundles = extractResourceBundles(e);
    assert resourceBundles.size() > 0;
    dissociate(resourceBundles, project);
  }

  @Override
  public void update(@NotNull final AnActionEvent e) {
    final Collection<ResourceBundle> resourceBundles = extractResourceBundles(e);
    if (!resourceBundles.isEmpty()) {
      final String actionText = resourceBundles.size() == 1 
                                ? PropertiesBundle.message("action.DissociateResourceBundleSingle.text", ContainerUtil.getFirstItem(resourceBundles).getBaseName()) 
                                : PropertiesBundle.message("action.DissociateResourceBundleMultiple.text", resourceBundles.size());
      e.getPresentation().setText(actionText, false);
      e.getPresentation().setVisible(true);
    } else {
      e.getPresentation().setVisible(false);
    }
  }

  public static void dissociate(final Collection<? extends ResourceBundle> resourceBundles, final Project project) {
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
