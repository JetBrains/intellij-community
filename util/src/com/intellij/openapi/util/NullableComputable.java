/*
 * @author max
 */
package com.intellij.openapi.util;

import org.jetbrains.annotations.Nullable;

public interface NullableComputable<T> extends Computable<T> {
  @Nullable
  T compute();
}