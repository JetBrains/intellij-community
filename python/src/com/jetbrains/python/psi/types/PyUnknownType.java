package com.jetbrains.python.psi.types;

import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author vlan
 */
public class PyUnknownType implements PyType {
  public static final PyUnknownType INSTANCE = new PyUnknownType();

  private PyUnknownType() {
  }

  @Override
  public List<? extends RatedResolveResult> resolveMember(String name,
                                                          @Nullable PyExpression location,
                                                          AccessDirection direction,
                                                          PyResolveContext resolveContext) {
    return null;
  }

  @Override
  public Object[] getCompletionVariants(String completionPrefix, PyExpression location, ProcessingContext context) {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @Override
  public String getName() {
    return "unknown";
  }

  @Override
  public boolean isBuiltin(TypeEvalContext context) {
    return true;
  }
}
