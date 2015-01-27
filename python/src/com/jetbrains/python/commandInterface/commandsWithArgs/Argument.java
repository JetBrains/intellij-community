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
package com.jetbrains.python.commandInterface.commandsWithArgs;

import org.jetbrains.annotations.NotNull;

/**
 * Command argument
 *
 * @author Ilya.Kazakevich
 */
public class Argument {
  private final boolean myNamed;
  @NotNull
  private final String myName;

  /**
   * @param named is named argument or not
   * @param name  name of argument
   */
  public Argument(final boolean named, @NotNull final String name) {
    myNamed = named;
    myName = name;
  }

  /**
   * @return is named argument or not
   */
  public boolean isNamed() {
    return myNamed;
  }

  /**
   * @return name of argument
   */
  @NotNull
  public String getName() {
    return myName;
  }
}
