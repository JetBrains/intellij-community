// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiNamedElement;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Reports assignment to 'self' or 'cls'.
 */
public final class PyMethodFirstArgAssignmentInspection extends PyInspection {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, PyInspectionVisitor.getContext(session));
  }

  public static class Visitor extends PyInspectionVisitor {
    public Visitor(@Nullable ProblemsHolder holder, @NotNull TypeEvalContext context) {
      super(holder, context);
    }

    private void complain(PsiElement element, String name) {
      registerProblem(element, PyPsiBundle.message("INSP.first.arg.assign.method.parameter.reassigned", name));
    }

    private void handleTarget(PyQualifiedExpression target, String name) {
      if (!target.isQualified() && name.equals(target.getText())) {
        complain(target, name);
      }
    }

    @Nullable
    private static String extractFirstParamName(PyElement node) {
      // are we a method?
      List<? extends PsiElement> place = PyUtil.searchForWrappingMethod(node, true);
      if (place == null || place.size() < 2) return null;
      PyFunction method = (PyFunction)place.get(place.size()-2);
      //PyClass owner = (PyClass)place.get(place.size()-1);
      // what is our first param?
      PyParameter[] params = method.getParameterList().getParameters();
      if (params.length < 1) return null; // no params
      PyNamedParameter first_parm = params[0].getAsNamed();
      if (first_parm == null) return null;
      if (first_parm.isKeywordContainer() || first_parm.isPositionalContainer()) return null; // legal but crazy cases; back off
      final String first_param_name = first_parm.getName();
      if (first_param_name == null || first_param_name.length() < 1) return null; // ignore cases of incorrect code
      // is it a static method?
      PyFunction.Modifier modifier = method.getModifier();
      if (modifier == PyFunction.Modifier.STATICMETHOD) return null; // these may do whatever they please
      return first_param_name;
    }

    private void markNameDefiner(PyNamedElementContainer definer) {
      final String first_param_name = extractFirstParamName((PyElement)definer);
      if (first_param_name != null) {
        // check the targets
        for (PsiNamedElement elt : definer.getNamedElements()) {
          if (elt instanceof PyTargetExpression) handleTarget((PyTargetExpression)elt, first_param_name);
        }
      }
    }

    @Override
    public void visitPyAssignmentStatement(@NotNull PyAssignmentStatement node) {
      markNameDefiner(node);
    }

    @Override
    public void visitPyAugAssignmentStatement(@NotNull PyAugAssignmentStatement node) {
      final String first_param_name = extractFirstParamName(node);
      if (first_param_name != null) {
        PyExpression target = node.getTarget();
        if (target instanceof PyQualifiedExpression) handleTarget((PyQualifiedExpression)target, first_param_name);
        else if (target instanceof PyTupleExpression) {
          for (PyExpression elt : PyUtil.flattenedParensAndTuples(((PyTupleExpression)target).getElements())) {
            if (elt instanceof PyQualifiedExpression) handleTarget((PyQualifiedExpression)elt, first_param_name);
          }
        }
      }
    }

    @Override
    public void visitPyForStatement(@NotNull PyForStatement node) {
      markNameDefiner(node);
    }

    /* TODO: implement when WithStmt is part-ified
    @Override
    public void visitPyWithStatement(PyWithStatement node) {
      super.visitPyWithStatement(node);
    }
    */

    private void markDefinition(PyElement definer) {
      final String first_param_name = extractFirstParamName(definer);
      if (first_param_name != null && first_param_name.equals(definer.getName())) {
        complain(definer.getNode().findChildByType(PyTokenTypes.IDENTIFIER).getPsi(), first_param_name); // no NPE here, or we won't have the name
      }
    }

    @Override
    public void visitPyFunction(@NotNull PyFunction definer) {
      markDefinition(definer);
    }

    @Override
    public void visitPyClass(@NotNull PyClass definer) {
      markDefinition(definer);
    }


  }
}
