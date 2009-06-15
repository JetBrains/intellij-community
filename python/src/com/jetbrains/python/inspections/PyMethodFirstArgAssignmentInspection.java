package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.patterns.SyntaxMatchers;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Reports assignment to 'self' or 'cls'.
 * User: dcheryasov
 * Date: Jun 12, 2009 6:45:16 AM
 */
public class PyMethodFirstArgAssignmentInspection  extends LocalInspectionTool {
  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return PyBundle.message("INSP.GROUP.python");
  }

  @Nls
  @NotNull
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.first.arg.assign");
  }

  @NotNull
  public String getShortName() {
    return "PyMethodFirstArgAssignmentInspection";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new Visitor(holder);
  }

  public static class Visitor extends PyInspectionVisitor {
    public Visitor(final ProblemsHolder holder) {
      super(holder);
    }

    private void complain(PsiElement element, String name) {
      registerProblem(element, PyBundle.message("INSP.first.arg.$0.assigned", name));
    }

    private void handleTarget(PyTargetExpression target, String name) {
      if (target.getQualifier() == null && name.equals(target.getText())) {
        complain(target, name);
      }
    }

    private static String extractFirstParamName(PyElement node) {
      // are we a method?
      List<? extends PsiElement> place = SyntaxMatchers.DEEP_IN_METHOD.search(node);
      if (place == null || place.size() < 2) return null;
      PyFunction method = (PyFunction)place.get(place.size()-2);
      //PyClass owner = (PyClass)place.get(place.size()-1);
      // what is our first param?
      PyParameter[] params = method.getParameterList().getParameters();
      if (params.length < 1) return null; // no params
      PyParameter first_parm = params[0];
      if (first_parm.isKeywordContainer() || first_parm.isPositionalContainer()) return null; // legal but crazy cases; back off
      final String first_param_name = first_parm.getName();
      if (first_param_name == null || first_param_name.length() < 1) return null; // ignore cases of incorrect code
      // is it a static method?
      boolean is_staticmethod = false;
      final PyDecoratorList deco_list = method.getDecoratorList();
      if (deco_list != null) {
        for (PyDecorator deco : deco_list.getDecorators()) {
          if (PyNames.STATICMETHOD.equals(deco.getName()) && deco.isBuiltin()) {
            is_staticmethod = true;
            break; // sane code has no more than one
          }
          // we ignore other decorators
        }
      }
      if (is_staticmethod) return null; // these may do whatever they please
      return first_param_name;
    }

    private void markNameDefiner(NameDefiner definer) {
      final String first_param_name = extractFirstParamName((PyElement)definer);
      if (first_param_name != null) {
        // check the targets
        for (PyElement elt : definer.iterateNames()) {
          if (elt instanceof PyTargetExpression) handleTarget((PyTargetExpression)elt, first_param_name);
        }
      }
    }

    @Override
    public void visitPyAssignmentStatement(PyAssignmentStatement node) {
      markNameDefiner(node);
    }

    @Override
    public void visitPyForStatement(PyForStatement node) {
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
        complain(definer.getNode().findChildByType(PyTokenTypes.IDENTIFIER).getPsi(), first_param_name);
      }
    }

    @Override
    public void visitPyFunction(PyFunction definer) {
      markDefinition(definer);
    }

    @Override
    public void visitPyClass(PyClass definer) {
      markDefinition(definer);
    }


  }
}
