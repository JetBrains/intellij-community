package com.intellij.refactoring.introduceVariable;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiVariable;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.refactoring.util.occurences.ExpressionOccurenceManager;
import com.intellij.util.containers.HashSet;

import java.util.ArrayList;

public class InputValidator implements IntroduceVariableBase.Validator {
  private final Project myProject;
  private final PsiElement myAnchorStatementIfAll;
  private final PsiElement myAnchorStatement;
  private final ExpressionOccurenceManager myOccurenceManager;
  private IntroduceVariableBase myIntroduceVariableBase;

  public boolean isOK(IntroduceVariableSettings settings) {
    String name = settings.getEnteredName();
    final PsiElement anchor;
    final boolean replaceAllOccurrences = settings.isReplaceAllOccurrences();
    if (replaceAllOccurrences) {
      anchor = myAnchorStatementIfAll;
    } else {
      anchor = myAnchorStatement;
    }
    final PsiElement scope = anchor.getParent();
    if(scope == null) return true;
    final ArrayList<String> conflicts = new ArrayList<String>();
    final HashSet<PsiVariable> reportedVariables = new HashSet<PsiVariable>();
    RenameUtil.CollidingVariableVisitor visitor = new RenameUtil.CollidingVariableVisitor() {
      public void visitCollidingElement(PsiVariable collidingVariable) {
        if (collidingVariable instanceof PsiField) return;
        if (!reportedVariables.contains(collidingVariable)) {
          reportedVariables.add(collidingVariable);
          String message = RefactoringBundle.message("introduced.variable.will.conflict.with.0", RefactoringUIUtil.getDescription(collidingVariable, true));
          conflicts.add(message);
        }
      }
    };
    RenameUtil.visitLocalsCollisions(anchor, name, scope, anchor, visitor);
    if (replaceAllOccurrences) {
      final PsiExpression[] occurences = myOccurenceManager.getOccurences();
      for (PsiExpression occurence : occurences) {
        IntroduceVariableBase.checkInLoopCondition(occurence, conflicts);
      }
    } else {
      IntroduceVariableBase.checkInLoopCondition(myOccurenceManager.getMainOccurence(), conflicts);
    }

    if (conflicts.size() > 0) {
      return myIntroduceVariableBase.reportConflicts(conflicts, myProject);
    } else {
      return true;
    }
  }


  public InputValidator(final IntroduceVariableBase introduceVariableBase,
                        Project project,
                        PsiElement anchorStatementIfAll,
                        PsiElement anchorStatement,
                        ExpressionOccurenceManager occurenceManager) {
    myIntroduceVariableBase = introduceVariableBase;
    myProject = project;
    myAnchorStatementIfAll = anchorStatementIfAll;
    myAnchorStatement = anchorStatement;
    myOccurenceManager = occurenceManager;
  }
}
