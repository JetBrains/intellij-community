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
public abstract class SectionBasedDocStringBuilder extends DocStringBuilder {
  protected static final String DEFAULT_SECTION_INDENT = StringUtil.repeatSymbol(' ', 4);
  protected static final String DEFAULT_CONTINUATION_INDENT = StringUtil.repeatSymbol(' ', 4);

  protected String mySectionIndent = DEFAULT_SECTION_INDENT;
  protected final String myContinuationIndent = DEFAULT_CONTINUATION_INDENT;

  private String myCurSectionTitle = null;

  @NotNull
  public SectionBasedDocStringBuilder startParametersSection() {
    // TODO make default section titles configurable
    return startSection("Parameters");
  }

  @NotNull
  public SectionBasedDocStringBuilder startReturnsSection() {
    return startSection("Returns");
  }

  @NotNull
  protected SectionBasedDocStringBuilder startSection(@NotNull String title) {
    if (myCurSectionTitle != null) {
      addEmptyLine();
    }
    myCurSectionTitle = title;
    return this;
  }

  @NotNull
  public SectionBasedDocStringBuilder endSection() {
    myCurSectionTitle = null;
    return this;
  }

  @NotNull
  public abstract SectionBasedDocStringBuilder addParameter(@NotNull String name, @Nullable String type, @NotNull String description);

  @NotNull
  public abstract SectionBasedDocStringBuilder addReturnValue(@Nullable String name, @NotNull String type, @NotNull String description);

  @NotNull
  protected SectionBasedDocStringBuilder addSectionLine(@NotNull String line) {
    return (SectionBasedDocStringBuilder)addLine(mySectionIndent + line);
  }

  @NotNull
  protected SectionBasedDocStringBuilder withSectionIndent(@NotNull String indent) {
    mySectionIndent = indent;
    return this;
  }
}
