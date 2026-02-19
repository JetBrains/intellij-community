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
public abstract class SectionBasedDocStringBuilder extends DocStringBuilder<SectionBasedDocStringBuilder> {

  protected String mySectionIndent;
  protected final String myContinuationIndent;

  private String myCurSectionTitle = null;

  protected SectionBasedDocStringBuilder(@NotNull String defaultSectionIndent, @NotNull String defaultContinuationIndent) {
    mySectionIndent = defaultSectionIndent; 
    myContinuationIndent = defaultContinuationIndent;
  }

  public @NotNull SectionBasedDocStringBuilder startParametersSection() {
    // TODO make default section titles configurable
    return startSection(getDefaultParametersHeader());
  }

  public @NotNull SectionBasedDocStringBuilder startReturnsSection() {
    return startSection(getDefaultReturnsHeader());
  }

  protected @NotNull SectionBasedDocStringBuilder startSection(@NotNull String title) {
    if (myCurSectionTitle != null) {
      addEmptyLine();
    }
    myCurSectionTitle = title;
    return this;
  }

  public @NotNull SectionBasedDocStringBuilder endSection() {
    myCurSectionTitle = null;
    return this;
  }

  protected abstract @NotNull String getDefaultParametersHeader();

  protected abstract @NotNull String getDefaultReturnsHeader();

  public abstract @NotNull SectionBasedDocStringBuilder addParameter(@NotNull String name, @Nullable String type, @NotNull String description);

  public abstract @NotNull SectionBasedDocStringBuilder addReturnValue(@Nullable String name, @NotNull String type, @NotNull String description);

  protected @NotNull SectionBasedDocStringBuilder addSectionLine(@NotNull String line) {
    return addLine(mySectionIndent + line);
  }

  protected @NotNull SectionBasedDocStringBuilder withSectionIndent(@NotNull String indent) {
    mySectionIndent = indent;
    return this;
  }
}
