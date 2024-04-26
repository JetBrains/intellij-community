package org.jetbrains.plugins.textmate.bundles

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
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

fun readVSCBundle(resourceLoader: (relativePath: String) -> InputStream?): TextMateBundleReader? {
  return resourceLoader(Constants.PACKAGE_JSON_NAME)?.let { packageJsonStream ->
    val extension = packageJsonStream.reader(Charsets.UTF_8).useLines { lineSequence ->
      JsonPlistReader.textmateJson.decodeFromString(VSCodeExtension.serializer(), lineSequence.removeJsonComments())
    }

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
          readPreferencesImpl(inputStream, scopesForLanguage(language.id))
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
}

internal fun readPreferencesImpl(inputStream: InputStream, scopeNames: Sequence<TextMateScopeName>): Sequence<TextMatePreferences> {
  val configuration = inputStream.reader(Charsets.UTF_8).useLines { lineSequence ->
    JsonPlistReader.textmateJson.decodeFromString(VSCodeExtensionLanguageConfiguration.serializer(), lineSequence.removeJsonComments())
  }

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
  return scopeNames.map { scopeName ->
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

@Serializable
internal data class VSCodeExtensionLanguage(
  val id: VSCodeExtensionLanguageId,
  val filenames: List<String> = emptyList(),
  val extensions: List<String> = emptyList(),
  val aliases: List<String> = emptyList(),
  val filenamePatterns: List<String> = emptyList(),
  val configuration: String? = null,
  val firstLine: String? = null,
)

@Serializable
internal data class VSCodeExtensionGrammar(
  val language: VSCodeExtensionLanguageId? = null,
  val scopeName: String,
  val path: String,
  val embeddedLanguages: Map<TextMateScopeName, VSCodeExtensionLanguageId> = emptyMap(),
)

@Serializable
internal data class VSCodeExtensionSnippet(val language: VSCodeExtensionLanguageId, val path: String)

@Serializable
internal data class VSCodeExtensionContributes(
  val languages: List<VSCodeExtensionLanguage> = emptyList(),
  val grammars: List<VSCodeExtensionGrammar> = emptyList(),
  val snippets: List<VSCodeExtensionSnippet> = emptyList(),
)

@Serializable
internal data class VSCodeExtension(val name: String, val contributes: VSCodeExtensionContributes)

@Serializable
internal data class VSCodeExtensionLanguageConfiguration(
  val brackets: List<List<String>> = emptyList(),
  val autoClosingPairs: List<VSCodeExtensionAutoClosingPairs> = emptyList(),
  val autoCloseBefore: String? = null,
  val surroundingPairs: List<VSCodeExtensionSurroundingPairs> = emptyList(),
  val comments: VSCodeExtensionComments,
  val indentationRules: VSCodeExtensionIndentationRules? = null,
  val onEnterRules: List<OnEnterRule> = emptyList(),
)

@Serializable(with = VSCodeExtensionSurroundingPairsDeserializer::class)
data class VSCodeExtensionSurroundingPairs(val open: String, val close: String)

class VSCodeExtensionSurroundingPairsDeserializer : KSerializer<VSCodeExtensionSurroundingPairs> {
  override val descriptor: SerialDescriptor = JsonObject.serializer().descriptor
  override fun deserialize(decoder: Decoder): VSCodeExtensionSurroundingPairs {
    return when (val json = (decoder as JsonDecoder).decodeJsonElement()) {
      is JsonArray -> VSCodeExtensionSurroundingPairs(json[0].jsonPrimitive.content, json[1].jsonPrimitive.content)
      is JsonObject -> VSCodeExtensionSurroundingPairs(json["open"]!!.jsonPrimitive.content, json["close"]!!.jsonPrimitive.content)
      else -> error("cannot deserialize $json")
    }
  }

  override fun serialize(encoder: Encoder, value: VSCodeExtensionSurroundingPairs) {
    (encoder as JsonEncoder).encodeJsonElement(JsonArray(listOf(JsonPrimitive(value.open), JsonPrimitive(value.close))))
  }
}

@Serializable(with = VSCodeExtensionAutoClosingPairsDeserializer::class)
internal data class VSCodeExtensionAutoClosingPairs(
  val open: String,
  val close: String,
  val notIn: EnumSet<TextMateStandardTokenType>? = null,
)

internal class VSCodeExtensionAutoClosingPairsDeserializer : KSerializer<VSCodeExtensionAutoClosingPairs> {
  override val descriptor: SerialDescriptor = JsonObject.serializer().descriptor

  override fun deserialize(decoder: Decoder): VSCodeExtensionAutoClosingPairs {
    require(decoder is JsonDecoder)
    return when (val json = decoder.decodeJsonElement()) {
      is JsonArray -> VSCodeExtensionAutoClosingPairs(json[0].jsonPrimitive.content, json[1].jsonPrimitive.content, null)
      is JsonObject -> VSCodeExtensionAutoClosingPairs(
        open = json["open"]!!.jsonPrimitive.content,
        close = json["close"]!!.jsonPrimitive.content,
        notIn = (json["notIn"] as? JsonArray)?.mapNotNull {
          when (it.jsonPrimitive.content) {
            "string" -> TextMateStandardTokenType.STRING
            "comment" -> TextMateStandardTokenType.COMMENT
            else -> null
          }
        }?.let { EnumSet.copyOf(it) })
      else -> error("cannot deserialize $json")
    }
  }

  override fun serialize(encoder: Encoder, value: VSCodeExtensionAutoClosingPairs) {
    val json = JsonObject(mapOf(
      "open" to JsonPrimitive(value.open),
      "close" to JsonPrimitive(value.close),
      "notIn" to JsonArray(value.notIn?.map { JsonPrimitive(it.name.lowercase()) } ?: emptyList())
    ))
    (encoder as JsonEncoder).encodeJsonElement(json)
  }
}

@Serializable
internal data class VSCodeExtensionComments(val lineComment: String? = null, val blockComment: List<String> = emptyList())

@Serializable(with = VSCodeExtensionIndentationRulesDeserializer::class)
internal data class VSCodeExtensionIndentationRules(
  val increaseIndentPattern: String? = null,
  val decreaseIndentPattern: String? = null,
  val indentNextLinePattern: String? = null,
  val unIndentedLinePattern: String? = null,
)

internal class VSCodeExtensionIndentationRulesDeserializer : KSerializer<VSCodeExtensionIndentationRules> {
  override val descriptor: SerialDescriptor = JsonObject.serializer().descriptor

  override fun deserialize(decoder: Decoder): VSCodeExtensionIndentationRules {
    require(decoder is JsonDecoder)
    val node = decoder.decodeJsonElement() as? JsonObject
    return VSCodeExtensionIndentationRules(
      increaseIndentPattern = readPattern(node, "increaseIndentPattern"),
      decreaseIndentPattern = readPattern(node, "decreaseIndentPattern"),
      indentNextLinePattern = readPattern(node, "indentNextLinePattern"),
      unIndentedLinePattern = readPattern(node, "unIndentedLinePattern"))
  }

  override fun serialize(encoder: Encoder, value: VSCodeExtensionIndentationRules) {
    val map = buildMap {
      value.increaseIndentPattern?.let { put("increaseIndentPattern", JsonPrimitive(it)) }
      value.decreaseIndentPattern?.let { put("decreaseIndentPattern", JsonPrimitive(it)) }
      value.indentNextLinePattern?.let { put("indentNextLinePattern", JsonPrimitive(it)) }
      value.unIndentedLinePattern?.let { put("unIndentedLinePattern", JsonPrimitive(it)) }
    }
    (encoder as JsonEncoder).encodeJsonElement(JsonObject(map))
  }

  private fun readPattern(node: JsonObject?, name: String): String? {
    return when (val child = node?.get(name)) {
      is JsonPrimitive -> child.content
      is JsonObject -> child["pattern"]!!.jsonPrimitive.content
      null -> null
      else -> error("unexpected indentationRules node")
    }
  }
}

internal class TextRuleDeserializer : KSerializer<TextRule> {
  override val descriptor: SerialDescriptor = JsonObject.serializer().descriptor

  override fun deserialize(decoder: Decoder): TextRule {
    require(decoder is JsonDecoder)
    return TextRule(when (val json = decoder.decodeJsonElement()) {
                      is JsonObject -> json["pattern"]!!.jsonPrimitive.content
                      is JsonPrimitive -> json.content
                      else -> error("cannot deserialize $json")
                    })
  }

  override fun serialize(encoder: Encoder, value: TextRule) {
    encoder.encodeString(value.text)
  }
}

// todo: remove when comments are supported in kotlin-serialization
private fun Sequence<String>.removeJsonComments(): String {
  return map { line ->
    val commentIndex = lineCommentIndex(line)
    if (commentIndex >= 0) {
      line.substring(0, commentIndex)
    }
    else {
      line
    }
  }.joinToString(separator = "\n")
}

private fun lineCommentIndex(line: String): Int {
  var i = 0
  var quote: Char? = null
  while (i < line.length) {
    val c = line[i]
    if (quote != null) {
      when (c) {
        '\\' -> {
          i++
        }
        quote -> {
          quote = null
        }
      }
    }
    else {
      when (c) {
        '\'' -> quote = c
        '"' -> quote = c
        '/' -> {
          if (i + 1 < line.length && line[i + 1] == '/') {
            return i
          }
        }
      }
    }
    i++
  }
  return -1
}