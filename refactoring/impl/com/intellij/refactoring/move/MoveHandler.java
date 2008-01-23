/**
 * created at Nov 26, 2001
 * @author Jeka
 */
package com.intellij.refactoring.move;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.jspJava.JspClass;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.anonymousToInner.AnonymousToInnerHandler;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesImpl;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesHandler;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesUtil;
import com.intellij.refactoring.move.moveInner.MoveInnerImpl;
import com.intellij.refactoring.move.moveInner.MoveInnerToUpperOrMembersHandler;
import com.intellij.refactoring.move.moveInstanceMethod.MoveInstanceMethodHandler;
import com.intellij.refactoring.move.moveMembers.MoveMembersImpl;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MoveHandler implements RefactoringActionHandler {

  public static final String REFACTORING_NAME = RefactoringBundle.message("move.tltle");

  /**
   * called by an Action in AtomicAction when refactoring is invoked from Editor
   */
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    int offset = editor.getCaretModel().getOffset();
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    PsiElement element = file.findElementAt(offset);
    while(true){

      if (element == null) {
        if (file instanceof PsiPlainTextFile) {
          PsiElement[] elements = new PsiElement[]{file};
          if (MoveFilesOrDirectoriesHandler.canMoveFiles(elements)) {
            doMove(project, elements, null, null);
          }
          return;
        }

        String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("the.caret.should.be.positioned.at.the.class.method.or.field.to.be.refactored"));
        CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, null, project);
        return;
      }

      if (tryToMoveElement(element, project, dataContext)) {
        return;
      } else {
        final TextRange range = element.getTextRange();
        if (range != null) {
          int relative = offset - range.getStartOffset();
          final PsiReference reference = element.findReferenceAt(relative);
          if (reference != null &&
              !(reference instanceof PsiJavaCodeReferenceElement &&
                ((PsiJavaCodeReferenceElement)reference).getParent() instanceof PsiAnonymousClass)) {
            final PsiElement refElement = reference.resolve();
            if (refElement != null && tryToMoveElement(refElement, project, dataContext)) return;
          }
        }
      }

      element = element.getParent();
    }
  }

  private static boolean tryToMoveElement(final PsiElement element, final Project project, final DataContext dataContext) {
    if ((element instanceof PsiFile && ((PsiFile)element).getVirtualFile() != null)
        || element instanceof PsiDirectory) {
      final PsiElement targetContainer = (PsiElement)dataContext.getData(DataConstantsEx.TARGET_PSI_ELEMENT);
      MoveFilesOrDirectoriesUtil.doMove(project, new PsiElement[]{element}, targetContainer, null);
      return true;
    } else if (element instanceof PsiField) {
      MoveMembersImpl.doMove(project, new PsiElement[]{element}, null, null);
      return true;
    } else if (element instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)element;
      if (!method.hasModifierProperty(PsiModifier.STATIC)) {
        new MoveInstanceMethodHandler().invoke(project, new PsiElement[]{method}, dataContext);
      }
      else {
        MoveMembersImpl.doMove(project, new PsiElement[]{method}, null, null);
      }
      return true;
    } else if (element instanceof PsiClass) {
      PsiClass aClass = (PsiClass)element;
      final PsiClass containingClass = aClass.getContainingClass();
      if (containingClass != null) { // this is inner class
        FeatureUsageTracker.getInstance().triggerFeatureUsed("refactoring.move.moveInner");
        if (!aClass.hasModifierProperty(PsiModifier.STATIC)) {
          if (containingClass instanceof JspClass) {
            CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, RefactoringBundle.message("move.nonstatic.class.from.jsp.not.supported"), null, project);
            return true;
          }
          MoveInnerImpl.doMove(project, new PsiElement[]{aClass}, null);
        }
        else {
          MoveInnerToUpperOrMembersHandler.SelectInnerOrMembersRefactoringDialog dialog = new MoveInnerToUpperOrMembersHandler.SelectInnerOrMembersRefactoringDialog(aClass, project);
          dialog.show();
          if (dialog.isOK()) {
            final MoveHandlerDelegate moveHandlerDelegate = dialog.getRefactoringHandler();
            if (moveHandlerDelegate != null) {
              moveHandlerDelegate.doMove(project, new PsiElement[] { aClass }, null, null);
            }
          }
        }
        return true;
      }
      if (!(element instanceof PsiAnonymousClass)) {
        MoveClassesOrPackagesImpl.doMove(project, new PsiElement[]{aClass},
                                         (PsiElement)dataContext.getData(DataConstantsEx.TARGET_PSI_ELEMENT), null);
      }
      else {
        new AnonymousToInnerHandler().invoke(project, (PsiAnonymousClass)element);
      }

      return true;
    }

    return false;
  }

  /**
   * called by an Action in AtomicAction
   */
  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    doMove(project, elements, dataContext != null
                              ? (PsiElement)dataContext.getData(DataConstantsEx.TARGET_PSI_ELEMENT)
                              : null, null);
  }

  /**
   * must be invoked in AtomicAction
   */
  public static void doMove(Project project, @NotNull PsiElement[] elements, PsiElement targetContainer, MoveCallback callback) {
    if (elements.length == 0) return;

    for(MoveHandlerDelegate delegate: Extensions.getExtensions(MoveHandlerDelegate.EP_NAME)) {
      if (delegate.canMove(elements, targetContainer)) {
        delegate.doMove(project, elements, targetContainer, callback);
        break;
      }
    }
  }

  /**
   * Performs some extra checks (that canMove does not)
   * May replace some elements with others which actulaly shall be moved (e.g. directory->package)
   */
  @Nullable
  public static PsiElement[] adjustForMove(Project project, final PsiElement[] sourceElements, final PsiElement targetElement) {
    for(MoveHandlerDelegate delegate: Extensions.getExtensions(MoveHandlerDelegate.EP_NAME)) {
      if (delegate.canMove(sourceElements, targetElement)) {
        return delegate.adjustForMove(project, sourceElements, targetElement);
      }
    }
    return sourceElements;
  }

  /**
   * Must be invoked in AtomicAction
   * target container can be null => means that container is not determined yet and must be spacify by the user
   */
  public static boolean canMove(@NotNull PsiElement[] elements, PsiElement targetContainer) {
    for(MoveHandlerDelegate delegate: Extensions.getExtensions(MoveHandlerDelegate.EP_NAME)) {
      if (delegate.canMove(elements, targetContainer)) return true;
    }

    return false;
  }
}
