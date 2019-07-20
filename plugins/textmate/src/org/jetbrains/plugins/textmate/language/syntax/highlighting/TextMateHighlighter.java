package org.jetbrains.plugins.textmate.language.syntax.highlighting;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.ui.ColorUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.TextMateService;
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateElementType;
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorCachingWeigher;
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorWeigher;
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorWeigherImpl;
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateWeigh;

import java.awt.*;
import java.util.List;
import java.util.Map;

public class TextMateHighlighter extends SyntaxHighlighterBase {
  private static final PlainSyntaxHighlighter PLAIN_SYNTAX_HIGHLIGHTER = new PlainSyntaxHighlighter();

  @Nullable
  private final Lexer myLexer;
  private final TextMateSelectorWeigher mySelectorWeigher;

  public TextMateHighlighter(@Nullable Lexer lexer) {
    myLexer = lexer;
    mySelectorWeigher = new TextMateSelectorCachingWeigher(new TextMateSelectorWeigherImpl());
  }

  @NotNull
  @Override
  public Lexer getHighlightingLexer() {
    return myLexer == null ? PLAIN_SYNTAX_HIGHLIGHTER.getHighlightingLexer() : myLexer;
  }

  @NotNull
  @Override
  public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
    if (!(tokenType instanceof TextMateElementType)) return PLAIN_SYNTAX_HIGHLIGHTER.getTokenHighlights(tokenType);
    final TextMateService service = TextMateService.getInstance();
    final Map<String, TextMateCustomTextAttributes> customHighlightingColors = service.getCustomHighlightingColors();
    final TextMateTheme theme = service.getCurrentTheme();
    List<HighlightingRule> highlightingRules = ContainerUtil.newSmartList(new HighlightingRule(TextMateTheme.DEFAULT_ATTRIBUTES_NAME, TextMateWeigh.ZERO));
    for (String currentRule : ContainerUtil.union(customHighlightingColors.keySet(), theme.getRules())) {
      final TextMateWeigh weigh = mySelectorWeigher.weigh(currentRule, tokenType.toString());
      if (weigh.weigh > 0) {
        highlightingRules.add(new HighlightingRule(currentRule, weigh));
      }
    }
    ContainerUtil.sort(highlightingRules);

    return ContainerUtil.map2Array(highlightingRules, TextAttributesKey.class, rule -> {
      final TextMateCustomTextAttributes customTextAttributes = customHighlightingColors.get(rule.myName);
      if (customTextAttributes != null) {
        final TextAttributes textAttributes = customTextAttributes.getTextAttributes();

        final Color backgroundColor = textAttributes.getBackgroundColor();
        if (backgroundColor != null) {
          final Color defaultBackground = theme.getDefaultBackground();
          if (defaultBackground != null) {
            final double backgroundAlpha = customTextAttributes.getBackgroundAlpha();
            if (backgroundAlpha > -1) {
              textAttributes.setBackgroundColor(ColorUtil.mix(defaultBackground, backgroundColor, backgroundAlpha));
              return TextAttributesKey.createTextAttributesKey("TextMateCustomRule_" + theme.getName() + rule.myName, textAttributes);
            }
          }
        }

        return TextAttributesKey.createTextAttributesKey("TextMateCustomRule_" + rule.myName, textAttributes);
      }
      return theme.getTextAttributesKey(rule.myName);
    });
  }

  private static class HighlightingRule implements Comparable<HighlightingRule> {
    public final String myName;
    private final TextMateWeigh myWeigh;

    private HighlightingRule(String name, TextMateWeigh weigh) {
      myName = name;
      myWeigh = weigh;
    }

    @Override
    public int compareTo(@NotNull HighlightingRule rule) {
      return myWeigh.compareTo(rule.myWeigh);
    }

    @Override
    public String toString() {
      return myName;
    }
  }
}
