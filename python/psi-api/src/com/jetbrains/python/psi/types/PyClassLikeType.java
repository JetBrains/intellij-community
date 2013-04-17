package com.jetbrains.python.psi.types;

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

  boolean isValid();
}
