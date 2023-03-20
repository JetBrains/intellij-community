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
package com.jetbrains.numpy.documentation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


/**
 * @author avereshchagin
 */
public class NumPyDocStringParameter {
  private final String myName;
  private final String myType;
  private final String myDescription;

  public NumPyDocStringParameter(@NotNull String name, @Nullable String type, @Nullable String description) {
    myName = name;
    myType = type;
    myDescription = description;
  }

  public String getName() {
    return myName;
  }

  public String getType() {
    return myType;
  }

  public String getDescription() {
    return myDescription;
  }
}
