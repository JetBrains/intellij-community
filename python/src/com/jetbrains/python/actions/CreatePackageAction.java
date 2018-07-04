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
package com.jetbrains.python.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeView;
import com.intellij.ide.actions.CreateDirectoryOrPackageHandler;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.ide.util.DirectoryChooserUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.jetbrains.python.PyNames;

/**
 * @author yole
 */
public class CreatePackageAction extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.actions.CreatePackageAction");

  @Override
  public void actionPerformed(AnActionEvent e) {
    final IdeView view = e.getData(LangDataKeys.IDE_VIEW);
    if (view == null) {
      return;
    }
    final Project project = e.getData(CommonDataKeys.PROJECT);
    final PsiDirectory directory = DirectoryChooserUtil.getOrChooseDirectory(view);

    if (directory == null) return;
    final SmartPointerManager pointerManager = SmartPointerManager.getInstance(project);
    final SmartPsiElementPointer<PsiDirectory> directoryPointer = pointerManager.createSmartPsiElementPointer(directory);
    final CreateDirectoryOrPackageHandler validator = new CreateDirectoryOrPackageHandler(project, directory, false, ".") {
      @Override
      protected void createDirectories(String subDirName) {
        super.createDirectories(subDirName);
        PsiFileSystemItem element = getCreatedElement();
        final PsiDirectory restoredDirectory = directoryPointer.getElement();
        if (element instanceof PsiDirectory && restoredDirectory != null) {
          createInitPyInHierarchy((PsiDirectory)element, restoredDirectory);
        }
      }
    };
    Messages.showInputDialog(project, IdeBundle.message("prompt.enter.new.package.name"),
                                                                       IdeBundle.message("title.new.package"),
                                                                       Messages.getQuestionIcon(), "", validator);
    final PsiFileSystemItem result = validator.getCreatedElement();
    if (result != null) {
      view.selectElement(result);
    }
  }

  public static void createInitPyInHierarchy(PsiDirectory created, PsiDirectory ancestor) {
    do {
      createInitPy(created);
      created = created.getParent();
    } while(created != null && !created.equals(ancestor));
  }

  private static void createInitPy(PsiDirectory directory) {
    final FileTemplateManager fileTemplateManager = FileTemplateManager.getInstance(directory.getProject());
    final FileTemplate template = fileTemplateManager.getInternalTemplate("Python Script");
    if (directory.findFile(PyNames.INIT_DOT_PY) != null) {
      return;
    }
    if (template != null) {
      try {
        FileTemplateUtil.createFromTemplate(template, PyNames.INIT_DOT_PY, fileTemplateManager.getDefaultProperties(), directory);
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
    else {
      final PsiFile file = PsiFileFactory.getInstance(directory.getProject()).createFileFromText(PyNames.INIT_DOT_PY, "");
      directory.add(file);
    }
  }

  @Override
  public void update(AnActionEvent e) {
    boolean enabled = isEnabled(e);
    e.getPresentation().setVisible(enabled);
    e.getPresentation().setEnabled(enabled);
  }

  private static boolean isEnabled(AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    final IdeView ideView = e.getData(LangDataKeys.IDE_VIEW);
    if (project == null || ideView == null) {
      return false;
    }
    final PsiDirectory[] directories = ideView.getDirectories();
    if (directories.length == 0) {
      return false;
    }
    return true;
  }
}
