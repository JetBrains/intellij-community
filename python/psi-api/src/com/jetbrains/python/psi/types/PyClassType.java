package com.jetbrains.python.psi.types;

import com.intellij.openapi.util.UserDataHolder;
import com.jetbrains.python.psi.PyClass;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface PyClassType extends PyCallableType, UserDataHolder {
  @Nullable
  PyClass getPyClass();

  boolean isDefinition();

  PyClassType toInstance();

  @Nullable
  String getClassQName();

  boolean isValid();
}
