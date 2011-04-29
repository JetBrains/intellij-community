/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/**
 * @author Alexey
 */
package com.intellij.lang.properties;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.lang.properties.editor.ResourceBundleEditor;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.rename.RenameHandler;
import com.intellij.refactoring.rename.RenameProcessor;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

public class ResourceBundleRenameHandler implements RenameHandler {
  private static final Logger LOG = Logger.getInstance("#" + ResourceBundleRenameHandler.class.getName());

  public boolean isAvailableOnDataContext(DataContext dataContext) {
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return false;
    }
    final ResourceBundle bundle = ResourceBundleUtil.getResourceBundleFromDataContext(dataContext);
    if (bundle == null) {
      return false;
    }

    ResourceBundleEditor editor = ResourceBundleUtil.getEditor(dataContext);
    return (editor == null || editor.getState(FileEditorStateLevel.NAVIGATION).getPropertyName() == null /* user selected non-bundle key element */)
           && bundle.getPropertiesFiles(project).size() > 1;
  }

  public boolean isRenaming(DataContext dataContext) {
    return isAvailableOnDataContext(dataContext);
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    ResourceBundle resourceBundle = ResourceBundleUtil.getResourceBundleFromDataContext(dataContext);

    assert resourceBundle != null;
    Messages.showInputDialog(project,
                             PropertiesBundle.message("rename.bundle.enter.new.resource.bundle.base.name.prompt.text"),
                             PropertiesBundle.message("rename.resource.bundle.dialog.title"),
                             Messages.getQuestionIcon(),
                             resourceBundle.getBaseName(),
                             new MyInputValidator(project, resourceBundle));
  }

  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    invoke(project, null, null, dataContext);
  }

  private static class MyInputValidator implements InputValidator {
    private final Project myProject;
    private final ResourceBundle myResourceBundle;

    public MyInputValidator(final Project project, final ResourceBundle resourceBundle) {
      myProject = project;
      myResourceBundle = resourceBundle;
    }

    public boolean checkInput(String inputString) {
      return inputString.indexOf(File.separatorChar) < 0 && inputString.indexOf('/') < 0;
    }

    public boolean canClose(final String inputString) {
      return doRename(inputString);
    }
    private boolean doRename(final String inputString) {
      final List<PropertiesFile> propertiesFiles = myResourceBundle.getPropertiesFiles(myProject);
      for (PropertiesFile propertiesFile : propertiesFiles) {
        if (!CodeInsightUtilBase.prepareFileForWrite(propertiesFile)) return false;
      }

      RenameProcessor renameProcessor = null;
      String baseName = myResourceBundle.getBaseName();
      for (PropertiesFile propertiesFile : propertiesFiles) {
        final VirtualFile virtualFile = propertiesFile.getVirtualFile();
        if (virtualFile == null) {
          continue;
        }
        final String newName = inputString + virtualFile.getNameWithoutExtension().substring(baseName.length()) + "."
                               + virtualFile.getExtension();
        if (renameProcessor == null) {
          renameProcessor = new RenameProcessor(myProject, propertiesFile, newName, false, false);
          continue;
        }
        renameProcessor.addElement(propertiesFile, newName);
      }
      if (renameProcessor == null) {
        LOG.assertTrue(false);
        return true;
      }
      renameProcessor.setCommandName(PropertiesBundle.message("rename.resource.bundle.dialog.title"));
      renameProcessor.doRun();
      return true;
    }
  }
}