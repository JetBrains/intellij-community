package org.jetbrains.plugins.textmate.language.syntax.highlighting;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.TextMateService;
import org.jetbrains.plugins.textmate.language.TextMateLanguageDescriptor;
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateHighlightingLexer;
import org.jetbrains.plugins.textmate.regex.joni.JoniRegexFactory;

public class TextMateSyntaxHighlighterFactory extends SyntaxHighlighterFactory {
  private static final SyntaxHighlighter PLAIN_SYNTAX_HIGHLIGHTER = new TextMateHighlighter(null);
  private static final Logger LOG = Logger.getInstance(TextMateSyntaxHighlighterFactory.class);

  @NotNull
  @Override
  public SyntaxHighlighter getSyntaxHighlighter(@Nullable Project project, @Nullable VirtualFile virtualFile) {
    if (virtualFile == null) {
      return PLAIN_SYNTAX_HIGHLIGHTER;
    }

    TextMateService textMateService = TextMateService.getInstance();
    if (textMateService != null) {
      final TextMateLanguageDescriptor languageDescriptor = textMateService.getLanguageDescriptorByFileName(virtualFile.getName());
      if (languageDescriptor != null) {
        LOG.debug("Textmate highlighting: " + virtualFile.getPath());
        return new TextMateHighlighter(new TextMateHighlightingLexer(languageDescriptor,
                                                                     new JoniRegexFactory(),
                                                                     Registry.get("textmate.line.highlighting.limit").asInteger()));
      }
    }
    return PLAIN_SYNTAX_HIGHLIGHTER;
  }
}
