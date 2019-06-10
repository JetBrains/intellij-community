package org.jetbrains.plugins.textmate.language.syntax.selector;

import com.google.common.base.Splitter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.getOccurrenceCount;

public class TextMateSelectorParser {
  private static final Logger LOG = Logger.getInstance(TextMateSelectorParser.class);
  private static final int NESTING_WEIGH_INITIAL = 100;
  private static final int BASE_WEIGH = NESTING_WEIGH_INITIAL * 10;
  private static final Splitter SELECTOR_SPLITTER = Splitter.on(' ').trimResults().omitEmptyStrings();

  private final List<TextMateSelectorToken> myTokens;
  private final String myHighlightingSelector;
  private int myIndex = 0;

  TextMateSelectorParser(String highlightingSelector) {
    myTokens = TextMateSelectorLexer.tokenize(highlightingSelector);
    myHighlightingSelector = highlightingSelector;
  }

  @Nullable
  public Node parse() {
    Node result = parseSelectorList();
    if (!eof()) {
      LOG.error("Cannot parse highlighting selector: " + myHighlightingSelector);
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

  interface Node {
    TextMateWeigh weigh(@NotNull String scope);
  }

  private static class Selector implements Node {
    private final String selector;

    Selector(String selector) {
      this.selector = selector;
    }

    @Override
    public TextMateWeigh weigh(@NotNull String scope) {
      if (scope.startsWith(selector)) {
        return new TextMateWeigh(BASE_WEIGH - getOccurrenceCount(scope, '.') + getOccurrenceCount(selector, '.'),
                                 TextMateWeigh.Priority.NORMAL);
      }
      return TextMateWeigh.ZERO;
    }
  }

  private static class ScopeSelector implements Node {
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
    public TextMateWeigh weigh(@NotNull String scope) {
      for (Node exclusion : exclusions) {
        if (exclusion.weigh(scope).weigh > 0) {
          return TextMateWeigh.ZERO;
        }
      }

      Stack<Node> highlightingSelectors = new Stack<>(children);
      if (highlightingSelectors.isEmpty()) {
        highlightingSelectors.push(new Selector(""));
      }
      Stack<String> targetSelectors = new Stack<>();
      ContainerUtil.addAll(targetSelectors, SELECTOR_SPLITTER.split(scope));

      String currentTargetSelector = targetSelectors.pop();
      Node currentHighlightingSelector = highlightingSelectors.peek();

      int nestingWeigh = NESTING_WEIGH_INITIAL;
      int result = 0;
      while (!highlightingSelectors.empty() && currentTargetSelector != null) {
        TextMateWeigh weigh = currentHighlightingSelector instanceof Selector
                              ? currentHighlightingSelector.weigh(currentTargetSelector)
                              : currentHighlightingSelector.weigh(scope);
        if (weigh.weigh > 0) {
          result += weigh.weigh * nestingWeigh;
          highlightingSelectors.pop();
          if (!highlightingSelectors.empty()) {
            currentHighlightingSelector = highlightingSelectors.peek();
          }
        }
        nestingWeigh--;
        currentTargetSelector = !highlightingSelectors.empty() && !targetSelectors.empty() ? targetSelectors.pop() : null;
      }
      if (!highlightingSelectors.empty()) {
        return TextMateWeigh.ZERO;
      }
      return new TextMateWeigh(!startMatch || targetSelectors.isEmpty() ? result : 0, priority);
    }
  }

  static class SelectorList implements Node {
    private final List<Node> children;

    SelectorList(List<Node> children) {
      this.children = children;
    }

    @Override
    public TextMateWeigh weigh(@NotNull String scope) {
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

  static class Conjunction implements Node {
    private final List<Node> children;

    Conjunction(List<Node> children) {
      this.children = children;
    }

    @Override
    public TextMateWeigh weigh(@NotNull String scope) {
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
