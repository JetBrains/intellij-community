package com.jetbrains.python.refactoring.changeSignature;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.changeSignature.ChangeInfo;
import com.intellij.refactoring.changeSignature.ChangeSignatureUsageProcessor;
import com.intellij.refactoring.changeSignature.ParameterInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.refactoring.PyRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * User : ktisha
 */

public class PyChangeSignatureUsageProcessor implements ChangeSignatureUsageProcessor {

  @Override
  public UsageInfo[] findUsages(ChangeInfo info) {
    if (info instanceof PyChangeInfo) {
      final List<UsageInfo> usages = PyRefactoringUtil.findUsages(((PyChangeInfo)info).getMethod());
      return usages.toArray(new UsageInfo[usages.size()]);
    }
    return UsageInfo.EMPTY_ARRAY;
  }

  @Nullable
  @Override
  public MultiMap<PsiElement, String> findConflicts(ChangeInfo info, Ref<UsageInfo[]> refUsages) {
    final MultiMap<PsiElement, String> conflicts = new MultiMap<PsiElement, String>();
    if (info instanceof PyChangeInfo && info.isNameChanged()) {
      final PyFunction function = ((PyChangeInfo)info).getMethod();
      final PyClass clazz = function.getContainingClass();
      if (clazz != null && clazz.findMethodByName(info.getNewName(), true) != null) {
        conflicts.putValue(function, RefactoringBundle.message("method.0.is.already.defined.in.the.1",
                                                             info.getNewName(),
                                                             "class " + clazz.getQualifiedName()));
      }
    }
    return conflicts;
  }

  @Override
  public boolean processUsage(ChangeInfo changeInfo, UsageInfo usageInfo, boolean beforeMethodChange, UsageInfo[] usages) {
    if (!isPythonUsage(usageInfo)) return false;
    if (!(changeInfo instanceof PyChangeInfo)) return false;
    if (!beforeMethodChange) return false;
    PsiElement element = usageInfo.getElement();
    if (element == null || !(element.getParent() instanceof PyCallExpression)) {
      return false;
    }
    final PyCallExpression call = (PyCallExpression)element.getParent();
    final PyArgumentList argumentList = call.getArgumentList();
    if (argumentList != null) {
      final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(element.getProject());
      StringBuilder builder = getSignature(changeInfo, argumentList);

      final PyExpression newCall =
        elementGenerator.createExpressionFromText(LanguageLevel.forElement(element), builder.toString());
      call.replace(newCall);

      return true;
    }
    return false;
  }

  private StringBuilder getSignature(ChangeInfo changeInfo, PyArgumentList argumentList) {
    StringBuilder builder = new StringBuilder(changeInfo.getNewName() + "(");

    final ParameterInfo[] newParameters = changeInfo.getNewParameters();
    final PyExpression[] arguments = argumentList.getArguments();
    boolean useKeywords = false;
    for (int i = 0; i != newParameters.length; ++i) {
      ParameterInfo info = newParameters[i];

      final int oldIndex = info.getOldIndex();
      if (oldIndex == i && i < arguments.length) {
        addNewParameter(builder, arguments[i], useKeywords, i, info);
      }
      else {
        useKeywords = addOldParameter(builder, newParameters, arguments, useKeywords, i, info, oldIndex);
      }
    }

    builder.append(")");
    return builder;
  }

  private boolean addOldParameter(StringBuilder builder,
                                  ParameterInfo[] newParameters,
                                  PyExpression[] arguments,
                                  boolean useKeywords,
                                  int index, ParameterInfo info, int oldIndex) {
    if (oldIndex != -1 && oldIndex < arguments.length) {
      final PyExpression parameter = arguments[oldIndex];
      appendParameter(builder, index, useKeywords && !(parameter instanceof PyKeywordArgument)?
                                  info.getName() + " = " + parameter.getText() : parameter.getText());
    }
    else if (!((PyParameterInfo)info).getDefaultInSignature()){
      appendParameter(builder, index, info.getDefaultValue());
    }
    else if (index != newParameters.length) {
      useKeywords = true;
    }
    return useKeywords;
  }

  private void addNewParameter(StringBuilder builder, PyExpression argument, boolean useKeywords, int index, ParameterInfo info) {
    if (!(argument instanceof PyKeywordArgument)) {
      appendParameter(builder, index, useKeywords? info.getName() + " = " + argument.getText() : argument.getText());
    }
    else {
      if (info.getName().equals(argument.getName())){
        appendParameter(builder, index, argument.getText());
      }
      else {
        final PyExpression valueExpression = ((PyKeywordArgument)argument).getValueExpression();
        appendParameter(builder, index, valueExpression == null?info.getName():info.getName() + " = " + valueExpression.getText());
      }
    }
  }

  private void appendParameter(StringBuilder builder, int index, String value) {
    if (index != 0)
      builder.append(", ");
    builder.append(value);
  }

  private static boolean isPythonUsage(UsageInfo info) {
    final PsiElement element = info.getElement();
    if (element == null) return false;
    return element.getLanguage() == PythonLanguage.getInstance();
  }


  @Override
  public boolean processPrimaryMethod(ChangeInfo changeInfo) {
    if (changeInfo instanceof PyChangeInfo && changeInfo.getLanguage().is(PythonLanguage.getInstance())) {
      final PyChangeInfo pyChangeInfo = (PyChangeInfo)changeInfo;
      processFunctionDeclaration(pyChangeInfo, pyChangeInfo.getMethod());
      return true;
    }
    return false;
  }

  private static void processFunctionDeclaration(@NotNull PyChangeInfo changeInfo, @NotNull PyFunction function) {
    if (changeInfo.isNameChanged()) {
      updateIdentifier(function.getProject(), function.getNameIdentifier(), changeInfo.getNewName());
    }

    if (changeInfo.isParameterSetOrOrderChanged()) {
      updateParameterList(changeInfo, function);
      fixDoc(function);
    }
  }

  private static void fixDoc(@NotNull PyFunction function) {  //TODO: fix docstring params
    //final PyStringLiteralExpression original = function.getDocStringExpression();
    //if (original == null) return;
  }

  private static void updateIdentifier(@NotNull Project project, @Nullable PsiElement oldIdentifier, @Nullable String newName) {
    if (oldIdentifier == null || StringUtil.isEmpty(newName)) return;

    final PsiElement newIdentifier = PyElementGenerator.getInstance(project).createNameIdentifier(newName).getPsi();
    if (newIdentifier != null) {
      oldIdentifier.replace(newIdentifier);
    }
  }

  private static void updateParameterList(PyChangeInfo changeInfo, PyFunction baseMethod) {
    final PsiElement parameterList = baseMethod.getParameterList();

    final PyParameterInfo[] parameters = changeInfo.getNewParameters();
    StringBuilder builder = new StringBuilder("def foo(");
    for(int i = 0; i != parameters.length; ++i) {
      PyParameterInfo info = parameters[i];
      builder.append(info.getName());
      final String defaultValue = info.getDefaultValue();
      if (defaultValue != null && info.getDefaultInSignature() && !StringUtil.isEmpty(defaultValue)) {
        builder.append(" = ").append(defaultValue);
      }

      if (i != parameters.length-1)
        builder.append(", ");
    }
    builder.append("): pass");

    final PyParameterList parameterList1 =
      PyElementGenerator.getInstance(baseMethod.getProject()).createFromText(LanguageLevel.forElement(baseMethod), PyFunction.class,
                                                                             builder.toString()).getParameterList();
    parameterList.replace(parameterList1);
  }

  @Override
  public boolean shouldPreviewUsages(ChangeInfo changeInfo, UsageInfo[] usages) {
    return false;
  }

  @Override
  public boolean setupDefaultValues(ChangeInfo changeInfo, Ref<UsageInfo[]> refUsages, Project project) {
    return true;
  }
}
