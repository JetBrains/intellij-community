package com.intellij.refactoring;

import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.ParameterInfo;
import com.intellij.refactoring.changeSignature.ThrownExceptionInfo;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.util.containers.HashSet;

import java.util.Arrays;
import java.util.Set;

/**
 * @author ven
 */
public class ChangeSignaturePropagationTest extends CodeInsightTestCase {
  public void testParamSimple() throws Exception {
    parameterPropagationTest();
  }

  public void testParamWithOverriding() throws Exception {
    parameterPropagationTest();
  }

  public void testExceptionSimple() throws Exception {
    exceptionPropagationTest();
  }

  public void testExceptionWithOverriding() throws Exception {
    exceptionPropagationTest();
  }

  private void parameterPropagationTest() throws Exception {
    PsiMethod method = getPrimaryMethod();
    PsiClass aClass = method.getContainingClass();
    PsiType newParamType = myJavaFacade.getElementFactory().createTypeByFQClassName("java.lang.Class", GlobalSearchScope.allScope(myProject));
    final ParameterInfo[] newParameters = new ParameterInfo[]{new ParameterInfo(-1, "clazz", newParamType, "null")};
    final Set<PsiMethod> methodsToPropagateParameters = new HashSet<PsiMethod>(Arrays.asList(aClass.getMethods()));
    doTest(newParameters, new ThrownExceptionInfo[0], methodsToPropagateParameters, null, method);
  }

  private void exceptionPropagationTest() throws Exception {
    PsiMethod method = getPrimaryMethod();
    PsiClass aClass = method.getContainingClass();
    PsiClassType newExceptionType = myJavaFacade.getElementFactory().createTypeByFQClassName("java.lang.Exception", GlobalSearchScope.allScope(myProject));
    final ThrownExceptionInfo[] newExceptions = new ThrownExceptionInfo[]{new ThrownExceptionInfo(-1, newExceptionType)};
    final Set<PsiMethod> methodsToPropagateExceptions = new HashSet<PsiMethod>(Arrays.asList(aClass.getMethods()));
    doTest(new ParameterInfo[0], newExceptions, null, methodsToPropagateExceptions, method);
  }

  private void doTest(ParameterInfo[] newParameters,
                      final ThrownExceptionInfo[] newExceptions,
                      Set<PsiMethod> methodsToPropagateParameterChanges,
                      Set<PsiMethod> methodsToPropagateExceptionChanges,
                      PsiMethod primaryMethod) throws Exception {
    final String filePath = getBasePath() + getTestName(false) + ".java";
    final PsiType returnType = primaryMethod.getReturnType();
    final CanonicalTypes.Type type = returnType == null ? null : CanonicalTypes.createTypeWrapper(returnType);
    new ChangeSignatureProcessor(myProject, primaryMethod, false, null,
                                 primaryMethod.getName(),
                                 type,
                                 generateParameterInfos(primaryMethod, newParameters),
                                 generateExceptionInfos(primaryMethod, newExceptions),
                                 methodsToPropagateParameterChanges,
                                 methodsToPropagateExceptionChanges).run();
    checkResultByFile(filePath + ".after");
  }

  private PsiMethod getPrimaryMethod() throws Exception {
    final String filePath = getBasePath() + getTestName(false) + ".java";
    configureByFile(filePath);
    final PsiElement targetElement = TargetElementUtilBase.findTargetElement(myEditor, TargetElementUtilBase.ELEMENT_NAME_ACCEPTED);
    assertTrue("<caret> is not on method name", targetElement instanceof PsiMethod);
    return (PsiMethod) targetElement;
  }

  private String getBasePath() {
    return "/refactoring/changeSignaturePropagation/";
  }

  private ParameterInfo[] generateParameterInfos (PsiMethod method, ParameterInfo[] newParameters) {
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    ParameterInfo[] result = new ParameterInfo[parameters.length + newParameters.length];
    for (int i = 0; i < parameters.length; i++) {
      result[i] = new ParameterInfo(i);
    }
    System.arraycopy(newParameters, 0, result, parameters.length, newParameters.length);
    return result;
  }

  private ThrownExceptionInfo[] generateExceptionInfos (PsiMethod method, ThrownExceptionInfo[] newExceptions) {
    final PsiClassType[] exceptions = method.getThrowsList().getReferencedTypes();
    ThrownExceptionInfo[] result = new ThrownExceptionInfo[exceptions.length + newExceptions.length];
    for (int i = 0; i < exceptions.length; i++) {
      result[i] = new ThrownExceptionInfo(i);
    }
    System.arraycopy(newExceptions, 0, result, exceptions.length, newExceptions.length);
    return result;
  }
}
