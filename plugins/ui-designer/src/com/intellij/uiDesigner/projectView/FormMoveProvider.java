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

package com.intellij.uiDesigner.projectView;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.MoveDestination;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.actions.MoveAction;
import com.intellij.refactoring.move.MoveClassesOrPackagesCallback;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesImpl;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class FormMoveProvider implements MoveAction.MoveProvider, RefactoringActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.projectView.FormMoveProvider");

  public boolean isEnabledOnDataContext(DataContext dataContext) {
    Form[] forms = Form.DATA_KEY.getData(dataContext);
    return forms != null && forms.length > 0;
  }

  public RefactoringActionHandler getHandler(DataContext dataContext) {
    return this;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    LOG.debug("invoked FormMoveProvider on file");
  }

  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    Form[] forms = Form.DATA_KEY.getData(dataContext);
    LOG.assertTrue(forms != null);
    PsiClass[] classesToMove = new PsiClass[forms.length];
    PsiFile[] filesToMove = new PsiFile[forms.length];
    for(int i=0; i<forms.length; i++) {
      classesToMove [i] = forms [i].getClassToBind();
      filesToMove [i] = forms [i].getFormFiles() [0];
    }

    final PsiElement initialTargetElement = LangDataKeys.TARGET_PSI_ELEMENT.getData(dataContext);
    MoveClassesOrPackagesImpl.doMove(project, classesToMove, initialTargetElement, new FormMoveCallback(filesToMove, classesToMove));
  }

  private static class FormMoveCallback implements MoveClassesOrPackagesCallback {
    private final PsiClass[] myClassesToMove;
    private final PsiFile[] myFilesToMove;

    public FormMoveCallback(final PsiFile[] filesToMove, final PsiClass[] classesToMove) {
      myClassesToMove = classesToMove;
      myFilesToMove = filesToMove;
    }

    public void refactoringCompleted() {
    }

    public void classesOrPackagesMoved(MoveDestination destination) {
      for(PsiFile file: myFilesToMove) {
        final PsiDirectory psiDirectory;
        try {
          psiDirectory = destination.getTargetDirectory(file);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
          continue;
        }
        moveFormFile(file, psiDirectory);
      }
    }

    public void classesMovedToInner(final PsiClass targetClass) {
      PsiDirectory target = targetClass.getContainingFile().getContainingDirectory();
      for(PsiFile file: myFilesToMove) {
        moveFormFile(file, target);
      }
    }

    private static void moveFormFile(final PsiFile file, final PsiDirectory psiDirectory) {
      new MoveFormsProcessor(file.getProject(), new PsiElement[] { file }, psiDirectory).run();
    }

    @Nullable
    public String getElementsToMoveName() {
      if (myClassesToMove.length == 1) {
        return UIDesignerBundle.message("move.class.and.form.prompt", UsageViewUtil.getLongName(myClassesToMove[0]));
      }
      return UIDesignerBundle.message("move.classes.and.forms.prompt");
    }
  }

  private static class MoveFormsProcessor extends MoveFilesOrDirectoriesProcessor {
    public MoveFormsProcessor(final Project project, final PsiElement[] elements, final PsiDirectory newParent) {
      super(project, elements, newParent, false, false, null, null);
    }

    @Override protected boolean isPreviewUsages(UsageInfo[] usages) {
      return false;
    }
  }
}
