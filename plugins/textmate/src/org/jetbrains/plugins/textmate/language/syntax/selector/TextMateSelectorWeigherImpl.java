package org.jetbrains.plugins.textmate.language.syntax.selector;

import org.jetbrains.annotations.NotNull;

public class TextMateSelectorWeigherImpl implements TextMateSelectorWeigher {
  @Override
  public TextMateWeigh weigh(@NotNull CharSequence scopeSelector, @NotNull CharSequence scope) {
    TextMateSelectorParser parser = new TextMateSelectorParser(scopeSelector);
    TextMateSelectorParser.Node node = parser.parse();
    if (node == null) {
      return TextMateWeigh.ZERO;
    }
    return node.weigh(scope);
  }
}