/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python.documentation.docstrings;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Mikhail Golubev
 */
public class TagBasedDocStringBuilder extends DocStringBuilder {
  private final String myTagPrefix;

  public TagBasedDocStringBuilder(@NotNull String prefix) {
    myTagPrefix = prefix;
  }

  @NotNull
  @Override
  public DocStringBuilder addParameter(@NotNull String name, @Nullable String type) {
    addLine(String.format("%sparam %s: ", myTagPrefix, name));
    if (type != null) {
      addParameterType(name, type);
    }
    return this;
  }

  @Override
  public DocStringBuilder addParameterType(@NotNull String name, @NotNull String type) {
    addLine(String.format("%stype %s: ", myTagPrefix, type));
    return this;
  }

  @NotNull
  @Override
  public DocStringBuilder addReturnValue(@Nullable String name, @NotNull String type) {
    // named return values are not supported in Sphinx and Epydoc
    addLine(String.format("%srtype: %s", myTagPrefix, type));
    return this;
  }
}
