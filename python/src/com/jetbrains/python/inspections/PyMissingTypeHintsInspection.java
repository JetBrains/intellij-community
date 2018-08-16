// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.codeInsight.intentions.PyAnnotateTypesIntention;
import com.jetbrains.python.debugger.PySignature;
import com.jetbrains.python.debugger.PySignatureCacheManager;
import com.jetbrains.python.inspections.quickfix.PyQuickFixUtil;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameter;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author traff
 */
public class PyMissingTypeHintsInspection extends PyInspection {
  /**
   * @noinspection PublicField
   */
  public boolean m_onlyWhenTypesAreKnown = true;

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly, @NotNull LocalInspectionToolSession session) {
    return new PyElementVisitor() {
      @Override
      public void visitPyFunction(PyFunction function) {
        if (!(function.getTypeComment() != null || typeAnnotationsExist(function))) {
          boolean flag = shouldRegisterProblem(function);
          if (flag) {
            ASTNode nameNode = function.getNameNode();
            if (nameNode != null) {
              holder.registerProblem(nameNode.getPsi(),
                                     "Type hinting is missing for function definition",
                                     new AddTypeHintsQuickFix(function.getName()));
            }
          }
        }
      }
    };
  }

  private boolean shouldRegisterProblem(PyFunction function) {
    if (m_onlyWhenTypesAreKnown) {
      PySignature signature = PySignatureCacheManager.getInstance(function.getProject()).findSignature(function);
      return signature != null && canAnnotate(signature);
    }
    else {
      return true;
    }
  }

  private static boolean canAnnotate(@NotNull PySignature signature) {
    return !"NoneType".equals(signature.getReturnTypeQualifiedName())
           || signature.getArgs().size() > 1
           || (signature.getArgs().size() == 1 && !"self".equals(signature.getArgs().get(0).getName()));
  }

  private static boolean typeAnnotationsExist(PyFunction function) {
    for (PyParameter param : function.getParameterList().getParameters()) {
      PyNamedParameter namedParameter = param.getAsNamed();
      if (namedParameter != null) {
        if (namedParameter.getAnnotation() != null) {
          return true;
        }
      }
    }
    if (function.getAnnotation() != null) {
      return true;
    }

    return false;
  }

  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel("Only when types are known(collected from run-time or inferred)",
                                          this, "m_onlyWhenTypesAreKnown");
  }

  private static class AddTypeHintsQuickFix implements LocalQuickFix {
    private final String myName;

    public AddTypeHintsQuickFix(@NotNull String name) {
      myName = name;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return "Add type hints for '" + myName + "'";
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return "Add type hints";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PyFunction function = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PyFunction.class);

      if (function != null) {
        PyAnnotateTypesIntention.annotateTypes(PyQuickFixUtil.getEditor(function), function);
      }
    }
  }
}
