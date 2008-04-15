package com.intellij.refactoring.util.duplicates;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.ParameterInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

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
  private final Map<PsiVariable, PsiType> myChangedParams = new HashMap<PsiVariable, PsiType>();

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
    if (decl instanceof PsiVariable) {
      return new VariableReturnValue((PsiVariable)decl);
    }
    final PsiElement parameterValue = getParameterValue(outputParameter);
    if (parameterValue instanceof PsiExpression) {
      return new ExpressionReturnValue((PsiExpression) parameterValue);
    }
    else {
      return null;
    }
  }


  boolean putParameter(Pair<PsiVariable, PsiType> parameter, PsiElement value) {
    final PsiVariable psiVariable = parameter.first;
    final PsiElement currentValue = myParameterValues.get(psiVariable);
    if (currentValue == null) {
      if (!(value instanceof PsiExpression)) return false;
      final PsiType type = ((PsiExpression)value).getType();
      final PsiType parameterType = parameter.second;
      if (type == null || !parameterType.isAssignableFrom(type)) return false;
      myParameterValues.put(psiVariable, value);
      final ArrayList<PsiElement> elements = new ArrayList<PsiElement>();
      myParameterOccurences.put(psiVariable, elements);
      if (!psiVariable.getType().isAssignableFrom(type)) {
        myChangedParams.put(psiVariable, parameterType);
      }
      return true;
    }
    else {
      if (!PsiEquivalenceUtil.areElementsEquivalent(currentValue, value)) {
        return false;
      }
      myParameterOccurences.get(psiVariable).add(value);
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

  boolean registerInstanceExpression(PsiExpression instanceExpression, final PsiClass contextClass) {
    if (myInstanceExpression == null) {
      if (instanceExpression != null) {
        final PsiType type = instanceExpression.getType();
        if (!(type instanceof PsiClassType)) return false;
        final PsiClass hisClass = ((PsiClassType) type).resolve();
        if (hisClass == null || !InheritanceUtil.isInheritorOrSelf(hisClass, contextClass, true)) return false;
      }
      myInstanceExpression = Ref.create(instanceExpression);
      return true;
    }
    else {
      if (myInstanceExpression.get() == null) {
        myInstanceExpression.set(instanceExpression);

        return true;
      }
      else {
        if (instanceExpression != null) {
          return PsiEquivalenceUtil.areElementsEquivalent(instanceExpression, myInstanceExpression.get());
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
      final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(methodCallExpression.getProject()).getElementFactory();
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

  @Nullable
  public String getChangedSignature(final PsiMethod method, final boolean shouldBeStatic, final String visibilityString) {
    if (!myChangedParams.isEmpty()) {
      @NonNls StringBuffer buffer = new StringBuffer();
      buffer.append(visibilityString);
      if (buffer.length() > 0) {
        buffer.append(" ");
      }
      if (shouldBeStatic) {
        buffer.append("static ");
      }
      final PsiTypeParameterList typeParameterList = method.getTypeParameterList();
      if (typeParameterList != null) {
        buffer.append(typeParameterList.getText());
        buffer.append(" ");
      }

      buffer.append(PsiFormatUtil.formatType(method.getReturnType(), 0, PsiSubstitutor.EMPTY));
      buffer.append(" ");
      buffer.append(method.getName());
      buffer.append("(");
      int count = 0;
      final String INDENT = "    ";
      final ArrayList<ParameterInfo> params = patchParams(method);
      for (ParameterInfo param : params) {
        String typeText = param.getTypeText();
        if (count > 0) {
          buffer.append(",");
        }
        buffer.append("\n");
        buffer.append(INDENT);
        buffer.append(typeText);
        buffer.append(" ");
        buffer.append(param.getName());
        count++;
      }

      if (count > 0) {
        buffer.append("\n");
      }
      buffer.append(")");
      final PsiClassType[] exceptions = method.getThrowsList().getReferencedTypes();
      if (exceptions.length > 0) {
        buffer.append("\n");
        buffer.append("throws\n");
        for (PsiType exception : exceptions) {
          buffer.append(INDENT);
          buffer.append(PsiFormatUtil.formatType(exception, 0, PsiSubstitutor.EMPTY));
          buffer.append("\n");
        }
      }
      return buffer.toString();
    }
    return null;
  }

  public void changeSignature(final PsiMethod psiMethod) {
    final ArrayList<ParameterInfo> newParameters = patchParams(psiMethod);
    final ChangeSignatureProcessor csp = new ChangeSignatureProcessor(psiMethod.getProject(), psiMethod, false, null, psiMethod.getName(),
                                                                      psiMethod.getReturnType(),
                                                                      newParameters.toArray(new ParameterInfo[newParameters.size()]));

    csp.run();
  }

  private ArrayList<ParameterInfo> patchParams(final PsiMethod psiMethod) {
    final ArrayList<ParameterInfo> newParameters = new ArrayList<ParameterInfo>();
    final PsiParameter[] oldParameters = psiMethod.getParameterList().getParameters();
    for (int i = 0; i < oldParameters.length; i++) {
      final PsiParameter oldParameter = oldParameters[i];
      PsiType type = oldParameter.getType();
      for (PsiVariable variable : myChangedParams.keySet()) {
        if (PsiEquivalenceUtil.areElementsEquivalent(variable, oldParameter)) {
          type = myChangedParams.get(variable);
          break;
        }
      }
      newParameters.add(new ParameterInfo(i, oldParameter.getName(), type));
    }
    return newParameters;
  }
}
