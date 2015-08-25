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

import com.jetbrains.python.documentation.SectionBasedDocString;
import com.jetbrains.python.toolbox.Substring;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class GoogleCodeStyleDocStringUpdater extends SectionBasedDocStringUpdater {
  public GoogleCodeStyleDocStringUpdater(@NotNull SectionBasedDocString docString, @NotNull String minContentIndent) {
    super(docString, minContentIndent);
  }

  @Override
  void updateParamDeclarationWithType(@NotNull Substring nameSubstring, @NotNull String type) {
    insert(nameSubstring.getEndOffset(), " (" + type + ")");
  }

  @Override
  protected SectionBasedDocStringBuilder createBuilder() {
    return new GoogleCodeStyleDocStringBuilder();
  }
}
