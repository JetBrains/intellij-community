package com.jetbrains.python.sdk;

import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public abstract class CPythonSdkFlavor extends PythonSdkFlavor {
  @NotNull
  @Override
  public String getName() {
    return "Python";
  }
}
