package com.jetbrains.python.psi;

import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface PyDocStringOwner {
  @Nullable
  String getDocString();
}
