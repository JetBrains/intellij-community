/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.rest.lexer;

import com.intellij.lexer.FlexAdapter;
import com.intellij.lexer.MergingLexerAdapter;
import com.intellij.psi.tree.TokenSet;
import com.jetbrains.rest.RestTokenTypes;

/**
 * User : catherine
 */
public class RestFlexLexer extends MergingLexerAdapter {
  public static final TokenSet TOKENS_TO_MERGE = TokenSet.create(RestTokenTypes.ITALIC, RestTokenTypes.BOLD, RestTokenTypes.FIXED,
                                                           RestTokenTypes.LINE, RestTokenTypes.PYTHON_LINE,
                                                           RestTokenTypes.COMMENT, RestTokenTypes.INLINE_LINE,
                                                           RestTokenTypes.WHITESPACE);
  public RestFlexLexer() {
    super(new FlexAdapter(new _RestFlexLexer(null)), TOKENS_TO_MERGE);
  }
}
