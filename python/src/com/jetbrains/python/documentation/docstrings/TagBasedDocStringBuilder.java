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

/**
 * @author Mikhail Golubev
 */
public class TagBasedDocStringBuilder extends DocStringBuilder<TagBasedDocStringBuilder> {
  private final String myTagPrefix;

  public TagBasedDocStringBuilder(@NotNull String prefix) {
    myTagPrefix = prefix;
  }

  @NotNull
  public TagBasedDocStringBuilder addParameterDescription(@NotNull String name, @NotNull String description) {
    return addLine(String.format("%sparam %s: %s", myTagPrefix, name, description));
  }

  @NotNull
  public TagBasedDocStringBuilder addParameterType(@NotNull String name, @NotNull String type) {
    return addLine(String.format("%stype %s: %s", myTagPrefix, name, type));
  }

  @NotNull
  public TagBasedDocStringBuilder addReturnValueType(@NotNull String type) {
    // named return values are not supported in Sphinx and Epydoc
    return addLine(String.format("%srtype: %s", myTagPrefix, type));
  }

  public TagBasedDocStringBuilder addReturnValueDescription(@NotNull String description) {
    return addLine(String.format("%sreturn: %s", myTagPrefix, description));
  }

  @NotNull
  public TagBasedDocStringBuilder addExceptionDescription(@NotNull String type, @NotNull String description) {
    return addLine(String.format("%sraise %s: %s", myTagPrefix, type, description));
  }

  @NotNull
  public TagBasedDocStringBuilder addSummary(@NotNull String summary) {
    return addLine(summary).addLine("");
  }
}
