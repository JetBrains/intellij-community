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
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.codeInsight.template.TemplateBuilderFactory;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.ParamHelper;
import com.jetbrains.python.psi.impl.PyFunctionBuilder;
import org.jetbrains.annotations.NotNull;

/**
 * User: catherine
 *
 * QuickFix to create function to unresolved unqualified reference
 */
public class UnresolvedRefCreateFunctionQuickFix implements LocalQuickFix {
  private PyCallExpression myElement;
  private PyReferenceExpression myReference;

  public UnresolvedRefCreateFunctionQuickFix(PyCallExpression element, PyReferenceExpression reference) {
    myElement = element;
    myReference = reference;
  }

  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.unresolved.reference.create.function.$0", myReference.getText());
  }

  @NotNull
  public String getFamilyName() {
    return getName();
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    if (!myElement.isValid() || !FileModificationService.getInstance().preparePsiElementForWrite(myElement)) return;

    PyFunctionBuilder functionBuilder = new PyFunctionBuilder(myReference.getText(), myElement);

    // if function is actually an argument of a call, don't use other arguments of the call to create parameter list of new function
    final PyArgumentList argumentList = myElement.getArgumentList();
    if (argumentList != null && !PsiTreeUtil.isAncestor(argumentList, myReference, false)) {
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

    PyFunction function = functionBuilder.buildFunction(project, LanguageLevel.getDefault());
    PyFunction parentFunction = PsiTreeUtil.getTopmostParentOfType(myElement, PyFunction.class);
    if (parentFunction != null ) {
      PyClass parentClass = PsiTreeUtil.getTopmostParentOfType(parentFunction, PyClass.class);
      if (parentClass != null) {
        PsiElement parent = parentClass.getParent();
        function = (PyFunction)parent.addBefore(function, parentClass);
      } else {
        PsiElement parent = parentFunction.getParent();
        function = (PyFunction)parent.addBefore(function, parentFunction);
      }
    } else {
      PyStatement statement = PsiTreeUtil.getTopmostParentOfType(myElement,
                                                                 PyStatement.class);
      if (statement != null) {
        PsiElement parent = statement.getParent();
        if (parent != null)
          function = (PyFunction)parent.addBefore(function, statement);
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
    builder.run();
  }
}
