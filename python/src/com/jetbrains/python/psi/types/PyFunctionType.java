package com.jetbrains.python.psi.types;

import com.intellij.util.ProcessingContext;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Type of a particular function that is represented as a {@link PyFunction} in the PSI tree.
 *
 * TODO: Maybe this class should extend PyClassType and have a Python mock class for <code>type 'function'</code> in the __builtins__.
 *
 * @author vlan
 */
public class PyFunctionType implements PyCallableType {
  @NotNull private final PyFunction myFunction;

  public PyFunctionType(@NotNull PyFunction function) {
    myFunction = function;
  }

  @Nullable
  @Override
  public PyType getCallType(@NotNull TypeEvalContext context, @Nullable PyQualifiedExpression callSite) {
    return myFunction.getReturnType(context, callSite);
  }

  @Override
  public List<? extends RatedResolveResult> resolveMember(String name,
                                                          @Nullable PyExpression location,
                                                          AccessDirection direction,
                                                          PyResolveContext resolveContext) {
    return Collections.emptyList();
  }

  @Override
  public Object[] getCompletionVariants(String completionPrefix, PyExpression location, ProcessingContext context) {
    return new Object[0];
  }

  @Override
  public String getName() {
    return "function";
  }

  @Override
  public boolean isBuiltin(TypeEvalContext context) {
    return false;
  }

  @Override
  public void assertValid(String message) {
  }

  @NotNull
  public PyFunction getFunction() {
    return myFunction;
  }
}
