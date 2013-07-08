/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.lang;

import com.intellij.lexer.Lexer;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;

public interface HtmlScriptContentProvider {
  /**
   * @return instance of the <code>com.intellij.psi.tree.IElementType</code> to use in html script tag
   */
  IElementType getScriptElementType();

  /**
   * @return highlighting lexer to use in html script tag
   */
  @Nullable
  Lexer getHighlightingLexer();

  class Empty implements HtmlScriptContentProvider{
    @Override
    public IElementType getScriptElementType() {
      return null;
    }

    @Nullable
    @Override
    public Lexer getHighlightingLexer() {
      return null;
    }
  }
}
