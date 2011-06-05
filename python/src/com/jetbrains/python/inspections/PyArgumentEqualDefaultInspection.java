package com.jetbrains.python.inspections;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReference;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.actions.RemoveArgumentEqualDefaultQuickFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
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
      PyCallExpression.PyMarkedCallee func = node.resolveCallee(myTypeEvalContext);
      if ((func != null && func.isImplicitlyResolved()) || (list == null)) return;
      if (func != null) {
        // getattr's default attribute is a special case, see PY-3440
        final Callable callable = func.getCallable();
        if ("getattr".equals(callable.getName()) && PyBuiltinCache.getInstance(node).hasInBuiltins(callable)) return;
      }
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
          if (key instanceof PyKeywordArgument && ((PyKeywordArgument)key).getValueExpression() != null) {
            key = ((PyKeywordArgument)key).getValueExpression();
          }
          if (isEqual(key, defaultValue)) {
            problemElements.add(e.getKey());
          }
        }
      }
      boolean canDelete = true;
      for (int i = arguments.length-1; i != -1; --i) {
        if (problemElements.contains(arguments[i])) {
          if (canDelete)
            registerProblem(arguments[i], PyBundle.message("INSP.argument.equals.to.default"),
                            new RemoveArgumentEqualDefaultQuickFix(problemElements));
          else
            registerProblem(arguments[i], PyBundle.message("INSP.argument.equals.to.default"));

        }
        else if (!(arguments[i] instanceof PyKeywordArgument)) canDelete = false;
      }
    }

    private boolean isEqual(PyExpression key, PyExpression defaultValue) {
      if (key instanceof PyNumericLiteralExpression && defaultValue instanceof PyNumericLiteralExpression) {
        if (key.getText().equals(defaultValue.getText()))
          return true;
      }
      else if (key instanceof PyStringLiteralExpression && defaultValue instanceof PyStringLiteralExpression) {
        if (((PyStringLiteralExpression)key).getStringValue().equals(((PyStringLiteralExpression)defaultValue).getStringValue()))
          return true;
      }
      else {
        PsiReference keyRef = key.getReference();
        PsiReference defRef = defaultValue.getReference();
        if (keyRef != null && defRef != null) {
          PsiElement keyResolve = keyRef.resolve();
          PsiElement defResolve = defRef.resolve();
          if (keyResolve != null && keyResolve.equals(defResolve))
            return true;
        }
      }
      return false;
    }
  }
}
