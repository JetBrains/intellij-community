package com.intellij.refactoring.util.duplicates;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;

import java.util.ArrayList;
import java.util.Map;

/**
 * @author dsl
 */
public final class Match {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.util.duplicates.Match");
  private final PsiElement myMatchStart;
  private final PsiElement myMatchEnd;
  private final Map<PsiVariable, PsiElement> myParameterValues = new HashMap<PsiVariable, PsiElement>();
  private final Map<PsiVariable, ArrayList<PsiElement>> myParameterOccurences = new HashMap<PsiVariable, ArrayList<PsiElement>>();
  private final Map<PsiElement, PsiElement> myDeclarationCorrespondence = new HashMap<PsiElement, PsiElement>();
  private ReturnValue myReturnValue = null;
  private Ref<PsiExpression> myInstanceExpression = null;

  Match(PsiExpression expression) {
    myMatchStart = expression;
    myMatchEnd = expression;
  }

  Match(PsiElement start, PsiElement end) {
    LOG.assertTrue(start.getParent() == end.getParent());
    myMatchStart = start;
    myMatchEnd = end;
  }


  public PsiElement getMatchStart() {
    return myMatchStart;
  }

  public PsiElement getMatchEnd() {
    return myMatchEnd;
  }

  public PsiElement getParameterValue(PsiVariable parameter) {
    return myParameterValues.get(parameter);
  }

  /**
   * Returns either local variable declaration or expression
   * @param outputParameter
   * @return
   */
  public ReturnValue getOutputVariableValue(PsiVariable outputParameter) {
    final PsiElement decl = myDeclarationCorrespondence.get(outputParameter);
    if (decl != null && decl instanceof PsiVariable)  {
      return new VariableReturnValue((PsiVariable) decl);
    }
    final PsiElement parameterValue = getParameterValue(outputParameter);
    if (parameterValue instanceof PsiExpression) {
      return new ExpressionReturnValue((PsiExpression) parameterValue);
    }
    else {
      return null;
    }
  }


  boolean putParameter(PsiVariable parameter, PsiElement value) {
    final PsiElement currentValue = myParameterValues.get(parameter);
    if (currentValue == null) {
      if (!(value instanceof PsiExpression)) return false;
      final PsiType type = ((PsiExpression)value).getType();
      final PsiType parameterType = parameter.getType();
      if (type == null || !parameterType.isAssignableFrom(type)) return false;
      myParameterValues.put(parameter, value);
      final ArrayList<PsiElement> elements = new ArrayList<PsiElement>();
      myParameterOccurences.put(parameter, elements);
      return true;
    }
    else {
      if (!CodeInsightUtil.areElementsEquivalent(currentValue, value)) {
        return false;
      }
      myParameterOccurences.get(parameter).add(value);
      return true;
    }
  }

  public ReturnValue getReturnValue() {
    return myReturnValue;
  }

  boolean registerReturnValue(ReturnValue returnValue) {
    if (myReturnValue == null) {
      myReturnValue = returnValue;
      return true;
    }
    else {
      if (!myReturnValue.isEquivalent(returnValue)) {
        return false;
      }
      else {
        return true;
      }
    }
  }

  boolean registerInstanceExpression(PsiExpression instanceExpression) {
    if (myInstanceExpression == null) {
      myInstanceExpression = Ref.create(instanceExpression);
      return true;
    }
    else {
      if (myInstanceExpression.get() == null) {
        if (instanceExpression instanceof PsiThisExpression) {
          myInstanceExpression.set(instanceExpression);
          return true;
        }
        else return myInstanceExpression != null;
      }
      else {
        if (instanceExpression != null) {
          return CodeInsightUtil.areElementsEquivalent(instanceExpression, myInstanceExpression.get());
        }
        else {
          return myInstanceExpression.get() == null || myInstanceExpression.get() instanceof PsiThisExpression;
        }
      }
    }
  }

  boolean putDeclarationCorrespondence(PsiElement patternDeclaration, PsiElement matchDeclaration) {
    LOG.assertTrue(matchDeclaration != null);
    PsiElement originalValue = myDeclarationCorrespondence.get(patternDeclaration);
    if (originalValue == null) {
      myDeclarationCorrespondence.put(patternDeclaration, matchDeclaration);
      return true;
    }
    else {
      return originalValue == matchDeclaration;
    }
  }

  private void replaceWith(final PsiStatement statement) throws IncorrectOperationException {
    final PsiElement matchStart = getMatchStart();
    final PsiElement matchEnd = getMatchEnd();
    matchStart.getParent().addBefore(statement, matchStart);
    matchStart.getParent().deleteChildRange(matchStart, matchEnd);
  }

  public void replaceByStatement(final PsiMethodCallExpression methodCallExpression,
                                           final PsiVariable outputVariable) throws IncorrectOperationException {
    final PsiStatement statement;
    if (outputVariable != null) {
      statement = getOutputVariableValue(outputVariable).createReplacement(methodCallExpression);
    }
    else if (getReturnValue() != null) {
      statement = getReturnValue().createReplacement(methodCallExpression);
    }
    else {
      final PsiElementFactory elementFactory = methodCallExpression.getManager().getElementFactory();
      PsiExpressionStatement expressionStatement = (PsiExpressionStatement) elementFactory.createStatementFromText("x();", null);
      final CodeStyleManager styleManager = CodeStyleManager.getInstance(methodCallExpression.getManager());
      expressionStatement = (PsiExpressionStatement)styleManager.reformat(expressionStatement);
      expressionStatement.getExpression().replace(methodCallExpression);
      statement = expressionStatement;
    }
    replaceWith(statement);
  }

  public PsiExpression getInstanceExpression() {
    if (myInstanceExpression == null) {
      return null;
    }
    else {
      return myInstanceExpression.get();
    }
  }

  public void replace(final PsiMethodCallExpression methodCallExpression, PsiVariable outputVariable) throws IncorrectOperationException {
    if (getMatchStart() == getMatchEnd() && getMatchStart() instanceof PsiExpression) {
      replaceWithExpression(methodCallExpression);
    }
    else {
      replaceByStatement(methodCallExpression, outputVariable);
    }
  }

  private void replaceWithExpression(final PsiMethodCallExpression methodCallExpression) throws IncorrectOperationException {
    LOG.assertTrue(getMatchStart() == getMatchEnd());
    getMatchStart().replace(methodCallExpression);
  }

  TextRange getTextRange() {
    final TextRange textRange =
      new TextRange(getMatchStart().getTextRange().getStartOffset(),
                    getMatchEnd().getTextRange().getEndOffset());
    return textRange;
  }
}
