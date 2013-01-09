package com.jetbrains.python.refactoring.changeSignature;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.changeSignature.ChangeInfo;
import com.intellij.refactoring.changeSignature.ChangeSignatureUsageProcessor;
import com.intellij.refactoring.changeSignature.ParameterInfo;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.refactoring.rename.ResolveSnapshotProvider;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Query;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.documentation.PyDocstringGenerator;
import com.jetbrains.python.documentation.PyDocumentationSettings;
import com.jetbrains.python.editor.PythonDocCommentUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.search.PyOverridingMethodsSearch;
import com.jetbrains.python.refactoring.PyRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * User : ktisha
 */

public class PyChangeSignatureUsageProcessor implements ChangeSignatureUsageProcessor {

  @Override
  public UsageInfo[] findUsages(ChangeInfo info) {
    if (info instanceof PyChangeInfo) {
      final List<UsageInfo> usages = PyRefactoringUtil.findUsages(((PyChangeInfo)info).getMethod(), true);
      final Query<PyFunction> search = PyOverridingMethodsSearch.search(((PyChangeInfo)info).getMethod(), true);
      final Collection<PyFunction> functions = search.findAll();
      for (PyFunction function : functions) {
        usages.add(new UsageInfo(function));
        usages.addAll(PyRefactoringUtil.findUsages(function, true));
      }
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
  public boolean processUsage(final ChangeInfo changeInfo, UsageInfo usageInfo, boolean beforeMethodChange, final UsageInfo[] usages) {
    if (!isPythonUsage(usageInfo)) return false;
    if (!(changeInfo instanceof PyChangeInfo)) return false;
    if (!beforeMethodChange) return false;
    PsiElement element = usageInfo.getElement();

    if (changeInfo.isNameChanged()) {
      final PsiElement method = changeInfo.getMethod();
      RenameUtil.doRenameGenericNamedElement(method, changeInfo.getNewName(), usages, null);
    }
    if (element == null) return false;
    if (element.getParent() instanceof PyCallExpression) {
      final PyCallExpression call = (PyCallExpression)element.getParent();
      final PyArgumentList argumentList = call.getArgumentList();
      if (argumentList != null) {
        final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(element.getProject());
        StringBuilder builder = getSignature(changeInfo, call);

        final PyExpression newCall =
          elementGenerator.createExpressionFromText(LanguageLevel.forElement(element), builder.toString());
        call.replace(newCall);

        return true;
      }
    } else if (element instanceof PyFunction) {
      processFunctionDeclaration((PyChangeInfo)changeInfo, (PyFunction)element);
    }
    return false;
  }

  private StringBuilder getSignature(ChangeInfo changeInfo, PyCallExpression call) {
    final PyArgumentList argumentList = call.getArgumentList();
    final PyExpression callee = call.getCallee();
    String name = callee != null? callee.getText() : changeInfo.getNewName();
    StringBuilder builder = new StringBuilder(name + "(");

    final ParameterInfo[] newParameters = changeInfo.getNewParameters();
    final PyExpression[] arguments = argumentList.getArguments();
    List<String> params = collectParameters(newParameters, arguments);
    builder.append(StringUtil.join(params, ","));
    builder.append(")");
    return builder;
  }

  private List<String> collectParameters(ParameterInfo[] newParameters, PyExpression[] arguments) {
    boolean useKeywords = false;
    boolean isMethod = false;
    boolean isAfterStar = false;
    List<String> params = new ArrayList<String>();
    for (int currentIndex = 0; currentIndex != newParameters.length; ++currentIndex) {
      ParameterInfo info = newParameters[currentIndex];
      if (info.getName().equals("self")) {
        isMethod = true;
        continue;
      }
      int oldIndex = info.getOldIndex();
      oldIndex = isMethod && oldIndex != -1? oldIndex - 1 : oldIndex;
      if (info.getName().equals("*")) {
        isAfterStar = true;
        useKeywords = true;
        continue;
      }
      oldIndex = isAfterStar && oldIndex != -1? oldIndex - 1 : oldIndex;
      if (info.getName().startsWith("**")) {
        addKwArgs(params, arguments, currentIndex);
      }
      else if (info.getName().startsWith("*")) {
        addPositional(params, arguments, currentIndex);
      }
      else if (oldIndex == currentIndex && currentIndex < arguments.length) {
        useKeywords = addOldParameter(params, arguments[currentIndex], useKeywords, info);
      }
      else {
        useKeywords = addNewParameter(params, arguments, useKeywords, info, currentIndex, oldIndex);
      }
    }
    return params;
  }


  private void addPositional(List<String> params, PyExpression[] arguments, int index) {
    for (int i = index; i != arguments.length; ++i) {
      if (!(arguments[i] instanceof PyKeywordArgument))
        params.add(arguments[i].getText());
    }
  }

  private void addKwArgs(List<String> params, PyExpression[] arguments, int index) {
    for (int i = index; i < arguments.length; ++i) {
      if (arguments[i] instanceof PyKeywordArgument)
        params.add(arguments[i].getText());
    }
  }

  private boolean addNewParameter(List<String> params,
                                  PyExpression[] arguments,
                                  boolean useKeywords, ParameterInfo info, int currentIndex, int oldIndex) {
    if (oldIndex != -1 && oldIndex < arguments.length) {
      if (currentIndex < arguments.length) {
        final PyExpression currentParameter = arguments[currentIndex];
        if (currentParameter instanceof PyKeywordArgument && !info.getName().equals(((PyKeywordArgument)currentParameter).getKeyword())) {
          params.add(currentParameter.getText());
        }
        else {
          addOldParameter(params, arguments[oldIndex], useKeywords, info);
        }
      }
      else {
        addOldParameter(params, arguments[oldIndex], useKeywords, info);
      }
    }
    else if (!((PyParameterInfo)info).getDefaultInSignature()){
      params.add(useKeywords? info.getName() + " = " + info.getDefaultValue() : info.getDefaultValue());
    }
    else {
      useKeywords = true;
    }
    return useKeywords;
  }

  private boolean addOldParameter(List<String> params,
                                  PyExpression argument,
                                  boolean useKeywords,
                                  ParameterInfo info) {
    if (!(argument instanceof PyKeywordArgument)) {
      params.add(useKeywords? info.getName() + " = " + argument.getText() : argument.getText());
    }
    else {
      if (info.getName().equals(argument.getName())){
        params.add(argument.getText());
      }
      else {
        final PyExpression valueExpression = ((PyKeywordArgument)argument).getValueExpression();
        params.add(valueExpression == null?info.getName():info.getName() + " = " + valueExpression.getText());
      }
      useKeywords = true;
    }
    return useKeywords;
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
    if (changeInfo.isParameterNamesChanged()) {
      final PyParameterInfo[] parameters = changeInfo.getNewParameters();
      for (int i = 0; i != parameters.length; ++i) {
        PyParameterInfo paramInfo = parameters[i];
        if (paramInfo.getOldIndex() == i) {
          final PyParameter[] oldParameters = function.getParameterList().getParameters();
          final UsageInfo[] usages = RenameUtil.findUsages(oldParameters[i], paramInfo.getName(), true, false, null);
          for (UsageInfo info : usages)
            RenameUtil.rename(info, paramInfo.getName());
        }
      }
    }
    if (changeInfo.isParameterSetOrOrderChanged()) {
      fixDoc(changeInfo, function);
      updateParameterList(changeInfo, function);
    }
    if (changeInfo.isNameChanged()) {
      RenameUtil.doRenameGenericNamedElement(function, changeInfo.getNewName(), UsageInfo.EMPTY_ARRAY, null);
    }
  }

  private static void fixDoc(PyChangeInfo changeInfo, @NotNull PyFunction function) {
    final PyStringLiteralExpression docStringExpression = function.getDocStringExpression();
    if (docStringExpression == null) return;
    final PyParameterInfo[] parameters = changeInfo.getNewParameters();
    Set<String> names = new HashSet<String>();
    for (PyParameterInfo info : parameters) {
      names.add(info.getName());
    }
    for (PyParameter p : function.getParameterList().getParameters()) {
      final String paramName = p.getName();
      if (!names.contains(paramName) && paramName != null) {
        PyDocumentationSettings documentationSettings = PyDocumentationSettings.getInstance(function.getProject());
        String prefix = documentationSettings.isEpydocFormat(docStringExpression.getContainingFile())? "@" : ":";
        final String replacement = PythonDocCommentUtil.removeParamFromDocstring(docStringExpression.getText(), prefix,
                                                                                 paramName);
        PyExpression str = PyElementGenerator.getInstance(function.getProject()).createDocstring(replacement).getExpression();
        docStringExpression.replace(str);
      }
    }
  }

  private static void updateParameterList(PyChangeInfo changeInfo, PyFunction baseMethod) {
    final PsiElement parameterList = baseMethod.getParameterList();

    final PyParameterInfo[] parameters = changeInfo.getNewParameters();
    StringBuilder builder = new StringBuilder("def foo(");
    final PyStringLiteralExpression docstring = baseMethod.getDocStringExpression();
    final PyParameter[] oldParameters = baseMethod.getParameterList().getParameters();
    for (int i = 0; i != parameters.length; ++i) {
      PyParameterInfo info = parameters[i];

      if (docstring != null && info.getOldIndex() == -1) {
        final String replacement = new PyDocstringGenerator(baseMethod).withParam("param", info.getName()).docStringAsText();
        PyExpression str = PyElementGenerator.getInstance(baseMethod.getProject()).createDocstring(replacement).getExpression();
        docstring.replace(str);
      }

      builder.append(info.getName());
      if (info.getOldIndex() != -1 && info.getOldIndex() < oldParameters.length) {
        final PyParameter parameter = oldParameters[info.getOldIndex()];
        if (parameter instanceof PyNamedParameter) {
          final PyAnnotation annotation = ((PyNamedParameter)parameter).getAnnotation();
          if (annotation != null)
            builder.append(annotation.getText());
        }
      }
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

  @Override
  public void registerConflictResolvers(List<ResolveSnapshotProvider.ResolveSnapshot> snapshots,
                                        @NotNull ResolveSnapshotProvider resolveSnapshotProvider,
                                        UsageInfo[] usages, ChangeInfo changeInfo) {
  }
}
