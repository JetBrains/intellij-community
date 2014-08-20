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
import com.intellij.lang.properties.PropertiesBundle;
import com.intellij.lang.properties.PropertiesImplUtil;
import com.intellij.lang.properties.PropertiesUtil;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.ResourceBundleManager;
import com.intellij.lang.properties.editor.ResourceBundleAsVirtualFile;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Dmitry Batkovich
 */
public class CombinePropertiesFilesAction extends AnAction {

  public CombinePropertiesFilesAction() {
    super(PropertiesBundle.message("combine.properties.files.title"), null, AllIcons.FileTypes.Properties);
  }

  @Override
  public void actionPerformed(final AnActionEvent e) {
    final List<PropertiesFile> propertiesFiles = getPropertiesFiles(e);
    final String newBaseName = Messages.showInputDialog(propertiesFiles.get(0).getProject(),
                                                        PropertiesBundle.message("combine.properties.files.prompt.text"),
                                                        PropertiesBundle.message("combine.properties.files.title"),
                                                        Messages.getQuestionIcon(),
                                                        PropertiesUtil.getDefaultBaseName(propertiesFiles),
                                                        new MyInputValidator(propertiesFiles));
    if (newBaseName != null) {
      final Project project = propertiesFiles.get(0).getProject();
      ResourceBundleManager.getInstance(project).combineToResourceBundle(propertiesFiles, newBaseName);
      final ResourceBundle resourceBundle = propertiesFiles.get(0).getResourceBundle();
      FileEditorManager.getInstance(project).openFile(new ResourceBundleAsVirtualFile(resourceBundle), true);
      ProjectView.getInstance(project).refresh();
    }
  }

  @Override
  public void update(final AnActionEvent e) {
    final List<PropertiesFile> propertiesFiles = getPropertiesFiles(e);
    boolean isAvailable = propertiesFiles != null && propertiesFiles.size() > 1;
    if (isAvailable) {
      for (PropertiesFile propertiesFile : propertiesFiles) {
        if (propertiesFile.getResourceBundle().getPropertiesFiles().size() != 1) {
          isAvailable = false;
          break;
        }
      }
    }
    e.getPresentation().setVisible(isAvailable);
  }

  @Nullable
  private static List<PropertiesFile> getPropertiesFiles(AnActionEvent e) {
    final PsiElement[] psiElements = e.getData(LangDataKeys.PSI_ELEMENT_ARRAY);
    if (psiElements == null || psiElements.length == 0) {
      return null;
    }
    final List<PropertiesFile> files = new ArrayList<PropertiesFile>(psiElements.length);
    for (PsiElement psiElement : psiElements) {
      if (!(psiElement instanceof PsiFile)) {
        return null;
      }
      final PropertiesFile propertiesFile = PropertiesImplUtil.getPropertiesFile((PsiFile)psiElement);
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

  private static class MyInputValidator implements InputValidatorEx {
    private final List<PropertiesFile> myPropertiesFiles;

    private MyInputValidator(final List<PropertiesFile> propertiesFiles) {
      myPropertiesFiles = propertiesFiles;
    }

    @Override
    public boolean checkInput(final String newBaseName) {
      return !newBaseName.isEmpty() && checkBaseName(newBaseName) == null;
    }

    @Override
    public boolean canClose(final String newBaseName) {
      return true;
    }

    @Nullable
    @Override
    public String getErrorText(String inputString) {
      return checkInput(inputString) ? null : String.format("Base name must be valid for file \'%s\'", checkBaseName(inputString).getFailedFile());
    }

    @Nullable
    private BaseNameError checkBaseName(final String baseNameCandidate) {
      for (PropertiesFile propertiesFile : myPropertiesFiles) {
        final String name = propertiesFile.getVirtualFile().getName();
        if (!name.startsWith(baseNameCandidate) || !PropertiesUtil.BASE_NAME_BORDER_CHAR.contains(name.charAt(baseNameCandidate.length()))) {
          return new BaseNameError(name);
        }
      }
      return null;
    }

    private static class BaseNameError {

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
