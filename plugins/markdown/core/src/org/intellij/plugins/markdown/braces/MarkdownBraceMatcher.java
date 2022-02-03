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
package org.intellij.plugins.markdown.braces;

import com.intellij.codeInsight.highlighting.PairedBraceMatcherAdapter;
import com.intellij.lang.BracePair;
import com.intellij.lang.PairedBraceMatcher;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.intellij.plugins.markdown.lang.MarkdownLanguage;
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes;
import org.jetbrains.annotations.NotNull;

public class MarkdownBraceMatcher extends PairedBraceMatcherAdapter {

  public MarkdownBraceMatcher() {
    super(new MyPairedBraceMatcher(), MarkdownLanguage.INSTANCE);
  }

  private static class MyPairedBraceMatcher implements PairedBraceMatcher {

    @Override
    public BracePair @NotNull [] getPairs() {
      return new BracePair[]{
        new BracePair(MarkdownTokenTypes.LPAREN, MarkdownTokenTypes.RPAREN, false),
        new BracePair(MarkdownTokenTypes.LBRACKET, MarkdownTokenTypes.RBRACKET, false),
        new BracePair(MarkdownTokenTypes.LT, MarkdownTokenTypes.GT, false),
        new BracePair(MarkdownTokenTypes.CODE_FENCE_START, MarkdownTokenTypes.CODE_FENCE_END, true)
      };
    }

    @Override
    public boolean isPairedBracesAllowedBeforeType(@NotNull IElementType lbraceType, IElementType type) {
      return type == null ||
             type == MarkdownTokenTypes.WHITE_SPACE ||
             type == MarkdownTokenTypes.EOL ||
             type == MarkdownTokenTypes.RPAREN ||
             type == MarkdownTokenTypes.RBRACKET ||
             type == MarkdownTokenTypes.GT;
    }

    @Override
    public int getCodeConstructStart(PsiFile file, int openingBraceOffset) {
      return openingBraceOffset;
    }
  }
}
