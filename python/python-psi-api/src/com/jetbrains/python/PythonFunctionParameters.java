// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python;

import org.jetbrains.annotations.Nullable;

/**
 * Python function arguments
 *
 * @author Ilya.Kazakevich
 */
public enum PythonFunctionParameters implements FunctionParameter {
  /**
   * environment.setdevault(key, failobj) # key
   */
  ENV_SET_DEFAULT_KEY("key", 0),

  /**
   * environment.setdevault(key, failobj) # failobj
   */
  ENV_SET_DEFAULT_FAILOBJ("failobj",1);


  private final @Nullable String myName;
  private final int myPosition;

  PythonFunctionParameters(@Nullable String name, final int position) {
    myName = name;
    myPosition = position;
  }

  @Override
  public int getPosition() {
    return myPosition;
  }

  @Override
  public @Nullable String getName() {
    return myName;
  }
}
