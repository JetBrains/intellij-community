/**
 * created at Nov 21, 2001
 * @author Jeka
 */
package com.intellij.refactoring.move.moveMembers;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.util.RefactoringMessageUtil;

import java.util.HashSet;
import java.util.Set;

public class MoveMembersImpl {
  public static final String REFACTORING_NAME = "Move Members";

  /**
   * element should be either not anonymous PsiClass whose members should be moved
   * or PsiMethod of a non-anonymous PsiClass
   * or PsiField of a non-anonymous PsiClass
   * or Inner PsiClass
   */
  public static void doMove(final Project project, PsiElement[] elements, PsiElement targetContainer, MoveCallback moveCallback) {
    if (elements.length == 0) {
      return;
    }
    final PsiClass sourceClass;
    if (elements[0].getParent() instanceof PsiClass) {
      sourceClass = (PsiClass) elements[0].getParent();
    } else {
      return;
    }
    final Set preselectMembers = new HashSet();
    for (int idx = 0; idx < elements.length; idx++) {
      PsiElement element = elements[idx];
      if (!sourceClass.equals(element.getParent())) {
        String message = "Cannot perform the refactoring.\nMembers to be moved should belong to the same class.";
        RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.MOVE_MEMBERS, project);
        return;
      }
      if (element instanceof PsiField) {
        PsiField field = (PsiField) element;
        if (!field.hasModifierProperty(PsiModifier.STATIC)) {
          String fieldName = PsiFormatUtil.formatVariable(
                  field,
                  PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.TYPE_AFTER,
              PsiSubstitutor.EMPTY);
          String message = "Field " + fieldName + " is not static.\n" +
                  REFACTORING_NAME + " refactoring is supported for static members only.";
          RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.MOVE_MEMBERS, project);
          return;
        }
        preselectMembers.add(field);
      } else if (element instanceof PsiMethod) {
        PsiMethod method = (PsiMethod) element;
        String methodName = PsiFormatUtil.formatMethod(
                method,
            PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS,
                PsiFormatUtil.SHOW_TYPE
        );
        if (method.isConstructor()) {
          String message = REFACTORING_NAME + " refactoring cannot be applied to constructors";
          RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.MOVE_MEMBERS, project);
          return;
        }
        if (!method.hasModifierProperty(PsiModifier.STATIC)) {
          String message = "Method " + methodName + " is not static.\n" +
                  REFACTORING_NAME + " refactoring is supported for static members only.";
          RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.MOVE_MEMBERS, project);
          return;
        }
        preselectMembers.add(method);
      } else if (element instanceof PsiClass) {
        PsiClass aClass = (PsiClass) element;
        if (!aClass.hasModifierProperty(PsiModifier.STATIC)) {
          String message = "Inner class " + aClass.getQualifiedName() + " is not static.\n" +
                  REFACTORING_NAME + " refactoring is supported for static members only.";
          RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.MOVE_MEMBERS, project);
          return;
        }
        preselectMembers.add(aClass);
      }
    }

    if (!sourceClass.isWritable()) {
      RefactoringMessageUtil.showReadOnlyElementRefactoringMessage(project, sourceClass);
      return;
    }

    final MoveMembersProcessor callback = new MoveMembersProcessor(project, moveCallback);
    final PsiClass initialTargerClass = targetContainer instanceof PsiClass? (PsiClass) targetContainer : (PsiClass) null;

    MoveMembersDialog dialog = new MoveMembersDialog(
            project,
            sourceClass,
            initialTargerClass,
            preselectMembers,
            callback
    );
    dialog.show();
  }
}
