package org.jetbrains.plugins.textmate.language.syntax

import kotlinx.collections.immutable.persistentMapOf
import org.jetbrains.plugins.textmate.Constants
import org.jetbrains.plugins.textmate.language.TextMateInterner
import org.jetbrains.plugins.textmate.language.TextMateLanguageDescriptor
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
  private val syntaxNodes = AtomicReference(persistentMapOf<CharSequence, RawLanguageDescriptor>())

  /**
   * Append a table with new syntax rules to support the new language.
   *
   * @param plist Plist represented a syntax file (*.tmLanguage) of the target language.
   * @return language scope root name
   */
  fun addSyntax(plist: Plist): CharSequence? {
    return loadLanguageDescriptor(plist)?.let { languageDescriptor ->
      syntaxNodes.update {
        it.put(languageDescriptor.scopeName, languageDescriptor)
      }
      languageDescriptor.scopeName
    }
  }

  fun build(): TextMateSyntaxTableCore {
    val ruleIdToReferenceRuleId = mutableMapOf<RuleId, ReferenceRuleId>()
    val compiledRules = mutableMapOf<RuleId, SyntaxNodeDescriptor>()

    val languageDescriptors = mutableMapOf<CharSequence, TextMateLanguageDescriptor>()
    val syntaxTable = TextMateSyntaxTableCore(languageDescriptors = languageDescriptors)
    val syntaxNodes = syntaxNodes.load()
    syntaxNodes.forEach { (scopeName, rawLanguageDescriptor) ->
      rawLanguageDescriptor.compile(syntaxNodes, compiledRules, ruleIdToReferenceRuleId, syntaxTable)?.let { compiledNode ->
        languageDescriptors[scopeName] = compiledNode
      }
    }
    val rulesRepository = arrayOfNulls<SyntaxNodeDescriptor?>(ruleIdToReferenceRuleId.size)
    ruleIdToReferenceRuleId.entries.forEach {
      rulesRepository[it.value] = compiledRules[it.key]
    }
    syntaxTable.setRulesRepository(rulesRepository)
    return syntaxTable
  }

  private fun loadLanguageDescriptor(plist: Plist): RawLanguageDescriptor? {
    val scopeNamePlistValue: PListValue? = plist.getPlistValue(Constants.StringKey.SCOPE_NAME.value)
    val scopeNameValue: String? = scopeNamePlistValue?.string

    return if (scopeNameValue != null) {
      val rootSyntaxRawNode = loadRealNode(plist, null)
      val rawInjections = plist.getPlistValue(Constants.INJECTIONS_KEY)?.plist?.entries()?.map { (key, value) ->
        key to loadRealNode(value.plist, rootSyntaxRawNode)
      } ?: emptyList()
      return RawLanguageDescriptor(scopeName = interner.intern(scopeNameValue),
                                   rootSyntaxRawNode = rootSyntaxRawNode,
                                   rawInjections = rawInjections)
    }
    else {
      null
    }
  }

  private fun loadRealNode(plist: Plist, parentBuilder: SyntaxRawNode?): SyntaxRawNodeImpl {
    val ruleId = currentRuleId.fetchAndIncrement()
    val result = SyntaxRawNodeImpl(ruleId, parentBuilder)
    for (entry in plist.entries()) {
      val pListValue: PListValue = entry.value
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

private class RawLanguageDescriptor(
  val scopeName: CharSequence,
  val rootSyntaxRawNode: SyntaxRawNode,
  val rawInjections: List<Pair<String, SyntaxRawNode>>
) {

  fun compile(
    topLevelNodes: Map<CharSequence, RawLanguageDescriptor>,
    compiledNodes: MutableMap<Int, SyntaxNodeDescriptor>,
    ruleIdToReferenceRuleId: MutableMap<RuleId, ReferenceRuleId>,
    syntaxTable: TextMateSyntaxTableCore,
  ): TextMateLanguageDescriptor? {
    return rootSyntaxRawNode.compile(topLevelNodes, compiledNodes, ruleIdToReferenceRuleId, syntaxTable)?.let { rootNode ->
      val injections = rawInjections.mapNotNull { (selector, injection) ->
        injection.compile(topLevelNodes, compiledNodes, ruleIdToReferenceRuleId, syntaxTable)?.let { compiledRule ->
          InjectionNodeDescriptor(selector, compiledRule)
        }
      }.compactList()
      TextMateLanguageDescriptor(scopeName, rootNode, injections)
    }
  }
}

private interface SyntaxRawNode {
  val ruleId: Int
  val parent: SyntaxRawNode?

  fun findInRepository(scopeName: String, topLevelRules: Map<CharSequence, RawLanguageDescriptor>): SyntaxRawNode?

  fun compile(
    topLevelNodes: Map<CharSequence, RawLanguageDescriptor>,
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
  override fun findInRepository(scopeName: String, topLevelRules: Map<CharSequence, RawLanguageDescriptor>): SyntaxRawNode? {
    return resolveInclude(topLevelRules)?.findInRepository(scopeName, topLevelRules)
  }

  override fun compile(
    topLevelNodes: Map<CharSequence, RawLanguageDescriptor>,
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

  private fun resolveInclude(topLevelNodes: Map<CharSequence, RawLanguageDescriptor>): SyntaxRawNode? {
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
          topLevelNodes[topLevelScope]?.rootSyntaxRawNode?.findInRepository(scopeName, topLevelNodes)
        }
        else {
          topLevelNodes[topLevelScope]?.rootSyntaxRawNode
        }
      }
    }
  }
}

private class SyntaxRawNodeImpl(
  override val ruleId: RuleId,
  override val parent: SyntaxRawNode?,
) : SyntaxRawNode {
  private val rawCaptures: MutableMap<Constants.CaptureKey, Array<TextMateRawCapture?>> = mutableMapOf()
  private val rawStringAttributes: MutableMap<Constants.StringKey, CharSequence> = mutableMapOf()
  private val rawChildren: MutableList<SyntaxRawNode> = mutableListOf()
  val repository: MutableMap<String, SyntaxRawNode> = mutableMapOf()

  override fun findInRepository(scopeName: String, topLevelRules: Map<CharSequence, RawLanguageDescriptor>): SyntaxRawNode? {
    return repository[scopeName] ?: parent?.findInRepository(scopeName, topLevelRules)
  }

  fun addChild(descriptor: SyntaxRawNode) {
    rawChildren.add(descriptor)
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
    topLevelNodes: Map<CharSequence, RawLanguageDescriptor>,
    compiledNodes: MutableMap<Int, SyntaxNodeDescriptor>,
    ruleIdToReferenceRuleId: MutableMap<RuleId, ReferenceRuleId>,
    syntaxTable: TextMateSyntaxTableCore,
  ): SyntaxNodeDescriptor {
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
    val result = SyntaxNodeDescriptorImpl(children = children,
                                          captures = captures,
                                          stringAttributes = stringAttributes)
    compiledNodes[ruleId] = result
    return result
  }
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