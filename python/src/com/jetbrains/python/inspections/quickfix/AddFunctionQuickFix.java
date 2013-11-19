/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.inspections.PyInspectionExtension;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.ParamHelper;
import com.jetbrains.python.psi.impl.PyFunctionBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.jetbrains.python.psi.PyUtil.sure;

/**
 * Adds a missing top-level function to a module.
 * <br/>
 * User: dcheryasov
 * Date: Sep 15, 2010 4:34:23 PM
 * @see AddMethodQuickFix AddMethodQuickFix
 */
public class AddFunctionQuickFix  implements LocalQuickFix {

  private final String myIdentifier;
  private PyFile myPyFile;

  public AddFunctionQuickFix(@NotNull String identifier, PyFile module) {
    myIdentifier = identifier;
    myPyFile = module;
  }

  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.NAME.add.function.$0.to.module.$1", myIdentifier, myPyFile.getName());
  }

  @NotNull
  public String getFamilyName() {
    return "Create function in module";
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    try {
      // descriptor points to the unresolved identifier
      // there can be no name clash, else the name would have resolved, and it hasn't.
      PsiElement problem_elt = descriptor.getPsiElement().getParent(); // id -> ref expr
      sure(myPyFile);
      sure(FileModificationService.getInstance().preparePsiElementForWrite(myPyFile));
      // try to at least match parameter count
      // TODO: get parameter style from code style
      PyFunctionBuilder builder = new PyFunctionBuilder(myIdentifier);
      PsiElement problemParent = problem_elt.getParent();
      if (problemParent instanceof PyCallExpression) {
        PyArgumentList arglist = ((PyCallExpression)problemParent).getArgumentList();
        sure(arglist);
        final PyExpression[] args = arglist.getArguments();
        for (PyExpression arg : args) {
          if (arg instanceof PyKeywordArgument) { // foo(bar) -> def foo(bar_1)
            builder.parameter(((PyKeywordArgument)arg).getKeyword());
          }
          else if (arg instanceof PyReferenceExpression) {
            PyReferenceExpression refex = (PyReferenceExpression)arg;
            builder.parameter(refex.getReferencedName());
          }
          else { // use a boring name
            builder.parameter("param");
          }
        }
      }
      else if (problemParent != null) {
        for (PyInspectionExtension extension : Extensions.getExtensions(PyInspectionExtension.EP_NAME)) {
          List<String> params = extension.getFunctionParametersFromUsage(problem_elt);
          if (params != null) {
            for (String param : params) {
              builder.parameter(param);
            }
            break;
          }
        }
      }
      // else: no arglist, use empty args
      PyFunction function = builder.buildFunction(project, LanguageLevel.forFile(myPyFile.getVirtualFile()));

      // add to the bottom
      function = (PyFunction) myPyFile.add(function);
      showTemplateBuilder(function);
    }
    catch (IncorrectOperationException ignored) {
      // we failed. tell about this
      PyUtil.showBalloon(project, PyBundle.message("QFIX.failed.to.add.function"), MessageType.ERROR);
    }
  }

  private static void showTemplateBuilder(PyFunction method) {
    method = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(method);

    final TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(method);
    ParamHelper.walkDownParamArray(
      method.getParameterList().getParameters(),
      new ParamHelper.ParamVisitor() {
        public void visitNamedParameter(PyNamedParameter param, boolean first, boolean last) {
          builder.replaceElement(param, param.getName());
        }
      }
    );

    // TODO: detect expected return type from call site context: PY-1863
    builder.replaceElement(method.getStatementList(), "return None");

    builder.run();
  }
}
