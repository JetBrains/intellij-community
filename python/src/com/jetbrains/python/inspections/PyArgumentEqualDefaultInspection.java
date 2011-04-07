package com.jetbrains.python.inspections;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.actions.RemoveArgumentEqualDefaultQuickFix;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * User: catherine
 *
 * Inspection to detect situations, where argument passed to function
 * is equal to default parameter value
 * for instance,
 * dict().get(x, None) --> None is default value for second param in dict().get function
 */
public class PyArgumentEqualDefaultInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.argument.equal.default");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new Visitor(holder);
  }

  private static class Visitor extends PyInspectionVisitor {

    public Visitor(final ProblemsHolder holder) {
      super(holder);
    }

    @Override
    public void visitPyCallExpression(final PyCallExpression node){
      PyArgumentList list = node.getArgumentList();
      if (list == null) return;
      PyArgumentList.AnalysisResult result = list.analyzeCall(myTypeEvalContext);
      checkArguments(result, node.getArguments());
    }

    private void checkArguments(PyArgumentList.AnalysisResult result, PyExpression[] arguments) {
      Map<PyExpression, PyNamedParameter> mapping = result.getPlainMappedParams();
      Set<PyExpression> problemElements = new HashSet<PyExpression>();
      for (Map.Entry<PyExpression, PyNamedParameter> e : mapping.entrySet()) {
        PyExpression defaultValue = e.getValue().getDefaultValue();
        if (defaultValue != null) {
          PyExpression key = e.getKey();
          String text = e.getKey().getText();
          if (key instanceof PyKeywordArgument && ((PyKeywordArgument)key).getValueExpression() != null) {
            text = ((PyKeywordArgument)key).getValueExpression().getText();
          }
          if (text.equals(defaultValue.getText())) {
            problemElements.add(e.getKey());
          }
        }
      }
      boolean canDelete = true;
      for (int i = arguments.length-1; i != -1; --i) {
        if (problemElements.contains(arguments[i])) {
          if (canDelete)
            registerProblem(arguments[i], "Argument equals to default parameter value",
                            new RemoveArgumentEqualDefaultQuickFix());
          else
            registerProblem(arguments[i], "Argument equals to default parameter value");

        }
        if (!(arguments[i] instanceof PyKeywordArgument)) canDelete = false;
      }
    }
  }
}
