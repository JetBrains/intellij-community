package com.jetbrains.python.inspections;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.actions.RemoveArgumentEqualDefaultQuickFix;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyNamedParameter;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

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
      checkArguments(result);
    }

    private void checkArguments(PyArgumentList.AnalysisResult result) {
      Map<PyExpression, PyNamedParameter> mapping = result.getPlainMappedParams();
      for (Map.Entry<PyExpression, PyNamedParameter> e : mapping.entrySet()) {
        PyExpression defaultValue = e.getValue().getDefaultValue();
        if (defaultValue != null) {
          if (e.getKey().getText().equals(defaultValue.getText())) {
            registerProblem(e.getKey(), "Argument equals to default parameter value",
                            new RemoveArgumentEqualDefaultQuickFix());
          }
        }
      }
    }
  }
}
