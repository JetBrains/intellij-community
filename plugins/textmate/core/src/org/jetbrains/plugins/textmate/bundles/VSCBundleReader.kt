package org.jetbrains.plugins.textmate.bundles

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.jetbrains.plugins.textmate.Constants
import org.jetbrains.plugins.textmate.language.TextMateStandardTokenType
import org.jetbrains.plugins.textmate.language.preferences.*
import org.jetbrains.plugins.textmate.plist.CompositePlistReader
import org.jetbrains.plugins.textmate.plist.JsonPlistReader
import org.jetbrains.plugins.textmate.plist.Plist
import org.jetbrains.plugins.textmate.plist.PlistValueType
import java.io.InputStream
import java.util.*

private typealias VSCodeExtensionLanguageId = String

private val vsCodeExtensionJsonReader by lazy {
  JsonPlistReader.createJsonReader().registerKotlinModule().readerFor(VSCodeExtension::class.java)
}

private val vsCodeExtensionLanguageConfigurationJsonReader by lazy {
  JsonPlistReader.createJsonReader().registerKotlinModule().readerFor(VSCodeExtensionLanguageConfiguration::class.java)
}

fun readVSCBundle(resourceLoader: (relativePath: String) -> InputStream?): TextMateBundleReader? {
  return resourceLoader(Constants.PACKAGE_JSON_NAME)?.let { packageJsonStream ->
    val extension = vsCodeExtensionJsonReader.readValue(packageJsonStream, VSCodeExtension::class.java)
    VSCBundleReader(extension = extension, resourceLoader = resourceLoader)
  }
}

private class VSCBundleReader(private val extension: VSCodeExtension,
                              private val resourceLoader: (relativePath: String) -> InputStream?) : TextMateBundleReader {
  override val bundleName: String = extension.name

  private val languages: Map<VSCodeExtensionLanguageId, VSCodeExtensionLanguage> by lazy {
    extension.contributes.languages.associateBy { language -> language.id }
  }

  private val languageToScope: Map<VSCodeExtensionLanguageId, TextMateScopeName> by lazy {
    extension.contributes.grammars.mapNotNull { grammar -> grammar.language?.let { it to grammar.scopeName } }.toMap()
  }

  private val embeddedLanguages: Map<VSCodeExtensionLanguageId, Collection<TextMateScopeName>> by lazy {
    val map = HashMap<VSCodeExtensionLanguageId, MutableSet<TextMateScopeName>>()
    extension.contributes.grammars.map { grammar ->
      for ((scopeName, languageId) in grammar.embeddedLanguages) {
        map.computeIfAbsent(languageId) { HashSet() }.add(scopeName)
      }
    }
    map
  }

  override fun readGrammars(): Sequence<TextMateGrammar> {
    return extension.contributes.grammars.asSequence().map { grammar ->
      val plist = lazy {
        resourceLoader(grammar.path)?.let { readPlist(it.buffered(), CompositePlistReader(), grammar.path) } ?: Plist.EMPTY_PLIST
      }

      val language = languages[grammar.language]
      val fileNameMatchers = language?.let {
        it.extensions.map { extension -> TextMateFileNameMatcher.Extension(extension.removePrefix(".")) } +
        it.filenames.map { fileName -> TextMateFileNameMatcher.Name(fileName) } +
        it.filenamePatterns.map { pattern -> TextMateFileNameMatcher.Pattern(pattern) }
      } ?: emptyList()

      TextMateGrammar(fileNameMatchers = fileNameMatchers,
                      firstLinePattern = language?.firstLine,
                      plist = plist,
                      overrideName = language?.aliases?.firstOrNull(),
                      overrideScopeName = grammar.scopeName)
    }
  }

  override fun readPreferences(): Sequence<TextMatePreferences> {
    return extension.contributes.languages.asSequence().flatMap { language ->
      language.configuration?.let { path ->
        resourceLoader(path)?.let { inputStream ->
          val configuration = vsCodeExtensionLanguageConfigurationJsonReader
            .readValue(inputStream, VSCodeExtensionLanguageConfiguration::class.java)
          val highlightingPairs = readBrackets(configuration.brackets).takeIf { it.isNotEmpty() }
          val smartTypingPairs = configuration.autoClosingPairs
            .mapTo(LinkedHashSet(configuration.autoClosingPairs.size)) {
              TextMateAutoClosingPair(it.open, it.close, it.notIn)
            }
            .takeIf { it.isNotEmpty() }
          val surroundingPairs = configuration.surroundingPairs
            .mapTo(LinkedHashSet(configuration.surroundingPairs.size)) {
              TextMateBracePair(it.open, it.close)
            }
            .takeIf { it.isNotEmpty() }
          val indentationRules = IndentationRules(configuration.indentationRules?.increaseIndentPattern,
                                                  configuration.indentationRules?.decreaseIndentPattern,
                                                  configuration.indentationRules?.indentNextLinePattern,
                                                  configuration.indentationRules?.unIndentedLinePattern)
          scopesForLanguage(language.id).map { scopeName ->
            val variables = readComments(scopeName, configuration.comments)
            TextMatePreferences(scopeName = scopeName,
                                variables = variables,
                                highlightingPairs = highlightingPairs,
                                smartTypingPairs = smartTypingPairs,
                                autoCloseBefore = configuration.autoCloseBefore,
                                surroundingPairs = surroundingPairs,
                                indentationRules = indentationRules,
                                customHighlightingAttributes = null,
                                onEnterRules = configuration.onEnterRules.toSet())
          }
        }
      } ?: emptySequence()
    }
  }

  override fun readSnippets(): Sequence<TextMateSnippet> {
    return extension.contributes.snippets.asSequence().filter { snippetConfiguration ->
      snippetConfiguration.path.endsWith(".json") || snippetConfiguration.path.endsWith(".code-snippets")
    }.flatMap { snippetConfiguration ->
      resourceLoader(snippetConfiguration.path)?.buffered()?.let { inputStream ->
        readPlist(inputStream, CompositePlistReader(), snippetConfiguration.path)?.entries()?.asSequence()?.flatMap { (name, value) ->
          val valuePlist = value.plist
          val bodyValue = valuePlist.getPlistValue("body", "")
          val body = when (bodyValue.type) {
            PlistValueType.STRING -> bodyValue.string
            PlistValueType.ARRAY -> bodyValue.array.joinToString(separator = "\n") { it.string }
            else -> error("Can't parse body: $bodyValue")
          }
          valuePlist.getPlistValue("prefix")?.string?.let { key ->
            val description = valuePlist.getPlistValue(Constants.DESCRIPTION_KEY, "").string
            val snippetScopeName = valuePlist.getPlistValue(Constants.SCOPE_KEY)
            if (snippetScopeName != null) {
              sequenceOf(TextMateSnippet(key, body, snippetScopeName.string, name, description, name))
            }
            else {
              scopesForLanguage(snippetConfiguration.language).map { scopeName ->
                TextMateSnippet(key, body, scopeName, name, description, name)
              }
            }
          } ?: emptySequence()
        }
      } ?: emptySequence()
    }
  }

  private fun scopesForLanguage(languageId: VSCodeExtensionLanguageId): Sequence<TextMateScopeName> {
    return sequence {
      languageToScope[languageId]?.let { yield(it) }
      embeddedLanguages[languageId]?.let { yieldAll(it) }
    }
  }

  private fun readBrackets(pairs: List<List<String>>): Set<TextMateBracePair> {
    return pairs.mapNotNull { pair ->
      pair.takeIf { it.size == 2 }?.let {
        TextMateBracePair(it[0], it[1])
      }
    }.toSet()
  }

  private fun readComments(scopeName: TextMateScopeName, comments: VSCodeExtensionComments): List<TextMateShellVariable> {
    return buildList {
      comments.lineComment?.let {
        add(TextMateShellVariable(scopeName, Constants.COMMENT_START_VARIABLE, "${it.trim()} "))
      }
      comments.blockComment.takeIf { it.size == 2 }?.let {
        val suffix = if (comments.lineComment != null) "_2" else ""
        add(TextMateShellVariable(scopeName, Constants.COMMENT_START_VARIABLE + suffix, "${it[0].trim()} "))
        add(TextMateShellVariable(scopeName, Constants.COMMENT_END_VARIABLE + suffix, " ${it[1].trim()}"))
      }
    }
  }
}

internal data class VSCodeExtensionLanguage(
  @JvmField val id: VSCodeExtensionLanguageId,
  @JvmField val filenames: List<String> = emptyList(),
  @JvmField val extensions: List<String> = emptyList(),
  @JvmField val aliases: List<String> = emptyList(),
  @JvmField val filenamePatterns: List<String> = emptyList(),
  @JvmField val configuration: String?,
  @JvmField val firstLine: String?,
)

internal data class VSCodeExtensionGrammar(
  @JvmField val language: VSCodeExtensionLanguageId?,
  @JvmField val scopeName: String,
  @JvmField val path: String,
  @JvmField val embeddedLanguages: Map<TextMateScopeName, VSCodeExtensionLanguageId> = emptyMap(),
)

internal data class VSCodeExtensionSnippet(@JvmField val language: VSCodeExtensionLanguageId, @JvmField val path: String)

internal data class VSCodeExtensionContributes(
  @JvmField val languages: List<VSCodeExtensionLanguage> = emptyList(),
  @JvmField val grammars: List<VSCodeExtensionGrammar> = emptyList(),
  @JvmField val snippets: List<VSCodeExtensionSnippet> = emptyList(),
)

internal data class VSCodeExtension(@JvmField val name: String, @JvmField val contributes: VSCodeExtensionContributes)

internal data class VSCodeExtensionLanguageConfiguration(
  @JvmField val brackets: List<List<String>> = emptyList(),
  @JvmField val autoClosingPairs: List<VSCodeExtensionAutoClosingPairs> = emptyList(),
  @JvmField val autoCloseBefore: String?,
  @JvmField val surroundingPairs: List<VSCodeExtensionSurroundingPairs> = emptyList(),
  @JvmField val comments: VSCodeExtensionComments,
  @JvmField val indentationRules: VSCodeExtensionIndentationRules?,
  @JvmField val onEnterRules: List<OnEnterRule> = emptyList(),
)

@JsonDeserialize(using = VSCodeExtensionSurroundingPairsDeserializer::class)
data class VSCodeExtensionSurroundingPairs(val open: String, val close: String)

class VSCodeExtensionSurroundingPairsDeserializer(vc: Class<*>?) : StdDeserializer<VSCodeExtensionSurroundingPairs>(vc) {
  @Suppress("unused")
  constructor() : this(null)

  override fun deserialize(p: JsonParser, ctxt: DeserializationContext): VSCodeExtensionSurroundingPairs {
    return when (val node: JsonNode = p.codec.readTree(p)) {
      is ArrayNode -> VSCodeExtensionSurroundingPairs(node.get(0).asText(), node.get(1).asText())
      is ObjectNode -> VSCodeExtensionSurroundingPairs(node["open"].asText(), node["close"].asText())
      else -> error("unexpected surroundingPairs node")
    }
  }
}

@JsonDeserialize(using = VSCodeExtensionAutoClosingPairsDeserializer::class)
internal data class VSCodeExtensionAutoClosingPairs(
  @JvmField val open: String,
  @JvmField val close: String,
  @JvmField val notIn: EnumSet<TextMateStandardTokenType>?,
)

internal class VSCodeExtensionAutoClosingPairsDeserializer(vc: Class<*>?) : StdDeserializer<VSCodeExtensionAutoClosingPairs>(vc) {
  @Suppress("unused")
  constructor() : this(null)

  override fun deserialize(p: JsonParser, ctxt: DeserializationContext): VSCodeExtensionAutoClosingPairs {
    return when (val node: JsonNode = p.codec.readTree(p)) {
      is ArrayNode -> VSCodeExtensionAutoClosingPairs(node.get(0).asText(), node.get(1).asText(), null)
      is ObjectNode -> VSCodeExtensionAutoClosingPairs(open = node["open"].asText(),
                                                       close = node["close"].asText(),
                                                       notIn = (node["notIn"] as? ArrayNode)?.mapNotNull {
                                                         when (it.asText()) {
                                                           "string" -> TextMateStandardTokenType.STRING
                                                           "comment" -> TextMateStandardTokenType.COMMENT
                                                           else -> null
                                                         }
                                                       }?.let { EnumSet.copyOf(it) })
      else -> error("unexpected autoClosingPairs node")
    }
  }
}

internal data class VSCodeExtensionComments(@JvmField val lineComment: String?, @JvmField val blockComment: List<String> = emptyList())

@JsonDeserialize(using = VSCodeExtensionIndentationRulesDeserializer::class)
internal data class VSCodeExtensionIndentationRules(
  @JvmField val increaseIndentPattern: String?,
  @JvmField val decreaseIndentPattern: String?,
  @JvmField val indentNextLinePattern: String?,
  @JvmField val unIndentedLinePattern: String?,
)

internal class VSCodeExtensionIndentationRulesDeserializer(vc: Class<*>?) : StdDeserializer<VSCodeExtensionIndentationRules>(vc) {
  @Suppress("unused")
  constructor() : this(null)

  override fun deserialize(p: JsonParser, ctxt: DeserializationContext): VSCodeExtensionIndentationRules {
    val node: JsonNode = p.codec.readTree(p)
    return VSCodeExtensionIndentationRules(
      increaseIndentPattern = readPattern(node, "increaseIndentPattern"),
      decreaseIndentPattern = readPattern(node, "decreaseIndentPattern"),
      indentNextLinePattern = readPattern(node, "indentNextLinePattern"),
      unIndentedLinePattern = readPattern(node, "unIndentedLinePattern"))
  }

  private fun readPattern(node: JsonNode, name: String): String? {
    return when (val child = node.get(name)) {
      is TextNode -> child.asText()
      is ObjectNode -> child.get("pattern").asText()
      null -> null
      else -> error("unexpected indentationRules node")
    }
  }
}

internal class TextRuleDeserializer(vc: Class<*>?) : StdDeserializer<TextRule>(vc) {
  @Suppress("unused")
  constructor() : this(null)
  override fun deserialize(p: JsonParser, ctxt: DeserializationContext): TextRule {
    return when (val node: JsonNode = p.codec.readTree(p)) {
      is TextNode -> TextRule(node.asText())
      is ObjectNode -> TextRule(node["pattern"].asText())
      else -> error("unexpected TextRule node")
    }
  }
}