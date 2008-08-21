package com.intellij.refactoring.introduceparameterobject.usageInfo;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.refactoring.base.RefactorJUsageInfo;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.ParameterInfo;
import com.intellij.refactoring.ui.ClassUtil;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SuppressWarnings({"MethodWithTooManyParameters"})
public class MergeMethodArguments extends RefactorJUsageInfo {
  private final PsiMethod method;
  private final boolean myKeepMethodAsDelegate;
  private final List<PsiTypeParameter> typeParams;
  private final String className;
  private final String packageName;
  private final String parameterName;
  private final int[] paramsToMerge;
  private boolean lastParamIsVararg;

  public MergeMethodArguments(PsiMethod method,
                              String className,
                              String packageName,
                              String parameterName,
                              int[] paramsToMerge,
                              List<PsiTypeParameter> typeParams,
                              final boolean keepMethodAsDelegate,
                              final boolean lastParameterIsVararg) {
    super(method);
    this.paramsToMerge = paramsToMerge;
    this.packageName = packageName;
    this.className = className;
    this.parameterName = parameterName;
    this.method = method;
    lastParamIsVararg = lastParameterIsVararg;
    myKeepMethodAsDelegate = keepMethodAsDelegate;
    this.typeParams = new ArrayList<PsiTypeParameter>(typeParams);
  }

  public void fixUsage() throws IncorrectOperationException {
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(method.getProject());
    final PsiClass psiClass = psiFacade.findClass(ClassUtil.createQualifiedName(packageName, className));
    final List<ParameterInfo> parametersInfo = new ArrayList<ParameterInfo>();
    parametersInfo.add(new ParameterInfo(-1, parameterName, new PsiImmediateClassType(psiClass, PsiSubstitutor.EMPTY), null) {
      @Override
      public PsiExpression getValue(final PsiCallExpression expr) throws IncorrectOperationException {
        return psiFacade.getElementFactory().createExpressionFromText(getMergedParam(expr), expr);
      }
    });
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    for (int i = 0; i < parameters.length; i++) {
      if (!isParameterToMerge(i)) {
        parametersInfo.add(new ParameterInfo(i, parameters[i].getName(), parameters[i].getType()));
      }
    }

    new ChangeSignatureProcessor(method.getProject(), method, myKeepMethodAsDelegate, null, method.getName(), method.getReturnType(),
                                 parametersInfo.toArray(new ParameterInfo[parametersInfo.size()])).run();
  }

  private boolean isParameterToMerge(int index) {
    for (int i : paramsToMerge) {
      if (i == index) {
        return true;
      }
    }
    return false;
  }

  private String getMergedParam(PsiCallExpression call) {
    final PsiExpression[] args = call.getArgumentList().getExpressions();
    StringBuffer newExpression = new StringBuffer();
    final String qualifiedName = ClassUtil.createQualifiedName(packageName, className);
    newExpression.append("new " + qualifiedName);
    if (!typeParams.isEmpty()) {
      final JavaResolveResult resolvant = call.resolveMethodGenerics();
      final PsiSubstitutor substitutor = resolvant.getSubstitutor();
      newExpression.append('<');
      final Map<PsiTypeParameter, PsiType> substitutionMap = substitutor.getSubstitutionMap();
      for (PsiTypeParameter typeParameter : typeParams) {
        final PsiType boundType = substitutionMap.get(typeParameter);
        if (boundType != null) {
          newExpression.append(boundType.getCanonicalText());
        }
        else {
          newExpression.append(typeParameter.getName());
        }
      }
      newExpression.append('>');
    }
    newExpression.append('(');
    boolean isFirst = true;
    for (int index : paramsToMerge) {
      if (!isFirst) {
        newExpression.append(", ");
      }
      isFirst = false;
      newExpression.append(args[index].getText());
    }
    if (lastParamIsVararg) {
      final int lastArg = paramsToMerge[paramsToMerge.length - 1];
      for (int i = lastArg + 1; i < args.length; i++) {
        newExpression.append(',');
        newExpression.append(args[i].getText());
      }
    }
    newExpression.append(')');
    return newExpression.toString();
  }
}
