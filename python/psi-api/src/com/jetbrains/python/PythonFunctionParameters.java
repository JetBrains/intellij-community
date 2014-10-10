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


  @Nullable
  private final String myName;
  private final int myPosition;

  PythonFunctionParameters(@Nullable String name, final int position) {
    myName = name;
    myPosition = position;
  }

  @Override
  public int getPosition() {
    return myPosition;
  }

  @Nullable
  @Override
  public String getName() {
    return myName;
  }
}
