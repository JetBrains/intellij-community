package com.intellij.openapi.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author cdr
 */
public interface UserDataHolderEx extends UserDataHolder {
  /**
   * @return written value
   */
  @NotNull
  <T> T putUserDataIfAbsent(@NotNull Key<T> key, @NotNull T value);

  /**
   * Replaces (atomically) old value in the map with the new one
   * @return true if old value got replaced, false otherwise
   * @see java.util.concurrent.ConcurrentMap#replace(Object, Object, Object)
   */
  <T> boolean replace(@NotNull Key<T> key, @NotNull T oldValue, @Nullable T newValue);
}
