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

package com.intellij.uiDesigner.actions;

import com.intellij.ide.IdeView;
import com.intellij.ide.actions.CreateElementActionBase;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiNameHelper;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import javax.swing.*;
import java.io.IOException;

/**
 * @author yole
 */
public abstract class AbstractCreateFormAction extends CreateElementActionBase implements DumbAware {
  public AbstractCreateFormAction(String text, String description, Icon icon) {
    super(text, description, icon);
  }

  public void update(final AnActionEvent e) {
    super.update(e);
    final Project project = e.getData(CommonDataKeys.PROJECT);
    final Presentation presentation = e.getPresentation();
    if (presentation.isEnabled()) {
      final IdeView view = e.getData(LangDataKeys.IDE_VIEW);
      if (view != null) {
        final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
        final PsiDirectory[] dirs = view.getDirectories();
        for (final PsiDirectory dir : dirs) {
          if (projectFileIndex.isUnderSourceRootOfType(dir.getVirtualFile(), JavaModuleSourceRootTypes.SOURCES) && JavaDirectoryService.getInstance().getPackage(dir) != null) {
            return;
          }
        }
      }

      presentation.setEnabled(false);
      presentation.setVisible(false);
    }
  }

  protected String createFormBody(@Nullable String fqn, @NonNls String formName, String layoutManager) throws IncorrectOperationException {
    String s;
    try {
      s = FileUtil.loadTextAndClose(getClass().getResourceAsStream(formName));
    }
    catch (IOException e) {
      throw new IncorrectOperationException(UIDesignerBundle.message("error.cannot.read", formName), (Throwable)e);
    }

    if (fqn != null) {
      s = StringUtil.replace(s, "$CLASS$", fqn);
    }
    else {
      s = StringUtil.replace(s, "bind-to-class=\"$CLASS$\"", "");
    }

    s = StringUtil.replace(s, "$LAYOUT$", layoutManager);

    return StringUtil.convertLineSeparators(s);
  }

  protected String getActionName(final PsiDirectory directory, final String newName) {
    return UIDesignerBundle.message("progress.creating.class", JavaDirectoryService.getInstance().getPackage(directory).getQualifiedName(), newName);
  }

  protected class JavaNameValidator extends MyInputValidator {
    private final Project myProject;

    public JavaNameValidator(Project project, PsiDirectory directory) {
      super(project, directory);
      myProject = project;
    }

    @Override
    public boolean checkInput(String inputString) {
      return inputString.length() > 0 && PsiNameHelper.getInstance(myProject).isQualifiedName(inputString);
    }
  }
}