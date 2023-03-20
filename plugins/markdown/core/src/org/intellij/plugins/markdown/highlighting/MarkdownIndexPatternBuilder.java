// Copyright 2000-2018 JetBrains s.r.o.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package org.intellij.plugins.markdown.highlighting;

import com.intellij.lexer.LayeredLexer;
import com.intellij.lexer.Lexer;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.search.IndexPatternBuilder;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.intellij.plugins.markdown.lang.MarkdownElementTypes;
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MarkdownIndexPatternBuilder implements IndexPatternBuilder {
  public static final TokenSet COMMENT_TOKEN_SET = TokenSet.create(MarkdownElementTypes.LINK_COMMENT);

  @Nullable
  @Override
  public Lexer getIndexingLexer(@NotNull PsiFile file) {
    if (!(file instanceof MarkdownFile)) {
      return null;
    }

    try {
      LayeredLexer.ourDisableLayersFlag.set(Boolean.TRUE);
      return ((MarkdownFile)file).getParserDefinition().createLexer(file.getProject());
    }
    finally {
      LayeredLexer.ourDisableLayersFlag.set(null);
    }
  }

  @Nullable
  @Override
  public TokenSet getCommentTokenSet(@NotNull PsiFile file) {
    return file instanceof MarkdownFile ? COMMENT_TOKEN_SET : null;
  }

  @Override
  public int getCommentStartDelta(IElementType tokenType) {
    return 1;
  }

  @Override
  public int getCommentEndDelta(IElementType tokenType) {
    return 1;
  }
}