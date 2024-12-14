package org.jetbrains.plugins.textmate.language.syntax.selector

import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateScope
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

class TextMateSelectorParser internal constructor(private val myHighlightingSelector: CharSequence) {
  private val myTokens: List<TextMateSelectorToken> = TextMateSelectorLexer.tokenize(myHighlightingSelector)
  private var myIndex = 0

  companion object {
    private val LOG: Logger = LoggerFactory.getLogger(TextMateSelectorParser::class.java)
    private const val NESTING_WEIGH_INITIAL = 100
    private const val BASE_WEIGH: Int = NESTING_WEIGH_INITIAL * 10
  }

  fun parse(): Node? {
    val result = parseSelectorList()
    if (!eof()) {
      LOG.error("Cannot parse highlighting selector: {}", myHighlightingSelector)
    }
    return result
  }

  private fun parseSelectorList(): Node? {
    val node = parseConjunction()
    if (node == null || this.token !== TextMateSelectorToken.COMMA) {
      return node
    }
    val children: MutableList<Node> = ArrayList<Node>()
    children.add(node)
    while (this.token === TextMateSelectorToken.COMMA) {
      advance()
      val child = parseConjunction()
      if (child == null) break
      children.add(child)
    }
    return SelectorList(children)
  }

  private fun parseConjunction(): Node? {
    val node = parseScopeSelector()
    if (node == null || this.token !== TextMateSelectorToken.PIPE) {
      return node
    }
    val children: MutableList<Node> = ArrayList<Node>()
    children.add(node)
    while (this.token === TextMateSelectorToken.PIPE) {
      advance()
      val child = parseScopeSelector()
      if (child == null) break
      children.add(child)
    }
    return Conjunction(children)
  }

  private fun parseScopeSelector(): Node? {
    var priority = TextMateWeigh.Priority.NORMAL
    val token = this.token
    if (token is TextMateSelectorLexer.PriorityToken) {
      advance()
      priority = token.priority
    }
    var startMatch = false
    if (this.token === TextMateSelectorToken.HAT) {
      advance()
      startMatch = true
    }
    val children: MutableList<Node?> = ArrayList<Node?>()
    val exclusions: MutableList<Node> = ArrayList<Node>()
    var next = parseSelector()
    while (next != null) {
      children.add(next)
      next = parseSelector()
    }

    while (this.token === TextMateSelectorToken.MINUS) {
      advance()
      val exclusion = parseScopeSelector()
      if (exclusion != null) {
        exclusions.add(exclusion)
      }
    }
    if (children.isEmpty() && exclusions.isEmpty()) {
      return null
    }
    return ScopeSelector(children, exclusions, startMatch, priority)
  }

  private fun parseSelector(): Node? {
    val token = this.token
    if (token === TextMateSelectorToken.LPAREN) {
      advance()
      val result = parseSelectorList()
      if (this.token === TextMateSelectorToken.RPAREN) {
        advance()
      }
      return result
    }
    if (token is TextMateSelectorLexer.SelectorToken) {
      advance()
      return Selector(token.text)
    }
    return null
  }

  private val token: TextMateSelectorToken?
    get() {
      if (myIndex < myTokens.size) {
        return myTokens[myIndex]
      }
      return null
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
      "".toRegex()
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
    private val children: MutableList<Node?>,
    private val exclusions: MutableList<Node>,
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

      val highlightingSelectors: Deque<Node> = LinkedList<Node>()
      for (child in children) {
        highlightingSelectors.push(child)
      }
      if (highlightingSelectors.isEmpty()) {
        highlightingSelectors.push(Selector(""))
      }

      var currentTargetSelector: TextMateScope? = scope
      var currentHighlightingSelector = highlightingSelectors.peek()

      var nestingWeigh: Int = NESTING_WEIGH_INITIAL
      var result = 0
      while (!highlightingSelectors.isEmpty() && currentTargetSelector != null) {
        val weigh = if (currentHighlightingSelector is Selector)
          currentHighlightingSelector.weigh(currentTargetSelector)
        else
          currentHighlightingSelector.weigh(scope)
        if (weigh.weigh > 0) {
          result += weigh.weigh * nestingWeigh
          highlightingSelectors.pop()
          if (!highlightingSelectors.isEmpty()) {
            currentHighlightingSelector = highlightingSelectors.peek()
          }
        }
        nestingWeigh--
        currentTargetSelector = currentTargetSelector.parent
      }
      if (!highlightingSelectors.isEmpty()) {
        return TextMateWeigh.ZERO
      }
      return TextMateWeigh(if (!startMatch || currentTargetSelector == null || currentTargetSelector.isEmpty) result else 0, priority)
    }
  }

  internal class SelectorList(private val children: MutableList<Node>) : Node {
    override fun weigh(scope: TextMateScope): TextMateWeigh {
      var result = TextMateWeigh.ZERO
      for (child in children) {
        val weigh = child.weigh(scope)
        if (weigh > result) {
          result = weigh
        }
      }
      return result
    }
  }

  internal class Conjunction(private val children: MutableList<Node>) : Node {
    override fun weigh(scope: TextMateScope): TextMateWeigh {
      for (child in children) {
        val weigh = child.weigh(scope)
        if (weigh.weigh > 0) {
          return weigh
        }
      }
      return TextMateWeigh.ZERO
    }
  }
}
