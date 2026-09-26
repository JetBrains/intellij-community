package org.intellij.plugins.markdown.highlighting;

import com.intellij.lang.Language;
import com.intellij.lexer.LayeredLexer;
import com.intellij.lexer.MergeFunction;
import com.intellij.lexer.MergingLexerAdapterBase;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.psi.tree.IElementType;
import org.intellij.plugins.markdown.lang.MarkdownTokenTypeSets;
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes;
import org.intellij.plugins.markdown.lang.lexer.MarkdownMergingLexer;
import org.intellij.plugins.markdown.lang.lexer.MarkdownToplevelLexer;
import org.jetbrains.annotations.Nullable;

public class MarkdownHighlightingLexer extends LayeredLexer {
  /**
   * The Markdown parser emits a separate {@link MarkdownTokenTypes#HTML_BLOCK_CONTENT} token for every line of an HTML block,
   * and {@link LayeredLexer} restarts the layer lexer on each token. Merging the whole block into a single token lets the HTML
   * lexer see multiline constructs, such as comments, as a whole.
   */
  private static final MergeFunction HTML_BLOCK_MERGE_FUNCTION = (type, originalLexer) -> {
    if (type == MarkdownTokenTypes.HTML_BLOCK_CONTENT) {
      while (originalLexer.getTokenType() == MarkdownTokenTypes.HTML_BLOCK_CONTENT
             || originalLexer.getTokenType() == MarkdownTokenTypes.EOL) {
        originalLexer.advance();
      }
    }
    return type;
  };

  public MarkdownHighlightingLexer() {
    this(getHtmlSyntaxHighlighter());
  }

  public MarkdownHighlightingLexer(@Nullable SyntaxHighlighter htmlSyntaxHighlighter) {
    super(new MergingLexerAdapterBase(new MarkdownToplevelLexer()) {
      @Override
      public MergeFunction getMergeFunction() {
        return HTML_BLOCK_MERGE_FUNCTION;
      }
    });

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
