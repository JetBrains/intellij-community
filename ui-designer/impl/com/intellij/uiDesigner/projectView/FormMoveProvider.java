/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 03.08.2006
 * Time: 15:31:45
 */
package com.intellij.uiDesigner.projectView;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.refactoring.MoveDestination;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.actions.MoveAction;
import com.intellij.refactoring.move.MoveClassesOrPackagesCallback;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesImpl;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.uiDesigner.UIDesignerBundle;
import org.jetbrains.annotations.Nullable;

public class FormMoveProvider implements MoveAction.MoveProvider, RefactoringActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.projectView.FormMoveProvider");

  public boolean isEnabledOnDataContext(DataContext dataContext) {
    Form[] forms = (Form[]) dataContext.getData(DataConstantsEx.GUI_DESIGNER_FORM_ARRAY);
    return forms != null && forms.length > 0;
  }

  public RefactoringActionHandler getHandler(DataContext dataContext) {
    return this;
  }

  public void invoke(Project project, Editor editor, PsiFile file, DataContext dataContext) {
    LOG.debug("invoked FormMoveProvider on file");
  }

  public void invoke(Project project, PsiElement[] elements, DataContext dataContext) {
    Form[] forms = (Form[]) dataContext.getData(DataConstantsEx.GUI_DESIGNER_FORM_ARRAY);
    LOG.assertTrue(forms != null);
    PsiClass[] classesToMove = new PsiClass[forms.length];
    PsiFile[] filesToMove = new PsiFile[forms.length];
    for(int i=0; i<forms.length; i++) {
      classesToMove [i] = forms [i].getClassToBind();
      filesToMove [i] = forms [i].getFormFiles() [0];
    }

    final PsiElement initialTargetElement = (PsiElement)dataContext.getData(DataConstantsEx.TARGET_PSI_ELEMENT);
    MoveClassesOrPackagesImpl.doMove(project, classesToMove, initialTargetElement, new FormMoveCallback(filesToMove, classesToMove));
  }

  private static class FormMoveCallback implements MoveClassesOrPackagesCallback {
    private PsiClass[] myClassesToMove;
    private PsiFile[] myFilesToMove;

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
        new MoveFormsProcessor(
          file.getProject(),
          new PsiElement[] { file },
          psiDirectory,
          false,
          false,
          null,
          null
        ).run();
      }
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
    public MoveFormsProcessor(final Project project, final PsiElement[] elements, final PsiDirectory newParent,
                              final boolean searchInComments,
                              final boolean searchInNonJavaFiles,
                              final MoveCallback moveCallback, final Runnable prepareSuccessfulCallback) {
      super(project, elements, newParent, searchInComments, searchInNonJavaFiles, moveCallback, prepareSuccessfulCallback);
    }


    @Override protected boolean isPreviewUsages(UsageInfo[] usages) {
      return false;
    }
  }
}
