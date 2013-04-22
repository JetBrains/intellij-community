package com.jetbrains.python.psi.types;

import com.intellij.openapi.util.UserDataHolder;
import com.jetbrains.python.psi.PyClass;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public interface PyClassType extends PyClassLikeType, UserDataHolder {
  @NotNull
  PyClass getPyClass();
}
