package org.intellij.plugins.markdown.highlighting;

import com.intellij.lang.Language;
import com.intellij.lexer.LayeredLexer;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.psi.tree.IElementType;
import org.intellij.plugins.markdown.lang.MarkdownTokenTypeSets;
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes;
import org.intellij.plugins.markdown.lang.lexer.MarkdownMergingLexer;
import org.intellij.plugins.markdown.lang.lexer.MarkdownToplevelLexer;
import org.jetbrains.annotations.Nullable;

public class MarkdownHighlightingLexer extends LayeredLexer {
  public MarkdownHighlightingLexer() {
    this(getHtmlSyntaxHighlighter());
  }

  public MarkdownHighlightingLexer(@Nullable SyntaxHighlighter htmlSyntaxHighlighter) {
    super(new MarkdownToplevelLexer());

    if (htmlSyntaxHighlighter != null) {
      registerLayer(htmlSyntaxHighlighter.getHighlightingLexer(), MarkdownTokenTypes.HTML_BLOCK_CONTENT);
    }
    registerSelfStoppingLayer(
      new MarkdownMergingLexer(),
      MarkdownTokenTypeSets.INLINE_HOLDING_ELEMENT_TYPES.getTypes(),
      IElementType.EMPTY_ARRAY
    );
  }

  static @Nullable SyntaxHighlighter getHtmlSyntaxHighlighter() {
    Language htmlLanguage = Language.findLanguageByID("HTML");
    return htmlLanguage == null ? null : SyntaxHighlighterFactory.getSyntaxHighlighter(htmlLanguage, null, null);
  }
}
