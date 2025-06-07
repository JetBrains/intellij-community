package org.jetbrains.plugins.textmate.language.syntax

import kotlinx.collections.immutable.persistentMapOf
import org.jetbrains.plugins.textmate.Constants
import org.jetbrains.plugins.textmate.language.TextMateInterner
import org.jetbrains.plugins.textmate.plist.PListValue
import org.jetbrains.plugins.textmate.plist.Plist
import org.jetbrains.plugins.textmate.update
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.fetchAndIncrement
import kotlin.math.max

private typealias RuleId = Int
private typealias ReferenceRuleId = Int

class TextMateSyntaxTableBuilder(private val interner: TextMateInterner) {
  private val currentRuleId = AtomicInt(0)
  private val syntaxNodes = AtomicReference(persistentMapOf<CharSequence, SyntaxRawNode>())

  /**
   * Append a table with new syntax rules to support the new language.
   *
   * @param plist Plist represented a syntax file (*.tmLanguage) of the target language.
   * @return language scope root name
   */
  fun addSyntax(plist: Plist): CharSequence? {
    val topLevelNode = loadRealNode(plist, null)
    val scopeName = topLevelNode.scopeName
    if (scopeName != null) {
      syntaxNodes.update {
        it.put(scopeName, topLevelNode)
      }
    }
    return scopeName
  }

  fun build(): TextMateSyntaxTableCore {
    val ruleIdToReferenceRuleId = mutableMapOf<RuleId, ReferenceRuleId>()
    val compiledRules = mutableMapOf<RuleId, SyntaxNodeDescriptor>()

    val rules = mutableMapOf<CharSequence, SyntaxNodeDescriptor>()
    val syntaxTable = TextMateSyntaxTableCore(rules = rules)
    val syntaxNodes = syntaxNodes.load()
    syntaxNodes.forEach { (scopeName, nodeBuilder) ->
      nodeBuilder.compile(syntaxNodes,
                          compiledRules,
                          ruleIdToReferenceRuleId,
                          syntaxTable)?.let { compiledNode ->
        rules[scopeName] = compiledNode
      }
    }
    val rulesRepository = arrayOfNulls<SyntaxNodeDescriptor?>(ruleIdToReferenceRuleId.size)
    ruleIdToReferenceRuleId.entries.forEach {
      rulesRepository[it.value] = compiledRules[it.key]
    }
    syntaxTable.setRulesRepository(rulesRepository)
    return syntaxTable
  }

  private fun loadRealNode(plist: Plist, parentBuilder: SyntaxRawNode?): SyntaxRawNodeImpl {
    val scopeNamePlistValue: PListValue? = plist.getPlistValue(Constants.StringKey.SCOPE_NAME.value)
    val scopeNameValue: String? = scopeNamePlistValue?.string
    val scopeName: CharSequence? = if (scopeNameValue != null) interner.intern(scopeNameValue) else null

    val ruleId = currentRuleId.fetchAndIncrement()
    val result = SyntaxRawNodeImpl(ruleId, parentBuilder, scopeName)
    for (entry in plist.entries()) {
      val pListValue: PListValue? = entry.value
      if (pListValue != null) {
        val key: String = entry.key
        val stringKey = Constants.StringKey.fromName(key)
        if (stringKey != null) {
          pListValue.string?.let { stringValue ->
            result.setStringAttribute(stringKey, interner.intern(stringValue))
          }
          continue
        }
        val captureKey = Constants.CaptureKey.fromName(key)
        if (captureKey != null) {
          loadCaptures(pListValue.plist, result)?.let { captures ->
            result.setCaptures(captureKey, captures)
          }
          continue
        }

        when {
          Constants.REPOSITORY_KEY.equals(key, ignoreCase = true) -> {
            pListValue.plist.entries().forEach { (key, value) ->
              result.appendRepository(key, loadNestedSyntax(value.plist, result))
            }
          }
          Constants.PATTERNS_KEY.equals(key, ignoreCase = true) -> {
            pListValue.array.forEach { value ->
              result.addChild(loadNestedSyntax(value.plist, result))
            }
          }
          Constants.INJECTIONS_KEY.equals(key, ignoreCase = true) -> {
            pListValue.plist.entries().forEach { (key, value) ->
              result.addInjection(key, loadRealNode(value.plist, result))
            }
          }
        }
      }
    }
    return result
  }

  private fun loadNestedSyntax(plist: Plist, parentBuilder: SyntaxRawNodeImpl): SyntaxRawNode {
    val include = plist.getPlistValue(Constants.INCLUDE_KEY)?.string
    return if (include != null) {
      SyntaxIncludeRawNode(currentRuleId.fetchAndIncrement(), parentBuilder, include)
    }
    else {
      loadRealNode(plist, parentBuilder)
    }
  }

  private fun loadCaptures(captures: Plist, parent: SyntaxRawNode?): Array<TextMateRawCapture?>? {
    val map = mutableMapOf<Int, TextMateRawCapture?>()
    var maxGroupIndex = -1
    captures.entries().forEach { (key, value) ->
      key.toIntOrNull()?.let { index ->
        val captureDict = value.plist
        val captureName = captureDict.getPlistValue(Constants.NAME_KEY)
        if (captureName != null) {
          map[index] = TextMateRawCapture.Name(interner.intern(captureName.string.orEmpty()))
        }
        else {
          map[index] = TextMateRawCapture.Rule(loadRealNode(captureDict, parent))
        }
        maxGroupIndex = max(maxGroupIndex, index)
      }
    }
    if (maxGroupIndex < 0 || map.isEmpty()) {
      return null
    }
    val result = arrayOfNulls<TextMateRawCapture>(maxGroupIndex + 1)
    map.entries.forEach { e -> result[e.key] = e.value }
    return result
  }
}

private sealed class TextMateRawCapture {
  class Name(val name: CharSequence) : TextMateRawCapture()
  class Rule(val node: SyntaxRawNode) : TextMateRawCapture()
}

private interface SyntaxRawNode {
  val ruleId: Int
  val parent: SyntaxRawNode?

  fun findInRepository(scopeName: String, topLevelRules: Map<CharSequence, SyntaxRawNode>): SyntaxRawNode?

  fun compile(
    topLevelNodes: Map<CharSequence, SyntaxRawNode>,
    compiledNodes: MutableMap<Int, SyntaxNodeDescriptor>,
    ruleIdToReferenceRuleId: MutableMap<RuleId, ReferenceRuleId>,
    syntaxTable: TextMateSyntaxTableCore,
  ): SyntaxNodeDescriptor?
}

private class SyntaxIncludeRawNode(
  override val ruleId: Int,
  override val parent: SyntaxRawNode,
  private val include: String,
) : SyntaxRawNode {
  override fun findInRepository(scopeName: String, topLevelRules: Map<CharSequence, SyntaxRawNode>): SyntaxRawNode? {
    return resolveInclude(topLevelRules)?.findInRepository(scopeName, topLevelRules)
  }

  override fun compile(
    topLevelNodes: Map<CharSequence, SyntaxRawNode>,
    compiledNodes: MutableMap<Int, SyntaxNodeDescriptor>,
    ruleIdToReferenceRuleId: MutableMap<RuleId, ReferenceRuleId>,
    syntaxTable: TextMateSyntaxTableCore,
  ): SyntaxNodeDescriptor? {
    val resolvedNode = resolveInclude(topLevelNodes)
    return resolvedNode?.let { resolvedNode ->
      val compiledNode = compiledNodes[resolvedNode.ruleId]
      val referenceRuleId = ruleIdToReferenceRuleId[resolvedNode.ruleId]
      when {
        compiledNode != null -> {
          // an optimization, reference directly already compiled nodes to save on SyntaxNodeReferenceDescriptor object
          compiledNode
        }
        referenceRuleId != null -> {
          SyntaxNodeReferenceDescriptor(referenceRuleId, syntaxTable)
        }
        else -> {
          val referenceRuleId = ruleIdToReferenceRuleId.size
          ruleIdToReferenceRuleId[resolvedNode.ruleId] = referenceRuleId
          SyntaxNodeReferenceDescriptor(referenceRuleId, syntaxTable)
        }
      }
    }
  }

  private fun resolveInclude(topLevelNodes: Map<CharSequence, SyntaxRawNode>): SyntaxRawNode? {
    return when {
      include.startsWith('#') -> {
        parent.findInRepository(include.substring(1), topLevelNodes)
      }
      Constants.INCLUDE_SELF_VALUE.equals(include, ignoreCase = true) || Constants.INCLUDE_BASE_VALUE.equals(include, ignoreCase = true) -> {
        generateSequence(parent, SyntaxRawNode::parent).last()
      }
      else -> {
        val i = include.indexOf('#')
        val topLevelScope = if (i >= 0) include.take(i) else include
        val scopeName = if (i >= 0) include.substring(i + 1) else ""
        if (scopeName.isNotEmpty()) {
          topLevelNodes[topLevelScope]?.findInRepository(scopeName, topLevelNodes)
        }
        else {
          topLevelNodes[topLevelScope]
        }
      }
    }
  }
}

private class SyntaxRawNodeImpl(
  override val ruleId: RuleId,
  override val parent: SyntaxRawNode?,
  val scopeName: CharSequence?,
) : SyntaxRawNode {
  private val rawCaptures: MutableMap<Constants.CaptureKey, Array<TextMateRawCapture?>> = mutableMapOf()
  private val rawStringAttributes: MutableMap<Constants.StringKey, CharSequence> = mutableMapOf()
  private val rawInjections: MutableList<Pair<String, SyntaxRawNode>> = mutableListOf()
  private val rawChildren: MutableList<SyntaxRawNode> = mutableListOf()
  val repository: MutableMap<String, SyntaxRawNode> = mutableMapOf()

  override fun findInRepository(scopeName: String, topLevelRules: Map<CharSequence, SyntaxRawNode>): SyntaxRawNode? {
    return repository[scopeName] ?: parent?.findInRepository(scopeName, topLevelRules)
  }

  fun addChild(descriptor: SyntaxRawNode) {
    rawChildren.add(descriptor)
  }

  fun addInjection(selector: String, descriptor: SyntaxRawNode) {
    rawInjections.add(selector to descriptor)
  }

  fun setStringAttribute(key: Constants.StringKey, value: CharSequence) {
    rawStringAttributes[key] = value
  }

  fun setCaptures(key: Constants.CaptureKey, captures: Array<TextMateRawCapture?>) {
    rawCaptures[key] = captures
  }

  fun appendRepository(key: String, descriptor: SyntaxRawNode) {
    repository[key] = descriptor
  }

  override fun compile(
    topLevelNodes: Map<CharSequence, SyntaxRawNode>,
    compiledNodes: MutableMap<RuleId, SyntaxNodeDescriptor>,
    ruleIdToReferenceRuleId: MutableMap<RuleId, ReferenceRuleId>,
    syntaxTable: TextMateSyntaxTableCore,
  ): SyntaxNodeDescriptor? {
    repository.values.forEach { repositoryNode ->
      repositoryNode.compile(topLevelNodes, compiledNodes, ruleIdToReferenceRuleId, syntaxTable)?.let {
        compiledNodes[repositoryNode.ruleId] = it
      }
    }

    val captures = if (rawCaptures.isNotEmpty()) {
      val array = arrayOfNulls<Array<TextMateCapture?>>(Constants.CaptureKey.entries.size)
      rawCaptures.forEach { (key, value) ->
        array[key.ordinal] = value.map {
          when (it) {
            is TextMateRawCapture.Name -> TextMateCapture.Name(it.name)
            is TextMateRawCapture.Rule -> {
              it.node.compile(topLevelNodes, compiledNodes, ruleIdToReferenceRuleId, syntaxTable)?.let { compiledRule ->
                TextMateCapture.Rule(compiledRule)
              }
            }
            null -> null
          }
        }.toTypedArray()
      }
      array
    }
    else {
      null
    }
    val stringAttributes = if (rawStringAttributes.isNotEmpty()) {
      val array = arrayOfNulls<CharSequence>(Constants.StringKey.entries.size)
      rawStringAttributes.forEach { (key, value) ->
        array[key.ordinal] = value
      }
      array
    }
    else {
      null
    }
    val children = rawChildren.mapNotNull { child ->
      child.compile(topLevelNodes, compiledNodes, ruleIdToReferenceRuleId, syntaxTable)
    }.compactList()
    val injections = rawInjections.mapNotNull { (selector, injection) ->
      injection.compile(topLevelNodes, compiledNodes, ruleIdToReferenceRuleId, syntaxTable)?.let { compiledRule ->
        InjectionNodeDescriptor(selector, compiledRule)
      }
    }.compactList()
    val result = SyntaxNodeDescriptorImpl(scopeName = scopeName,
                                          children = children,
                                          injections = injections,
                                          captures = captures,
                                          stringAttributes = stringAttributes)
    compiledNodes[ruleId] = result
    return result
  }

  private fun <T> List<T>.compactList(): List<T> {
    return when {
      isEmpty() -> emptyList()
      size == 1 -> listOf(get(0))
      this is ArrayList -> {
        this.trimToSize()
        this
      }
      else -> this
    }
  }
}

