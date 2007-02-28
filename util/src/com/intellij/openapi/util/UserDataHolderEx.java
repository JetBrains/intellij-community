package com.intellij.openapi.util;

import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
 */
public interface UserDataHolderEx extends UserDataHolder {
  /**
   * @return written value
   */
  @NotNull
  <T> T putUserDataIfAbsent(@NotNull Key<T> key, @NotNull T value);
}
