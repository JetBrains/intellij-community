/**
 * created at Nov 12, 2001
 * @author Jeka
 */
package com.intellij.refactoring.move.moveInner;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.refactoring.RefactoringBundle;

public class MoveInnerImpl {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.move.moveInner.MoveInnerImpl");

  public static final String REFACTORING_NAME = RefactoringBundle.message("move.inner.to.upper.level.title");

  public static void doMove(final Project project, PsiElement[] elements, final MoveCallback moveCallback) {
    if (elements.length != 1) return;
    final PsiClass aClass = (PsiClass) elements[0];
    boolean condition = aClass.getParent() instanceof PsiClass;
    LOG.assertTrue(condition);

    if (!aClass.isWritable()) {
      if (!RefactoringMessageUtil.checkReadOnlyStatus(project, aClass)) return;
    }


    final MoveInnerDialog dialog = new MoveInnerDialog(
            project,
            aClass,
            new MoveInnerProcessor(project, moveCallback)
    );
    dialog.show();

  }

  /**
   * must be called in atomic action
   */
  public static PsiElement getTargetContainer(PsiClass innerClass) {
    PsiElement outerClassParent = innerClass.getParent().getParent();
    while (outerClassParent != null) {
      if (outerClassParent instanceof PsiClass && !(outerClassParent instanceof PsiAnonymousClass)) {
        return outerClassParent;
      } else if (outerClassParent instanceof PsiFile) {
        return innerClass.getContainingFile().getContainingDirectory();
      }
      outerClassParent = outerClassParent.getParent();
    }
    // should not happen
    LOG.assertTrue(false);
    return null;
  }
}
