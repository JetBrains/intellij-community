/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.lexer;

import com.intellij.openapi.fileTypes.FileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @deprecated Use or extend {@link HtmlLexer} with {@code highlightMode} set to {@code true}.
 */
@Deprecated(forRemoval = true)
public class HtmlHighlightingLexer extends HtmlLexer {

  public HtmlHighlightingLexer() {
    super(true);
  }

  public HtmlHighlightingLexer(@Nullable FileType styleFileType) {
    super(true);
  }

  protected HtmlHighlightingLexer(@NotNull Lexer lexer, boolean caseInsensitive) {
    super(lexer, caseInsensitive, true);
  }

  protected HtmlHighlightingLexer(@NotNull Lexer lexer, boolean caseInsensitive,
                                  @Nullable FileType styleFileType) {
    super(lexer, caseInsensitive, true);
  }
}
