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

import java.util.List;
import java.util.Map;

import static com.jetbrains.python.psi.PyFunction.Modifier.CLASSMETHOD;
import static com.jetbrains.python.psi.PyFunction.Modifier.STATICMETHOD;

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

  public static class Visitor extends PyInspectionVisitor {

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
              final PyArgumentList arglist = call.getArgumentList();
              if (arglist != null) {
                final PyCallExpression.PyArgumentsMapping mapping = call.mapArguments(getResolveContext());
                final PyCallExpression.PyMarkedCallee markedCallee = mapping.getMarkedCallee();
                if (markedCallee != null  && markedCallee.getModifier() != STATICMETHOD) {
                  final List<PyParameter> params = PyUtil.getParameters(markedCallee.getCallable(), myTypeEvalContext);
                  if (params.size() > 0 && params.get(0) instanceof PyNamedParameter) {
                    PyNamedParameter first_param = (PyNamedParameter)params.get(0);
                    for (Map.Entry<PyExpression, PyNamedParameter> entry : mapping.getMappedParameters().entrySet()) {
                      // we ignore *arg and **arg which we cannot analyze
                      if (entry.getValue() == first_param) {
                        PyExpression first_arg = entry.getKey();
                        assert first_arg != null;
                        PyType first_arg_type = myTypeEvalContext.getType(first_arg);
                        if (first_arg_type instanceof PyClassType) {
                          final PyClassType first_arg_class_type = (PyClassType)first_arg_type;
                          if (first_arg_class_type.isDefinition() && markedCallee.getModifier() != CLASSMETHOD) {
                            registerProblem(
                              first_arg,
                              PyBundle.message("INSP.instance.of.$0.excpected", qual_class.getQualifiedName())
                            );
                          }
                          PyClass first_arg_class = first_arg_class_type.getPyClass();
                          if (first_arg_class != qual_class) {
                            // delegating to a parent is fine
                            if (markedCallee.getCallable() instanceof PyFunction) {
                              PyCallable callable = PsiTreeUtil.getParentOfType(call, PyCallable.class);
                              if (callable != null) {
                                PyFunction method = callable.asMethod();
                                if (method != null) {
                                  PyClass calling_class = method.getContainingClass();
                                  assert calling_class != null; // it's a method
                                  if (first_arg_class.isSubclass(qual_class, null) && calling_class.isSubclass(qual_class, null)) {
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
