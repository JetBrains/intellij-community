/*
 * Copyright 2000-2018 JetBrains s.r.o.
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
package org.intellij.plugins.markdown.highlighting;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.IElementType;
import org.intellij.plugins.markdown.lang.MarkdownElementTypes;
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes;
import org.jetbrains.annotations.NotNull;

public class MarkdownHighlightingAnnotator implements Annotator {

  private static final SyntaxHighlighter SYNTAX_HIGHLIGHTER = new MarkdownSyntaxHighlighter();

  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    final IElementType type = element.getNode().getElementType();

    if (type == MarkdownTokenTypes.EMPH) {
      final PsiElement parent = element.getParent();
      if (parent == null) {
        return;
      }

      final IElementType parentType = parent.getNode().getElementType();
      if (parentType == MarkdownElementTypes.EMPH || parentType == MarkdownElementTypes.STRONG) {
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION).textAttributes(parentType == MarkdownElementTypes.EMPH
                                     ? MarkdownHighlighterColors.ITALIC_MARKER_ATTR_KEY
                                     : MarkdownHighlighterColors.BOLD_MARKER_ATTR_KEY).create();
      }
      return;
    }

    if (element instanceof LeafPsiElement) {
      return;
    }

    final TextAttributesKey[] tokenHighlights = SYNTAX_HIGHLIGHTER.getTokenHighlights(type);

    if (tokenHighlights.length > 0 && !MarkdownHighlighterColors.TEXT_ATTR_KEY.equals(tokenHighlights[0])
        && !tokenHighlights[0].getExternalName().equals("MARKDOWN_LIST_ITEM")) {
      holder.newSilentAnnotation(HighlightSeverity.INFORMATION).textAttributes(tokenHighlights[0]).create();
    }
  }
}
