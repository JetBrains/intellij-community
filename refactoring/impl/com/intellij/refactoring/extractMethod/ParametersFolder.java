/*
 * User: anna
 * Date: 23-Jun-2009
 */
package com.intellij.refactoring.extractMethod;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.ParameterTablePanel;
import com.intellij.refactoring.util.duplicates.DuplicatesFinder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ParametersFolder {
  private final Map<PsiVariable, PsiExpression> myExpressions = new HashMap<PsiVariable, PsiExpression>();
  private final Map<PsiVariable, List<PsiExpression>> myMentionedInExpressions = new HashMap<PsiVariable, List<PsiExpression>>();
  private final Set<String> myUsedNames = new HashSet<String>();

  private final Set<PsiVariable> myDeleted = new HashSet<PsiVariable>();


  public void clear() {
    myExpressions.clear();
    myMentionedInExpressions.clear();
    myUsedNames.clear();
    myDeleted.clear();
  }

  public boolean isParameterSafeToDelete(@NotNull ParameterTablePanel.VariableData data, @NotNull LocalSearchScope scope) {
    Next:
    for (PsiReference reference : ReferencesSearch.search(data.variable, scope)) {
      PsiElement expression = reference.getElement();
      while (expression != null) {
        for (PsiExpression psiExpression : myExpressions.values()) {
          if (PsiEquivalenceUtil.areElementsEquivalent(expression, psiExpression)) {
            continue Next;
          }
        }
        expression = PsiTreeUtil.getParentOfType(expression, PsiExpression.class);
      }
      return false;
    }
    final PsiExpression psiExpression = myExpressions.get(data.variable);
    if (psiExpression == null) return true;
    for (PsiVariable variable : myExpressions.keySet()) {
      if (variable != data.variable && !myDeleted.contains(variable)) {
        final PsiExpression expr = myExpressions.get(variable);
        if (expr != null && PsiEquivalenceUtil.areElementsEquivalent(expr, psiExpression)) {
          myDeleted.add(data.variable);
          return true;
        }
      }
    }
    return false;
  }

  public void foldParameterUsagesInBody(@NotNull ParameterTablePanel.VariableData data, @NotNull PsiElement element,
                                              PsiElement[] psiElements) {
    if (myDeleted.contains(data.variable)) return;
    final PsiExpression psiExpression = myExpressions.get(data.variable);
    if (psiExpression == null) return;
    final PsiExpression expression = findEquivalent(psiExpression, element);
    if (expression != null && expression.isValid()) {
      final PsiExpression refExpression =
        JavaPsiFacade.getElementFactory(expression.getProject()).createExpressionFromText(data.variable.getName(), element);
      for (int i = 0, psiElementsLength = psiElements.length; i < psiElementsLength; i++) {
        PsiElement psiElement = psiElements[i];
        if (expression == psiElement) {
          psiElements[i] = expression.replace(refExpression);
          return;
        }
      }
      expression.replace(refExpression);
    }
  }

  public boolean isParameterFoldable(@NotNull ParameterTablePanel.VariableData data,
                                     @NotNull LocalSearchScope scope,
                                     @NotNull final List<? extends PsiVariable> inputVariables) {
    final List<PsiExpression> mentionedInExpressions = getMentionedExpressions(data.variable, scope);
    if (mentionedInExpressions == null) return false;

    int currenRank = 0;
    PsiExpression mostRanked = null;
    for (int i = mentionedInExpressions.size() - 1; i >= 0; i--) {
      PsiExpression expression = mentionedInExpressions.get(i);
      final int r = findUsedVariables(data, inputVariables, expression).size();
      if (currenRank < r) {
        currenRank = r;
        mostRanked = expression;
      }
    }

    if (mostRanked != null) {
      myExpressions.put(data.variable, mostRanked);
      data.type = mostRanked.getType();
      final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(mostRanked.getProject());
      final SuggestedNameInfo nameInfo = codeStyleManager.suggestVariableName(VariableKind.PARAMETER, null, mostRanked, data.type);
      data.name = nameInfo.names[0];
      setUniqueName(data);
    }

    return mostRanked != null;
  }

  private void setUniqueName(ParameterTablePanel.VariableData data) {
    int idx = 1;
    while (myUsedNames.contains(data.name)) {
      data.name += idx;
    }
    myUsedNames.add(data.name);
  }

  private static Set<PsiVariable> findUsedVariables(ParameterTablePanel.VariableData data, final List<? extends PsiVariable> inputVariables,
                                             PsiExpression expression) {
    final Set<PsiVariable> found = new HashSet<PsiVariable>();
    expression.accept(new JavaRecursiveElementVisitor() {
      @Override
      public void visitReferenceExpression(PsiReferenceExpression referenceExpression) {
        super.visitReferenceExpression(referenceExpression);
        PsiElement resolved = referenceExpression.resolve();
        if (resolved instanceof PsiVariable && inputVariables.contains(resolved)) {
          found.add((PsiVariable)resolved);
        }
      }
    });
    found.remove(data.variable);
    return found;
  }

  public boolean isFoldable() {
    return !myExpressions.isEmpty();
  }

  @Nullable
  private List<PsiExpression> getMentionedExpressions(PsiVariable var, LocalSearchScope scope) {
    if (myMentionedInExpressions.containsKey(var)) return myMentionedInExpressions.get(var);
    final PsiElement[] scopeElements = scope.getScope();
    List<PsiExpression> expressions = null;
    for (PsiReference reference : ReferencesSearch.search(var, scope)) {
      PsiElement expression = reference.getElement();
      if (expressions == null) {
        expressions = new ArrayList<PsiExpression>();
        while (expression != null) {
          if (PsiUtil.isAccessedForWriting((PsiExpression)expression)) return null;
          for (PsiElement scopeElement : scopeElements) {
            if (PsiTreeUtil.isAncestor(expression, scopeElement, true)) {
              expression = null;
              break;
            }
          }
          if (expression == null) break;

          if (((PsiExpression)expression).getType() != PsiType.VOID) {
            expressions.add((PsiExpression)expression);
          }
          expression = PsiTreeUtil.getParentOfType(expression, PsiExpression.class);
        }
      }
      else {
        for (Iterator<PsiExpression> iterator = expressions.iterator(); iterator.hasNext();) {
          if (findEquivalent(iterator.next(), expression) == null) {
            iterator.remove();
          }
        }
      }
    }
    myMentionedInExpressions.put(var, expressions);
    return expressions;
  }

  @NotNull
  public String getGeneratedCallArgument(@NotNull ParameterTablePanel.VariableData data) {
    return myExpressions.containsKey(data.variable) ? myExpressions.get(data.variable).getText() : data.name;
  }

  public boolean annotateWithParameter(@NotNull ParameterTablePanel.VariableData data, @NotNull PsiElement element) {
    final PsiExpression psiExpression = myExpressions.get(data.variable);
    if (psiExpression != null) {
      final PsiExpression expression = findEquivalent(psiExpression, element);
      if (expression != null) {
        expression.putUserData(DuplicatesFinder.PARAMETER, Pair.create(data.variable, expression.getType()));
        return true;
      }
    }
    return false;
  }

  @Nullable
  private static PsiExpression findEquivalent(PsiExpression expr, PsiElement element) {
    PsiElement expression = element;
    while (expression  != null) {
      if (PsiEquivalenceUtil.areElementsEquivalent(expression, expr)) {
        return (PsiExpression)expression;
      }
      expression = PsiTreeUtil.getParentOfType(expression, PsiExpression.class);
    }
    return null;
  }

  public boolean wasExcluded(PsiVariable variable) {
    return myDeleted.contains(variable) || (myMentionedInExpressions.containsKey(variable) && myExpressions.get(variable) == null);
  }
}