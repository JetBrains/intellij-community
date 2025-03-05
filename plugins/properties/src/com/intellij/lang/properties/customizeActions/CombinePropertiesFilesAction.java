// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.customizeActions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.lang.properties.*;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.editor.ResourceBundleAsVirtualFile;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Dmitry Batkovich
 */
public final class CombinePropertiesFilesAction extends AnAction {
  public CombinePropertiesFilesAction() {
    super(PropertiesBundle.messagePointer("combine.properties.files.title"), AllIcons.FileTypes.Properties);
  }

  @Override
  public void actionPerformed(final @NotNull AnActionEvent e) {
    final List<PropertiesFile> initialPropertiesFiles = getPropertiesFiles(e);
    final List<PropertiesFile> propertiesFiles = initialPropertiesFiles == null ? new ArrayList<>()
                                                                                : new ArrayList<>(initialPropertiesFiles);
    final List<ResourceBundle> resourceBundles = getResourceBundles(e);
    if (resourceBundles != null) {
      for (ResourceBundle bundle : resourceBundles) {
        propertiesFiles.addAll(bundle.getPropertiesFiles());
      }
    }

    final String newBaseName = Messages.showInputDialog(propertiesFiles.get(0).getProject(),
                                                        PropertiesBundle.message("combine.properties.files.prompt.text"),
                                                        PropertiesBundle.message("combine.properties.files.title"),
                                                        Messages.getQuestionIcon(),
                                                        PropertiesUtil.getDefaultBaseName(propertiesFiles),
                                                        new MyInputValidator(propertiesFiles));
    if (newBaseName != null) {
      final Project project = propertiesFiles.get(0).getProject();

      final Set<ResourceBundle> uniqueBundlesToDissociate = new HashSet<>();
      for (PropertiesFile file : propertiesFiles) {
        final ResourceBundle resourceBundle = file.getResourceBundle();
        if (resourceBundle.getPropertiesFiles().size() != 1) {
          uniqueBundlesToDissociate.add(resourceBundle);
        }
      }
      final ResourceBundleManager resourceBundleManager = ResourceBundleManager.getInstance(project);
      for (ResourceBundle resourceBundle : uniqueBundlesToDissociate) {
        resourceBundleManager.dissociateResourceBundle(resourceBundle);
      }

      final ResourceBundle resourceBundle = resourceBundleManager.combineToResourceBundleAndGet(propertiesFiles, newBaseName);
      FileEditorManager.getInstance(project).openFile(new ResourceBundleAsVirtualFile(resourceBundle), true);
      ProjectView.getInstance(project).refresh();
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(final @NotNull AnActionEvent e) {
    final Collection<PropertiesFile> propertiesFiles = getPropertiesFiles(e);
    final List<ResourceBundle> resourceBundles = getResourceBundles(e);
    int elementCount = 0;
    if (propertiesFiles != null) {
      elementCount += propertiesFiles.size();
    }
    if (resourceBundles != null) {
      elementCount += resourceBundles.size();
    }
    e.getPresentation().setEnabledAndVisible(elementCount > 1);
  }

  private static @Nullable List<ResourceBundle> getResourceBundles(@NotNull AnActionEvent e) {
    final ResourceBundle[] resourceBundles = e.getData(ResourceBundle.ARRAY_DATA_KEY);
    return resourceBundles == null ? null : List.of(resourceBundles);
  }

  private static @Nullable List<PropertiesFile> getPropertiesFiles(@NotNull AnActionEvent e) {
    final PsiElement[] psiElements = e.getData(PlatformCoreDataKeys.PSI_ELEMENT_ARRAY);
    if (psiElements == null || psiElements.length == 0) {
      return null;
    }
    final List<PropertiesFile> files = new ArrayList<>(psiElements.length);
    for (PsiElement psiElement : psiElements) {
      final PropertiesFile propertiesFile = PropertiesImplUtil.getPropertiesFile(psiElement);
      if (propertiesFile == null) {
        return null;
      }
      files.add(propertiesFile);
    }
    return files;
  }

  @Override
  public boolean isDumbAware() {
    return true;
  }

  private static final class MyInputValidator implements InputValidatorEx {
    private final List<? extends PropertiesFile> myPropertiesFiles;

    private MyInputValidator(final List<? extends PropertiesFile> propertiesFiles) {
      myPropertiesFiles = propertiesFiles;
    }

    @Override
    public boolean checkInput(final String newBaseName) {
      return !newBaseName.isEmpty() && checkBaseName(newBaseName) == null;
    }

    @Override
    public @Nullable String getErrorText(String inputString) {
      return checkInput(inputString) ? null : PropertiesBundle.message("combine.properties.files.validation.error", checkBaseName(inputString).getFailedFile());
    }

    private @Nullable BaseNameError checkBaseName(final String baseNameCandidate) {
      for (PropertiesFile propertiesFile : myPropertiesFiles) {
        final String name = propertiesFile.getVirtualFile().getName();
        if (name.startsWith(baseNameCandidate) &&
            (name.length() == baseNameCandidate.length() ||
             PropertiesUtil.BASE_NAME_BORDER_CHAR.contains(name.charAt(baseNameCandidate.length())))) {
          continue;
        }
        return new BaseNameError(name);
      }
      return null;
    }

    private static final class BaseNameError {

      private final String myFailedFile;

      private BaseNameError(String failedFile) {
        myFailedFile = failedFile;
      }

      public String getFailedFile() {
        return myFailedFile;
      }
    }
  }
}
