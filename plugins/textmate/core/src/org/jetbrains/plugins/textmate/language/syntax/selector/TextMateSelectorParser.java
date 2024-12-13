package org.jetbrains.plugins.textmate.language.syntax.selector;

import com.intellij.openapi.util.text.Strings;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

public final class TextMateSelectorParser {
  private static final Logger LOG = LoggerFactory.getLogger(TextMateSelectorParser.class);
  private static final int NESTING_WEIGH_INITIAL = 100;
  private static final int BASE_WEIGH = NESTING_WEIGH_INITIAL * 10;

  private final List<TextMateSelectorToken> myTokens;
  private final CharSequence myHighlightingSelector;
  private int myIndex = 0;

  TextMateSelectorParser(CharSequence highlightingSelector) {
    myTokens = TextMateSelectorLexer.tokenize(highlightingSelector);
    myHighlightingSelector = highlightingSelector;
  }

  @Nullable
  public Node parse() {
    Node result = parseSelectorList();
    if (!eof()) {
      LOG.error("Cannot parse highlighting selector: {}", myHighlightingSelector);
    }
    return result;
  }

  @Nullable
  private Node parseSelectorList() {
    Node node = parseConjunction();
    if (node == null || getToken() != TextMateSelectorToken.COMMA) {
      return node;
    }
    List<Node> children = new ArrayList<>();
    children.add(node);
    while (getToken() == TextMateSelectorToken.COMMA) {
      advance();
      Node child = parseConjunction();
      if (child == null) break;
      children.add(child);
    }
    return new SelectorList(children);
  }

  @Nullable
  private Node parseConjunction() {
    Node node = parseScopeSelector();
    if (node == null || getToken() != TextMateSelectorToken.PIPE) {
      return node;
    }
    List<Node> children = new ArrayList<>();
    children.add(node);
    while (getToken() == TextMateSelectorToken.PIPE) {
      advance();
      Node child = parseScopeSelector();
      if (child == null) break;
      children.add(child);
    }
    return new Conjunction(children);
  }

  @Nullable
  private Node parseScopeSelector() {
    TextMateWeigh.Priority priority = TextMateWeigh.Priority.NORMAL;
    TextMateSelectorToken token = getToken();
    if (token instanceof TextMateSelectorLexer.PriorityToken) {
      advance();
      priority = ((TextMateSelectorLexer.PriorityToken)token).getPriority();
    }
    boolean startMatch = false;
    if (getToken() == TextMateSelectorToken.HAT) {
      advance();
      startMatch = true;
    }
    List<Node> children = new ArrayList<>();
    List<Node> exclusions = new ArrayList<>();
    Node next = parseSelector();
    while (next != null) {
      children.add(next);
      next = parseSelector();
    }

    while (getToken() == TextMateSelectorToken.MINUS) {
      advance();
      Node exclusion = parseScopeSelector();
      if (exclusion != null) {
        exclusions.add(exclusion);
      }
    }
    if (children.isEmpty() && exclusions.isEmpty()) {
      return null;
    }
    return new ScopeSelector(children, exclusions, startMatch, priority);
  }

  @Nullable
  private Node parseSelector() {
    TextMateSelectorToken token = getToken();
    if (token == TextMateSelectorToken.LPAREN) {
      advance();
      Node result = parseSelectorList();
      if (getToken() == TextMateSelectorToken.RPAREN) {
        advance();
      }
      return result;
    }
    if (token instanceof TextMateSelectorLexer.SelectorToken) {
      advance();
      return new Selector(((TextMateSelectorLexer.SelectorToken)token).getText());
    }
    return null;
  }

  @Nullable
  private TextMateSelectorToken getToken() {
    if (myIndex < myTokens.size()) {
      return myTokens.get(myIndex);
    }
    return null;
  }

  private void advance() {
    myIndex++;
  }

  private boolean eof() {
    return myIndex >= myTokens.size();
  }

  public interface Node {
    TextMateWeigh weigh(@NotNull TextMateScope scope);
  }

  private static final class Selector implements Node {
    @NotNull
    private final String selector;

    Selector(@NotNull String selector) {
      this.selector = selector;
    }

    @Override
    public TextMateWeigh weigh(@NotNull TextMateScope scope) {
      CharSequence scopeName = scope.getScopeName();
      if (scopeName != null && (selector.isEmpty() ||
                                startsWith(scopeName, selector + ".") ||
                                equal(scopeName, selector))) {
        return new TextMateWeigh(BASE_WEIGH - scope.getDotsCount() + Strings.countChars(selector, '.'),
                                 TextMateWeigh.Priority.NORMAL);
      }
      return TextMateWeigh.ZERO;
    }
  }

  @Contract(pure = true)
  private static boolean startsWith(@NotNull CharSequence text, @NotNull CharSequence prefix) {
    int l1 = text.length();
    int l2 = prefix.length();
    if (l1 < l2) return false;

    for (int i = 0; i < l2; i++) {
      if (text.charAt(i) != prefix.charAt(i)) return false;
    }

    return true;
  }

  private static boolean equal(@NotNull CharSequence s1, @NotNull CharSequence s2) {
    if (s1 == s2) return true;
    if (s1.length() != s2.length()) return false;
    for (int i = 0; i < s1.length(); i++) {
      if (s1.charAt(i) != s2.charAt(i)) {
        return false;
      }
    }
    return true;
  }

  private static final class ScopeSelector implements Node {
    private final List<Node> children;
    private final List<Node> exclusions;
    private final boolean startMatch;
    private final TextMateWeigh.Priority priority;

    private ScopeSelector(List<Node> children,
                          List<Node> exclusions,
                          boolean startMatch,
                          TextMateWeigh.Priority priority) {
      this.children = children;
      this.exclusions = exclusions;
      this.startMatch = startMatch;
      this.priority = priority;
    }

    @Override
    public TextMateWeigh weigh(@NotNull TextMateScope scope) {
      for (Node exclusion : exclusions) {
        if (exclusion.weigh(scope).weigh > 0) {
          return TextMateWeigh.ZERO;
        }
      }

      if (scope.getLevel() > 100) {
        return TextMateWeigh.ZERO;
      }

      Deque<Node> highlightingSelectors = new LinkedList<>();
      for (Node child : children) {
        highlightingSelectors.push(child);
      }
      if (highlightingSelectors.isEmpty()) {
        highlightingSelectors.push(new Selector(""));
      }

      TextMateScope currentTargetSelector = scope;
      Node currentHighlightingSelector = highlightingSelectors.peek();

      int nestingWeigh = NESTING_WEIGH_INITIAL;
      int result = 0;
      while (!highlightingSelectors.isEmpty() && currentTargetSelector != null) {
        TextMateWeigh weigh = currentHighlightingSelector instanceof Selector
                              ? currentHighlightingSelector.weigh(currentTargetSelector)
                              : currentHighlightingSelector.weigh(scope);
        if (weigh.weigh > 0) {
          result += weigh.weigh * nestingWeigh;
          highlightingSelectors.pop();
          if (!highlightingSelectors.isEmpty()) {
            currentHighlightingSelector = highlightingSelectors.peek();
          }
        }
        nestingWeigh--;
        currentTargetSelector = currentTargetSelector.getParent();
      }
      if (!highlightingSelectors.isEmpty()) {
        return TextMateWeigh.ZERO;
      }
      return new TextMateWeigh(!startMatch || currentTargetSelector == null || currentTargetSelector.isEmpty() ? result : 0, priority);
    }
  }

  static final class SelectorList implements Node {
    private final List<Node> children;

    SelectorList(List<Node> children) {
      this.children = children;
    }

    @Override
    public TextMateWeigh weigh(@NotNull TextMateScope scope) {
      TextMateWeigh result = TextMateWeigh.ZERO;
      for (Node child : children) {
        TextMateWeigh weigh = child.weigh(scope);
        if (weigh.compareTo(result) > 0) {
          result = weigh;
        }
      }
      return result;
    }
  }

  static final class Conjunction implements Node {
    private final List<Node> children;

    Conjunction(List<Node> children) {
      this.children = children;
    }

    @Override
    public TextMateWeigh weigh(@NotNull TextMateScope scope) {
      for (Node child : children) {
        TextMateWeigh weigh = child.weigh(scope);
        if (weigh.weigh > 0) {
          return weigh;
        }
      }
      return TextMateWeigh.ZERO;
    }
  }
}
