package com.jetbrains.python.psi.types;

import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author vlan
 */
public interface PyClassLikeType extends PyCallableType {
  boolean isDefinition();

  PyClassLikeType toInstance();

  @Nullable
  String getClassQName();

  @NotNull
  List<PyClassLikeType> getSuperClassTypes(@NotNull TypeEvalContext context);

  @Nullable
  List<? extends RatedResolveResult> resolveMember(@NotNull final String name, @Nullable PyExpression location,
                                                   @NotNull AccessDirection direction, @NotNull PyResolveContext resolveContext,
                                                   boolean inherited);

  boolean isValid();
}
