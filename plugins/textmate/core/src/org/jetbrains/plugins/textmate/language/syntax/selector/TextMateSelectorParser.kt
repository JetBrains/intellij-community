package org.jetbrains.plugins.textmate.language.syntax.selector

import org.jetbrains.plugins.textmate.getLogger
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateScope
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorToken.PriorityToken
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorToken.SelectorToken
import org.jetbrains.plugins.textmate.logging.TextMateLogger

class TextMateSelectorParser internal constructor(private val myHighlightingSelector: CharSequence) {
  private val myTokens: List<TextMateSelectorToken> = TextMateSelectorLexer.tokenize(myHighlightingSelector)
  private var myIndex = 0

  companion object {
    private val LOG: TextMateLogger = getLogger(TextMateSelectorParser::class)
    private const val NESTING_WEIGH_INITIAL = 100
    private const val BASE_WEIGH: Int = NESTING_WEIGH_INITIAL * 10
  }

  fun parse(): Node? {
    val result = parseSelectorList()
    if (!eof()) {
      LOG.error { "Cannot parse highlighting selector: $myHighlightingSelector" }
    }
    return result
  }

  private fun parseSelectorList(): Node? {
    val node = parseConjunction()
    if (node == null || getCurrentToken() != TextMateSelectorToken.COMMA) {
      return node
    }
    return SelectorList(buildList {
      add(node)
      while (getCurrentToken() == TextMateSelectorToken.COMMA) {
        advance()
        val child = parseConjunction()
        if (child == null) break
        add(child)
      }
    })
  }

  private fun parseConjunction(): Node? {
    val node = parseScopeSelector()
    if (node == null || getCurrentToken() != TextMateSelectorToken.PIPE) {
      return node
    }
    return Conjunction(buildList {
      add(node)
      while (getCurrentToken() == TextMateSelectorToken.PIPE) {
        advance()
        val child = parseScopeSelector()
        if (child == null) break
        add(child)
      }
    })
  }

  private fun parseScopeSelector(): Node? {
    val token = getCurrentToken()
    val priority = if (token is PriorityToken) {
      advance()
      token.priority
    }
    else {
      TextMateWeigh.Priority.NORMAL
    }
    val startMatch = getCurrentToken() == TextMateSelectorToken.HAT
    if (startMatch) {
      advance()
    }
    val children = mutableListOf<Node>()
    val exclusions = mutableListOf<Node>()
    var next = parseSelector()
    while (next != null) {
      children.add(next)
      next = parseSelector()
    }

    while (getCurrentToken() == TextMateSelectorToken.MINUS) {
      advance()
      val exclusion = parseScopeSelector()
      if (exclusion != null) {
        exclusions.add(exclusion)
      }
    }
    return if (children.isNotEmpty() || exclusions.isNotEmpty()) {
      ScopeSelector(children, exclusions, startMatch, priority)
    }
    else {
      null
    }
  }

  private fun parseSelector(): Node? {
    val token = getCurrentToken()
    if (token == TextMateSelectorToken.LPAREN) {
      advance()
      val result = parseSelectorList()
      if (getCurrentToken() == TextMateSelectorToken.RPAREN) {
        advance()
      }
      return result
    }
    return if (token is SelectorToken) {
      advance()
      Selector(token.text)
    }
    else {
      null
    }
  }

  private fun getCurrentToken(): TextMateSelectorToken? {
    return if (myIndex < myTokens.size) {
      myTokens[myIndex]
    }
    else {
      null
    }
  }

  private fun advance() {
    myIndex++
  }

  private fun eof(): Boolean {
    return myIndex >= myTokens.size
  }

  interface Node {
    fun weigh(scope: TextMateScope): TextMateWeigh
  }

  private class Selector(private val selector: String) : Node {
    override fun weigh(scope: TextMateScope): TextMateWeigh {
      val scopeName = scope.scopeName
      if (scopeName != null && (selector.isEmpty() ||
                                scopeName.startsWith("$selector.") ||
                                scopeName.contentEquals(selector))
      ) {
        return TextMateWeigh(BASE_WEIGH - scope.dotsCount + selector.count { it == '.' },
                             TextMateWeigh.Priority.NORMAL)
      }
      return TextMateWeigh.ZERO
    }
  }

  private class ScopeSelector(
    private val children: List<Node>,
    private val exclusions: List<Node>,
    private val startMatch: Boolean,
    private val priority: TextMateWeigh.Priority
  ) : Node {
    override fun weigh(scope: TextMateScope): TextMateWeigh {
      for (exclusion in exclusions) {
        if (exclusion.weigh(scope).weigh > 0) {
          return TextMateWeigh.ZERO
        }
      }

      if (scope.level > 100) {
        return TextMateWeigh.ZERO
      }

      val highlightingSelectors = ArrayDeque<Node>()
      for (child in children) {
        highlightingSelectors.addLast(child)
      }
      if (highlightingSelectors.isEmpty()) {
        highlightingSelectors.addLast(Selector(""))
      }

      var currentTargetSelector: TextMateScope? = scope
      var currentHighlightingSelector = highlightingSelectors.last()

      var nestingWeigh: Int = NESTING_WEIGH_INITIAL
      var result = 0
      while (!highlightingSelectors.isEmpty() && currentTargetSelector != null) {
        val weigh = if (currentHighlightingSelector is Selector) {
          currentHighlightingSelector.weigh(currentTargetSelector)
        }
        else {
          currentHighlightingSelector.weigh(scope)
        }
        if (weigh.weigh > 0) {
          result += weigh.weigh * nestingWeigh
          highlightingSelectors.removeLast()
          if (!highlightingSelectors.isEmpty()) {
            currentHighlightingSelector = highlightingSelectors.last()
          }
        }
        nestingWeigh--
        currentTargetSelector = currentTargetSelector.parent
      }
      if (!highlightingSelectors.isEmpty()) {
        return TextMateWeigh.ZERO
      }
      return TextMateWeigh(if (!startMatch || currentTargetSelector == null || currentTargetSelector.isEmpty) {
        result
      } else {
        0
      }, priority)
    }
  }

  private class SelectorList(private val children: List<Node>) : Node {
    override fun weigh(scope: TextMateScope): TextMateWeigh {
      return children.maxOfOrNull { it.weigh(scope) } ?: TextMateWeigh.ZERO
    }
  }

  private class Conjunction(private val children: List<Node>) : Node {
    override fun weigh(scope: TextMateScope): TextMateWeigh {
      return children.map { it.weigh(scope) }.firstOrNull { it.weigh > 0 } ?: TextMateWeigh.ZERO
    }
  }
}
