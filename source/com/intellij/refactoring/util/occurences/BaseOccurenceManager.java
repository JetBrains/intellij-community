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
        ArrayList result = new ArrayList();
        for (int i = 0; i < myOccurences.length; i++) {
          PsiExpression occurence = myOccurences[i];
          if(myFilter.isOK(occurence)) { result.add(occurence); }
        }
        if(result.size() > 0) {
          myOccurences = (PsiExpression[]) result.toArray(new PsiExpression[result.size()]);
        } else {
          myOccurences = defaultOccurences();
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

  public static boolean needToDeclareFinal(PsiExpression[] occurrences) {
    PsiElement scopeToDeclare = null;;
    for (int i = 0; i < occurrences.length; i++) {
      PsiExpression occurrence = occurrences[i];
      if(scopeToDeclare == null) {
        scopeToDeclare = occurrence;
      } else {
        scopeToDeclare = PsiTreeUtil.findCommonParent(scopeToDeclare, occurrence);
      }
    }
    if(scopeToDeclare == null) {
      return false;
    }

    for (int i = 0; i < occurrences.length; i++) {
      PsiExpression occurrence = occurrences[i];
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
