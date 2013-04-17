package com.jetbrains.python.psi.types;

import org.jetbrains.annotations.Nullable;

/**
 * @author vlan
 */
public interface PyClassLikeType extends PyCallableType {
  boolean isDefinition();

  PyClassLikeType toInstance();

  @Nullable
  String getClassQName();

  boolean isValid();
}
