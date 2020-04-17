package org.jetbrains.plugins.textmate.language.syntax.highlighting;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.TextMateService;
import org.jetbrains.plugins.textmate.language.TextMateScopeComparator;
import org.jetbrains.plugins.textmate.language.TextMateScopeSelectorOwner;
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateElementType;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class TextMateHighlighter extends SyntaxHighlighterBase {
  private static final PlainSyntaxHighlighter PLAIN_SYNTAX_HIGHLIGHTER = new PlainSyntaxHighlighter();

  @Nullable
  private final Lexer myLexer;

  public TextMateHighlighter(@Nullable Lexer lexer) {
    myLexer = lexer;
  }

  @NotNull
  @Override
  public Lexer getHighlightingLexer() {
    return myLexer == null ? PLAIN_SYNTAX_HIGHLIGHTER.getHighlightingLexer() : myLexer;
  }

  @Override
  public TextAttributesKey @NotNull [] getTokenHighlights(IElementType tokenType) {
    if (!(tokenType instanceof TextMateElementType)) return PLAIN_SYNTAX_HIGHLIGHTER.getTokenHighlights(tokenType);
    TextMateService service = TextMateService.getInstance();
    Map<CharSequence, TextMateTextAttributesAdapter> customHighlightingColors = service.getCustomHighlightingColors();

    Set<HighlightingRule> highlightingRules = new HashSet<>();
    for (CharSequence currentRule : ContainerUtil.union(customHighlightingColors.keySet(), TextMateTheme.INSTANCE.getRules())) {
      highlightingRules.add(new HighlightingRule(currentRule));
    }

    return ContainerUtil.map2Array(new TextMateScopeComparator<HighlightingRule>(tokenType.toString()).sortAndFilter(highlightingRules),
                                   TextAttributesKey.class, rule -> {
        TextMateTextAttributesAdapter customTextAttributes = customHighlightingColors.get(rule.myName);
        return customTextAttributes != null ? customTextAttributes.getTextAttributesKey(TextMateTheme.INSTANCE)
                                            : TextMateTheme.INSTANCE.getTextAttributesKey(rule.myName);
      });
  }

  private static class HighlightingRule implements TextMateScopeSelectorOwner {
    private final CharSequence myName;

    private HighlightingRule(@NotNull CharSequence name) {
      myName = name;
    }

    @Override
    public @NotNull CharSequence getScopeSelector() {
      return myName;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      HighlightingRule rule = (HighlightingRule)o;
      return Objects.equals(myName, rule.myName);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myName);
    }
  }
}
