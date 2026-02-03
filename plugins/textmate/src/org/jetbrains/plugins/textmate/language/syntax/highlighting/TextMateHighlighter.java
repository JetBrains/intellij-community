package org.jetbrains.plugins.textmate.language.syntax.highlighting;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.openapi.util.text.Strings;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.TextMateService;
import org.jetbrains.plugins.textmate.language.TextMateScopeComparatorCore;
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateElementType;
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateScope;
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateCachingSelectorWeigherKt;
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorWeigher;
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorWeigherImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TextMateHighlighter extends SyntaxHighlighterBase {
  private static final PlainSyntaxHighlighter PLAIN_SYNTAX_HIGHLIGHTER = new PlainSyntaxHighlighter();

  private final @Nullable Lexer myLexer;
  private final @NotNull TextMateSelectorWeigher mySelectorWeigher =
    TextMateCachingSelectorWeigherKt.caching(new TextMateSelectorWeigherImpl());

  public TextMateHighlighter(@Nullable Lexer lexer) {
    myLexer = lexer;
  }

  @Override
  public @NotNull Lexer getHighlightingLexer() {
    return myLexer == null ? PLAIN_SYNTAX_HIGHLIGHTER.getHighlightingLexer() : myLexer;
  }

  @Override
  public TextAttributesKey @NotNull [] getTokenHighlights(IElementType tokenType) {
    if (!(tokenType instanceof TextMateElementType)) return PLAIN_SYNTAX_HIGHLIGHTER.getTokenHighlights(tokenType);
    TextMateService service = TextMateService.getInstance();
    Map<CharSequence, TextMateTextAttributesAdapter> customHighlightingColors = service.getCustomHighlightingColors();

    Set<CharSequence> highlightingRules = ContainerUtil.union(customHighlightingColors.keySet(), TextMateTheme.INSTANCE.getRules());

    TextMateScope textMateScope = trimEmbeddedScope((TextMateElementType)tokenType);
    List<CharSequence> selectors =
      ContainerUtil.reverse(new TextMateScopeComparatorCore<>(mySelectorWeigher, textMateScope, TextMateHighlighter::identity)
                                                           .sortAndFilter(highlightingRules));
    return ContainerUtil.map2Array(selectors, TextAttributesKey.class, rule -> {
      TextMateTextAttributesAdapter customTextAttributes = customHighlightingColors.get(rule);
      return customTextAttributes != null ? customTextAttributes.getTextAttributesKey(TextMateTheme.INSTANCE)
                                          : TextMateTheme.INSTANCE.getTextAttributesKey(rule);
    });
  }

  private static TextMateScope trimEmbeddedScope(TextMateElementType tokenType) {
    TextMateScope current = tokenType.getScope();
    List<CharSequence> trail = new ArrayList<>();
    while (current != null) {
      CharSequence scopeName = current.getScopeName();
      if (scopeName != null && Strings.contains(scopeName, ".embedded.")) {
        TextMateScope result = TextMateScope.EMPTY;
        for (int i = trail.size() - 1; i >= 0; i--) {
          result = result.add(trail.get(i));
        }
        return result;
      }
      trail.add(scopeName);
      current = current.getParent();
    }
    return tokenType.getScope();
  }

  private static CharSequence identity(CharSequence scope) {
    return scope;
  }
}
