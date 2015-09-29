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

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Mikhail Golubev
 */
public class NumpyDocStringBuilder extends SectionBasedDocStringBuilder {
  public static final String DEFAULT_SECTION_INDENT = "";
  public static final String DEFAULT_CONTINUATION_INDENT = "    ";
  public static final char DEFAULT_SECTION_TITLE_UNDERLINE_SYMBOL = '-';
  
  private char myUnderlineSymbol = DEFAULT_SECTION_TITLE_UNDERLINE_SYMBOL;

  public NumpyDocStringBuilder() {
    // Sections are not indented and continuation indent of 4 spaces like in Numpy sources
    super(DEFAULT_SECTION_INDENT, DEFAULT_CONTINUATION_INDENT);
  }

  @Override
  @NotNull
  protected String getDefaultParametersHeader() {
    return "Parameters";
  }

  @Override
  @NotNull
  protected String getDefaultReturnsHeader() {
    return "Returns";
  }

  @NotNull
  @Override
  protected SectionBasedDocStringBuilder startSection(@NotNull String title) {
    super.startSection(title);
    addLine(StringUtil.capitalize(title));
    addLine(StringUtil.repeatSymbol(myUnderlineSymbol, title.length()));
    return this;
  }

  @NotNull
  @Override
  public SectionBasedDocStringBuilder addParameter(@NotNull String name, @Nullable String type, @NotNull String description) {
    if (type != null) {
      addSectionLine(String.format("%s : %s", name, type));
    }
    else {
      addSectionLine(name);
    }
    if (!description.isEmpty()) {
      addSectionLine(myContinuationIndent + description);
    }
    return this;
  }

  @NotNull
  @Override
  public SectionBasedDocStringBuilder addReturnValue(@Nullable String name, @NotNull String type, @NotNull String description) {
    if (name != null) {
      addSectionLine(String.format("%s : %s", name, type));
    }
    else {
      addSectionLine(type);
    }
    if (!description.isEmpty()) {
      addSectionLine(myContinuationIndent + description);
    }
    return this;
  }
}
