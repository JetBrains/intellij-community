package com.intellij.refactoring.util.occurences;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.RefactoringUtil;

import java.util.ArrayList;

/**
 * @author dsl
 */
public abstract class BaseOccurenceManager implements OccurenceManager {
  private PsiExpression[] myOccurences = null;
  private PsiElement myAnchorStatement = null;
  protected final OccurenceFilter myFilter;

  public BaseOccurenceManager(OccurenceFilter filter) {
    myFilter = filter;
  }

  public PsiExpression[] getOccurences() {
    if(myOccurences == null) {
      myOccurences = findOccurences();

      if(myFilter != null) {
        ArrayList<PsiExpression> result = new ArrayList<PsiExpression>();
        for (PsiExpression occurence : myOccurences) {
          if (myFilter.isOK(occurence)) result.add(occurence);
        }
        if (result.isEmpty()) {
          myOccurences = defaultOccurences();
        }
        else {
          myOccurences = result.toArray(new PsiExpression[result.size()]);
        }
      }

      if (getAnchorStatementForAll() == null) {
        myOccurences = defaultOccurences();
      }
    }
    return myOccurences;
  }

  protected abstract PsiExpression[] defaultOccurences();

  protected abstract PsiExpression[] findOccurences();

  public boolean isInFinalContext() {
    return needToDeclareFinal(myOccurences);
  }

  public PsiElement getAnchorStatementForAll() {
    if(myAnchorStatement == null) {
      myAnchorStatement = getAnchorStatementForAllInScope(null);
    }
    return myAnchorStatement;

  }
  public PsiElement getAnchorStatementForAllInScope(PsiElement scope) {
    return RefactoringUtil.getAnchorElementForMultipleExpressions(myOccurences, scope);
  }

  private static boolean needToDeclareFinal(PsiExpression[] occurrences) {
    PsiElement scopeToDeclare = null;
    for (PsiExpression occurrence : occurrences) {
      if (scopeToDeclare == null) {
        scopeToDeclare = occurrence;
      }
      else {
        scopeToDeclare = PsiTreeUtil.findCommonParent(scopeToDeclare, occurrence);
      }
    }
    if(scopeToDeclare == null) {
      return false;
    }

    for (PsiExpression occurrence : occurrences) {
      PsiElement parent = occurrence;
      while (!parent.equals(scopeToDeclare)) {
        parent = parent.getParent();
        if (parent instanceof PsiClass) {
          return true;
        }
      }
    }
    return false;
  }
}
