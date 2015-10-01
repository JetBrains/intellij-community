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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.psi.PyIndentUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Mikhail Golubev
 */
public class GoogleCodeStyleDocStringBuilder extends SectionBasedDocStringBuilder {
  public static final String DEFAULT_CONTINUATION_INDENT = PyIndentUtil.FOUR_SPACES;

  @NotNull
  public static String getDefaultSectionIndent(@NotNull Project project) {
    return PyIndentUtil.getIndentFromSettings(project);
  }

  @NotNull
  public static GoogleCodeStyleDocStringBuilder forProject(@NotNull Project project) {
    return new GoogleCodeStyleDocStringBuilder(getDefaultSectionIndent(project)); 
  }

  public GoogleCodeStyleDocStringBuilder(@NotNull String sectionIndent) {
    super(sectionIndent, DEFAULT_CONTINUATION_INDENT);
  }

  @Override
  @NotNull
  protected String getDefaultParametersHeader() {
    return "Args";
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
    addLine(StringUtil.capitalize(title) + ":");
    return this;
  }

  @NotNull
  @Override
  public SectionBasedDocStringBuilder addParameter(@NotNull String name, @Nullable String type, @NotNull String description) {
    if (type != null) {
      addSectionLine(String.format("%s (%s): %s", name, type, description));
    }
    else {
      addSectionLine(String.format("%s: %s", name, description));
    }
    return this;
  }

  @NotNull
  @Override
  public SectionBasedDocStringBuilder addReturnValue(@Nullable String name, @NotNull String type, @NotNull String description) {
    if (name != null) {
      addSectionLine(String.format("%s (%s): %s", name, type, description));
    }
    else {
      addSectionLine(String.format("%s: %s", type, description));
    }
    return this;
  }
}
