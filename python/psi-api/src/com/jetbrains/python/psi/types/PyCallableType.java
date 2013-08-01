package com.jetbrains.python.psi.types;

import com.intellij.openapi.util.Pair;
import com.jetbrains.python.psi.PyQualifiedExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A type instances of which can possibly be called. For example, a class definition can be called, and the result of a call is a class
 * instance.
 *
 * @author yole
 */
public interface PyCallableType extends PyType {
  /**
   * Returns true if the type is callable.
   */
  boolean isCallable();

  /**
   * Returns the type which is the result of calling an instance of this type.
   *
   * @return the call result type or null if invalid.
   * @param context
   * @param callSite
   */
  @Nullable
  PyType getCallType(@NotNull TypeEvalContext context, @Nullable PyQualifiedExpression callSite);

  /**
   * Returns the list of parameter types.
   *
   * @return list of (name, type) pairs or null if not applicable. Name and type in pair may be null.
   */
  @Nullable
  List<Pair<String, PyType>> getParameters(@NotNull TypeEvalContext context);
}
