package com.jetbrains.python.inspections;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

import static com.jetbrains.python.psi.PyFunction.Flag.CLASSMETHOD;
import static com.jetbrains.python.psi.PyFunction.Flag.STATICMETHOD;

/**
 * Checks for for calls like <code>X.method(y,...)</code>, where y is not an instance of X.
 * <br/>
 * Not marked are cases of inheritance calls in old-style classes, like:<pre>
 * class B(A):
 *   def foo(self):
 *     A.foo(self)
 * </pre>
 * <br/>
 * User: dcheryasov
 * Date: Sep 22, 2010 12:21:44 PM
 */
public class PyCallByClassInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.different.class.call");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  @Override
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.WEAK_WARNING;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly, @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  private static class Visitor extends PyInspectionVisitor {

    public Visitor(final ProblemsHolder holder, LocalInspectionToolSession session) {
      super(holder, session);
    }


    @Override
    public void visitPyCallExpression(PyCallExpression call) {
      PyExpression callee = call.getCallee();
      if (callee instanceof PyQualifiedExpression) {
        PyExpression qualifier = ((PyQualifiedExpression)callee).getQualifier();
        if (qualifier != null) {
          PyType qual_type = myTypeEvalContext.getType(qualifier);
          if (qual_type instanceof PyClassType) {
            final PyClassType qual_class_type = (PyClassType)qual_type;
            if (qual_class_type.isDefinition()) {
              PyClass qual_class = qual_class_type.getPyClass();
              if (qual_class != null) {
                final PyArgumentList arglist = call.getArgumentList();
                if (arglist != null) {
                  PyArgumentList.AnalysisResult analysis = arglist.analyzeCall(myTypeEvalContext);
                  final PyCallExpression.PyMarkedCallee markedCallee = analysis.getMarkedCallee();
                  if (markedCallee != null  && !markedCallee.getFlags().contains(STATICMETHOD)) {
                    PyParameter[] params = markedCallee.getCallable().getParameterList().getParameters();
                    if (params.length > 0 && params[0] instanceof PyNamedParameter) {
                      PyNamedParameter first_param = (PyNamedParameter)params[0];
                      for (Map.Entry<PyExpression, PyNamedParameter> entry : analysis.getPlainMappedParams().entrySet()) {
                        // we ignore *arg and **arg which we cannot analyze
                        if (entry.getValue() == first_param) {
                          PyExpression first_arg = entry.getKey();
                          assert first_arg != null;
                          PyType first_arg_type = myTypeEvalContext.getType(first_arg);
                          if (first_arg_type instanceof PyClassType) {
                            final PyClassType first_arg_class_type = (PyClassType)first_arg_type;
                            if (first_arg_class_type.isDefinition() && !markedCallee.getFlags().contains(CLASSMETHOD)) {
                              registerProblem(
                                first_arg,
                                PyBundle.message("INSP.instance.of.$0.excpected", qual_class.getQualifiedName())
                              );
                            }
                            PyClass first_arg_class = first_arg_class_type.getPyClass();
                            if (first_arg_class != null && first_arg_class != qual_class) {
                              // delegating to a parent is fine
                              if (markedCallee.getCallable() instanceof PyFunction) {
                                Callable callable = PsiTreeUtil.getParentOfType(call, Callable.class);
                                if (callable != null) {
                                  PyFunction method = callable.asMethod();
                                  if (method != null) {
                                    PyClass calling_class = method.getContainingClass();
                                    assert calling_class != null; // it's a method
                                    if (first_arg_class.isSubclass(qual_class) && calling_class.isSubclass(qual_class)) {
                                      break;
                                      // TODO: might propose to switch to super() here
                                    }
                                  }
                                }
                              }
                              // otherwise, it's not
                              registerProblem(
                                first_arg,
                                PyBundle.message(
                                  "INSP.passing.$0.instead.of.$1",
                                  first_arg_class.getQualifiedName(),  qual_class.getQualifiedName()
                                )
                              );
                            }
                          }
                          break; // once we found the first parameter, we don't need the rest
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
