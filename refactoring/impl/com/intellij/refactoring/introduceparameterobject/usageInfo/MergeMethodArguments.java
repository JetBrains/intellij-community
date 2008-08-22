package com.intellij.refactoring.introduceparameterobject.usageInfo;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.ParameterInfo;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SuppressWarnings({"MethodWithTooManyParameters"})
public class MergeMethodArguments extends FixableUsageInfo {
  private final PsiMethod method;
  private final boolean myKeepMethodAsDelegate;
  private final List<PsiTypeParameter> typeParams;
  private final String className;
  private final String packageName;
  private final String parameterName;
  private final int[] paramsToMerge;
  private final boolean lastParamIsVararg;

  public MergeMethodArguments(PsiMethod method,
                              String className,
                              String packageName,
                              String parameterName,
                              int[] paramsToMerge,
                              List<PsiTypeParameter> typeParams,
                              final boolean keepMethodAsDelegate) {
    super(method);
    this.paramsToMerge = paramsToMerge;
    this.packageName = packageName;
    this.className = className;
    this.parameterName = parameterName;
    this.method = method;
    lastParamIsVararg = method.isVarArgs();
    myKeepMethodAsDelegate = keepMethodAsDelegate;
    this.typeParams = new ArrayList<PsiTypeParameter>(typeParams);
  }

  public void fixUsage() throws IncorrectOperationException {
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(method.getProject());
    final PsiClass psiClass = psiFacade.findClass(StringUtil.getQualifiedName(packageName, className));
    final List<ParameterInfo> parametersInfo = new ArrayList<ParameterInfo>();
    final PsiMethod deepestSuperMethod = method.findDeepestSuperMethod();
    PsiSubstitutor subst = PsiSubstitutor.EMPTY;
    if (deepestSuperMethod != null) {
      final PsiClass parentClass = deepestSuperMethod.getContainingClass();
      assert psiClass != null;
      final PsiSubstitutor parentSubstitutor =
        TypeConversionUtil.getSuperClassSubstitutor(parentClass, method.getContainingClass(), PsiSubstitutor.EMPTY);
      for (int i1 = 0; i1 < psiClass.getTypeParameters().length; i1++) {
        final PsiTypeParameter typeParameter = psiClass.getTypeParameters()[i1];
        for (PsiTypeParameter parameter : parentClass.getTypeParameters()) {
          if (Comparing.strEqual(typeParameter.getName(), parameter.getName())) {
            subst = subst.put(typeParameter, parentSubstitutor.substitute(
              new PsiImmediateClassType(parameter, PsiSubstitutor.EMPTY)));
            break;
          }
        }
      }
    }
    parametersInfo.add(new ParameterInfo(-1, parameterName, new PsiImmediateClassType(psiClass, subst), null) {
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
    if (call instanceof PsiMethodCallExpression) {
      final PsiReferenceExpression methodExpression = ((PsiMethodCallExpression)call).getMethodExpression();
      if (methodExpression.getQualifierExpression() instanceof PsiSuperExpression){
        return parameterName;
      }
    }
    final PsiExpression[] args = call.getArgumentList().getExpressions();
    StringBuffer newExpression = new StringBuffer();
    final String qualifiedName = StringUtil.getQualifiedName(packageName, className);
    newExpression.append("new ").append(qualifiedName);
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
      newExpression.append(getArgument(args, index));
    }
    if (lastParamIsVararg) {
      final int lastArg = paramsToMerge[paramsToMerge.length - 1];
      for (int i = lastArg + 1; i < args.length; i++) {
        newExpression.append(',');
        newExpression.append(getArgument(args, i));
      }
    }
    newExpression.append(')');
    return newExpression.toString();
  }

  @Nullable
  private String getArgument(PsiExpression[] args, int i) {
    if (i < args.length) {
      return args[i].getText();
    }
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    if (i < parameters.length) return parameters[i].getName();
    return null;
  }
}
