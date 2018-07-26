/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.codeInsight.template.TemplateBuilderFactory;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.ParamHelper;
import com.jetbrains.python.psi.impl.PyFunctionBuilder;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * User: catherine
 *
 * QuickFix to create function to unresolved unqualified reference
 */
public class UnresolvedRefCreateFunctionQuickFix implements LocalQuickFix {
  private final String myFunctionName;
  private final SmartPsiElementPointer<PyCallExpression> myCallExpr;
  private final SmartPsiElementPointer<PyReferenceExpression> myReferenceExpr;

  public UnresolvedRefCreateFunctionQuickFix(PyCallExpression element, PyReferenceExpression reference) {
    final SmartPointerManager manager = SmartPointerManager.getInstance(element.getProject());
    myCallExpr = manager.createSmartPsiElementPointer(element);
    myReferenceExpr = manager.createSmartPsiElementPointer(reference);
    myFunctionName = reference.getReferencedName();
  }

  @Nls
  @NotNull
  @Override
  public String getName() {
    return PyBundle.message("QFIX.NAME.unresolved.reference.create.function", myFunctionName);
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("QFIX.unresolved.reference.create.function");
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PyCallExpression callExpr = myCallExpr.getElement();
    final PyReferenceExpression referenceExpr = myReferenceExpr.getElement();

    if (callExpr == null || !callExpr.isValid() || referenceExpr == null || !referenceExpr.isValid()) {
      return;
    }

    final PyFunctionBuilder functionBuilder = new PyFunctionBuilder(referenceExpr.getText(), callExpr);

    // if function is actually an argument of a call, don't use other arguments of the call to create parameter list of new function
    final PyArgumentList argumentList = callExpr.getArgumentList();
    if (argumentList != null && !PsiTreeUtil.isAncestor(argumentList, referenceExpr, false)) {
      for (PyExpression param : argumentList.getArguments()) {
        if (param instanceof PyKeywordArgument) {
          functionBuilder.parameter(((PyKeywordArgument)param).getKeyword());
        }
        else if (param instanceof PyReferenceExpression) {
          PyReferenceExpression refex = (PyReferenceExpression)param;
          functionBuilder.parameter(refex.getReferencedName());
        }
        else {
          functionBuilder.parameter("param");
        }
      }
    }
    else {
      functionBuilder.parameter("args");
    }

    PyFunction function = functionBuilder.buildFunction();

    final InjectedLanguageManager instance = InjectedLanguageManager.getInstance(project);
    final PsiLanguageInjectionHost host = instance.getInjectionHost(callExpr);
    final PsiElement insertAnchor = host != null ? host : callExpr;

    final PyFunction parentFunction = PsiTreeUtil.getTopmostParentOfType(insertAnchor, PyFunction.class);
    if (parentFunction != null) {
      final PyClass parentClass = PsiTreeUtil.getTopmostParentOfType(parentFunction, PyClass.class);
      if (parentClass != null) {
        final PsiElement parent = parentClass.getParent();
        function = (PyFunction)parent.addBefore(function, parentClass);
      }
      else {
        final PsiElement parent = parentFunction.getParent();
        function = (PyFunction)parent.addBefore(function, parentFunction);
      }
    }
    else {
      final PyStatement statement = PsiTreeUtil.getTopmostParentOfType(insertAnchor, PyStatement.class);
      if (statement != null) {
        final PsiElement parent = statement.getParent();
        if (parent != null) {
          function = (PyFunction)parent.addBefore(function, statement);
        }
      }
    }

    function = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(function);
    final TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(function);
    ParamHelper.walkDownParamArray(
      function.getParameterList().getParameters(),
      new ParamHelper.ParamVisitor() {
        public void visitNamedParameter(PyNamedParameter param, boolean first, boolean last) {
          builder.replaceElement(param, param.getName());
        }
      }
    );
    builder.replaceElement(function.getStatementList(), PyNames.PASS);

    final FileEditor editor = FileEditorManager.getInstance(project).getSelectedEditor(insertAnchor.getContainingFile().getVirtualFile());
    if (!(editor instanceof TextEditor)) {
      return;
    }

    builder.run(((TextEditor)editor).getEditor(), false);
  }
}
