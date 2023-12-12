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

typealias VSCodeExtensionLanguageId = String

fun readVSCBundle(resourceLoader: (relativePath: String) -> InputStream?): TextMateBundleReader? {
  return resourceLoader(Constants.PACKAGE_JSON_NAME)?.buffered()?.let { packageJsonStream ->
    val jsonReader = JsonPlistReader.createJsonReader().registerKotlinModule()
    val extension = jsonReader.readValue(packageJsonStream, VSCodeExtension::class.java)
    VSCBundleReader(extension, resourceLoader)
  }
}

private class VSCBundleReader(private val extension: VSCodeExtension,
                              private val resourceLoader: (relativePath: String) -> InputStream?) : TextMateBundleReader {
  override val bundleName: String = extension.name

  private val jsonReader by lazy {
    JsonPlistReader.createJsonReader().registerKotlinModule()
  }

  private val languages: Map<VSCodeExtensionLanguageId, VSCodeExtensionLanguage> by lazy {
    extension.contributes.languages.associateBy { language -> language.id }
  }

  private val languageToScope: Map<VSCodeExtensionLanguageId, TextMateScopeName> by lazy {
    extension.contributes.grammars.mapNotNull { grammar -> grammar.language?.let { it to grammar.scopeName } }.toMap()
  }

  private val embeddedLanguages: Map<VSCodeExtensionLanguageId, Collection<TextMateScopeName>> by lazy {
    buildMap<VSCodeExtensionLanguageId, MutableSet<TextMateScopeName>> {
      extension.contributes.grammars.map { grammar ->
        grammar.embeddedLanguages.forEach { (scopeName, languageId) ->
          getOrPut(languageId) { HashSet() }.add(scopeName)
        }
      }
    }
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
        resourceLoader(path)?.buffered()?.let { inputStream ->
          val configuration = jsonReader.readValue(inputStream,
                                                   VSCodeExtensionLanguageConfiguration::class.java)
          val highlightingPairs = readBrackets(configuration.brackets).takeIf { it.isNotEmpty() }
          val smartTypingPairs = configuration.autoClosingPairs.map {
            TextMateAutoClosingPair(it.open, it.close, it.notIn)
          }.toSet().takeIf { it.isNotEmpty() }
          val surroundingPairs = configuration.surroundingPairs.map {
            TextMateBracePair(it.open, it.close)
          }.toSet().takeIf { it.isNotEmpty() }
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

data class VSCodeExtensionLanguage(val id: VSCodeExtensionLanguageId,
                                   val filenames: List<String> = emptyList(),
                                   val extensions: List<String> = emptyList(),
                                   val aliases: List<String> = emptyList(),
                                   val filenamePatterns: List<String> = emptyList(),
                                   val configuration: String?,
                                   val firstLine: String?)

data class VSCodeExtensionGrammar(val language: VSCodeExtensionLanguageId?,
                                  val scopeName: String,
                                  val path: String,
                                  val embeddedLanguages: Map<TextMateScopeName, VSCodeExtensionLanguageId> = emptyMap())

data class VSCodeExtensionSnippet(val language: VSCodeExtensionLanguageId,
                                  val path: String)

data class VSCodeExtensionContributes(val languages: List<VSCodeExtensionLanguage> = emptyList(),
                                      val grammars: List<VSCodeExtensionGrammar> = emptyList(),
                                      val snippets: List<VSCodeExtensionSnippet> = emptyList())

data class VSCodeExtension(val name: String,
                           val contributes: VSCodeExtensionContributes)

data class VSCodeExtensionLanguageConfiguration(val brackets: List<List<String>> = emptyList(),
                                                val autoClosingPairs: List<VSCodeExtensionAutoClosingPairs> = emptyList(),
                                                val autoCloseBefore: String?,
                                                val surroundingPairs: List<VSCodeExtensionSurroundingPairs> = emptyList(),
                                                val comments: VSCodeExtensionComments,
                                                val indentationRules: VSCodeExtensionIndentationRules?,
                                                val onEnterRules: List<OnEnterRule> = emptyList())

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
data class VSCodeExtensionAutoClosingPairs(val open: String, val close: String, val notIn: EnumSet<TextMateStandardTokenType>?)

class VSCodeExtensionAutoClosingPairsDeserializer(vc: Class<*>?) : StdDeserializer<VSCodeExtensionAutoClosingPairs>(vc) {
  @Suppress("unused")
  constructor() : this(null)

  override fun deserialize(p: JsonParser, ctxt: DeserializationContext): VSCodeExtensionAutoClosingPairs {
    return when (val node: JsonNode = p.codec.readTree(p)) {
      is ArrayNode -> VSCodeExtensionAutoClosingPairs(node.get(0).asText(), node.get(1).asText(), null)
      is ObjectNode -> VSCodeExtensionAutoClosingPairs(node["open"].asText(),
                                                       node["close"].asText(),
                                                       (node["notIn"] as? ArrayNode)?.mapNotNull {
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

data class VSCodeExtensionComments(val lineComment: String?,
                                   val blockComment: List<String> = emptyList())

@JsonDeserialize(using = VSCodeExtensionIndentationRulesDeserializer::class)
data class VSCodeExtensionIndentationRules(val increaseIndentPattern: String?,
                                           val decreaseIndentPattern: String?,
                                           val indentNextLinePattern: String?,
                                           val unIndentedLinePattern: String?)


class VSCodeExtensionIndentationRulesDeserializer(vc: Class<*>?) : StdDeserializer<VSCodeExtensionIndentationRules>(vc) {
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
    return when (val child = node[name]) {
      is TextNode -> child.asText()
      is ObjectNode -> child["pattern"].asText()
      null -> null
      else -> error("unexpected indentationRules node")
    }
  }
}

class TextRuleDeserializer(vc: Class<*>?) : StdDeserializer<TextRule>(vc) {
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