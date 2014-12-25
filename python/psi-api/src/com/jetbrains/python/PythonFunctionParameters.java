/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
