package org.jetbrains.plugins.textmate.bundles

import org.jetbrains.plugins.textmate.Constants
import org.jetbrains.plugins.textmate.getLogger
import org.jetbrains.plugins.textmate.language.PreferencesReadUtil
import org.jetbrains.plugins.textmate.language.preferences.*
import org.jetbrains.plugins.textmate.plist.Plist
import org.jetbrains.plugins.textmate.plist.PlistReader
import java.io.InputStream

interface TextMateBundleReader {
  companion object {
    internal val logger = getLogger(TextMateBundleReader::class)
  }

  val bundleName: String

  fun readGrammars(): Sequence<TextMateGrammar>
  fun readPreferences(): Sequence<TextMatePreferences>
  fun readSnippets(): Sequence<TextMateSnippet>
}

typealias TextMateScopeName = String

data class TextMateGrammar(val fileNameMatchers: Collection<TextMateFileNameMatcher>,
                           val firstLinePattern: String?,
                           val plist: Lazy<Plist>,
                           val overrideName: String?,
                           val overrideScopeName: String?)

data class TextMatePreferences(val scopeName: TextMateScopeName,
                               val variables: Collection<TextMateShellVariable>,
                               val highlightingPairs: Set<TextMateBracePair>?,
                               val smartTypingPairs: Set<TextMateAutoClosingPair>?,
                               val autoCloseBefore: String?,
                               val surroundingPairs:  Set<TextMateBracePair>?,
                               val indentationRules: IndentationRules,
                               val customHighlightingAttributes: TextMateTextAttributes?,
                               val onEnterRules: Set<OnEnterRule>?)

fun readTextMateBundle(fallbackBundleName: String, plistReader: PlistReader, resourceReader: TextMateResourceReader): TextMateBundleReader {
  val infoPlist = resourceReader.read(Constants.BUNDLE_INFO_PLIST_NAME)?.use { plistReader.read(it) }
  val bundleName = infoPlist?.getPlistValue(Constants.NAME_KEY, fallbackBundleName)?.string!!

  return object : TextMateBundleReader {
    override val bundleName: String = bundleName

    override fun readGrammars(): Sequence<TextMateGrammar> {
      return resourceReader.readPlistInDirectory(plistReader = plistReader, relativePath = "Syntaxes") { name ->
        name.endsWith(".tmLanguage") || name.endsWith(".plist") || name.endsWith(".tmLanguage.json")
      }.map { plist ->
        val fileNameMatchers = plist.getPlistValue(Constants.FILE_TYPES_KEY, emptyList<Any>()).stringArray.filterNotNull().flatMap { s ->
          listOf(TextMateFileNameMatcher.Name(s), TextMateFileNameMatcher.Extension(s))
        }
        val firstLinePattern = plist.getPlistValue(Constants.FIRST_LINE_MATCH)?.string
        TextMateGrammar(fileNameMatchers = fileNameMatchers,
                        firstLinePattern = firstLinePattern,
                        plist = lazy { plist },
                        overrideName = null,
                        overrideScopeName = null)
      }
    }

    override fun readPreferences(): Sequence<TextMatePreferences> {
      return resourceReader.readPlistInDirectory(plistReader = plistReader, relativePath = "Preferences") { name ->
        name.endsWith(".tmPreferences") || name.endsWith(".plist")
      }.mapNotNull { plist ->
        readPreferencesFromPlist(plist)
      }
    }

    override fun readSnippets(): Sequence<TextMateSnippet> {
      return resourceReader.readPlistInDirectory(plistReader = plistReader, relativePath = "Snippets") { name ->
        name.endsWith(".tmSnippet") || name.endsWith(".plist")
      }.mapNotNull { plist ->
        readSnippetFromPlist(plist)
      }
    }
  }
}

fun readSublimeBundle(
  bundleName: String,
  plistReader: PlistReader,
  resourceReader: TextMateResourceReader,
): TextMateBundleReader {
  return object : TextMateBundleReader {
    override val bundleName: String = bundleName

    override fun readGrammars(): Sequence<TextMateGrammar> {
      return resourceReader.readPlistInDirectory(plistReader = plistReader, relativePath = ".") { name ->
        name.endsWith(".tmLanguage") || name.endsWith(".plist") || name.endsWith(".tmLanguage.json")
      }.map { plist ->
        val fileNameMatchers = plist.getPlistValue(Constants.FILE_TYPES_KEY, emptyList<Any>()).stringArray.filterNotNull().flatMap { s ->
          listOf(TextMateFileNameMatcher.Name(s), TextMateFileNameMatcher.Extension(s))
        }
        val firstLinePattern = plist.getPlistValue(Constants.FIRST_LINE_MATCH)?.string
        TextMateGrammar(fileNameMatchers = fileNameMatchers,
                        firstLinePattern = firstLinePattern,
                        plist = lazy { plist },
                        overrideName = null,
                        overrideScopeName = null)
      }
    }

    override fun readPreferences(): Sequence<TextMatePreferences> {
      return resourceReader.readPlistInDirectory(plistReader = plistReader, relativePath = ".") { name ->
        name.endsWith(".tmPreferences") || name.endsWith(".plist")
      }.mapNotNull { plist ->
        readPreferencesFromPlist(plist)
      }
    }

    override fun readSnippets(): Sequence<TextMateSnippet> {
      return resourceReader.readPlistInDirectory(plistReader = plistReader, relativePath = ".") { name ->
        name.endsWith(".plist")
      }.mapNotNull { plist ->
        readSnippetFromPlist(plist)
      }
    }
  }
}

private fun readSnippetFromPlist(plist: Plist): TextMateSnippet? {
  val key = plist.getPlistValue(Constants.TAB_TRIGGER_KEY, "").string.takeIf { !it.isNullOrEmpty() } ?: return null
  val content = plist.getPlistValue(Constants.StringKey.CONTENT.value, "").string.takeIf { !it.isNullOrEmpty() } ?: return null
  val name = plist.getPlistValue(Constants.NAME_KEY, "").string.takeIf { !it.isNullOrEmpty() } ?: key
  val scope = plist.getPlistValue(Constants.SCOPE_KEY, "").string!!
  val description = plist.getPlistValue(Constants.DESCRIPTION_KEY, "").string!! //NON-NLS
  val uuid = plist.getPlistValue(Constants.UUID_KEY, "").string!!
  return TextMateSnippet(key, content, scope, name, description, uuid)
}

private fun readPreferencesFromPlist(plist: Plist): TextMatePreferences? {
  return plist.getPlistValue(Constants.SCOPE_KEY)?.string?.let { scopeName ->
    plist.getPlistValue(Constants.SETTINGS_KEY)?.plist?.let { settings ->
      val highlightingPairs = PreferencesReadUtil.readPairs(settings.getPlistValue(Constants.HIGHLIGHTING_PAIRS_KEY))
      val smartTypingPairs = PreferencesReadUtil.readPairs(settings.getPlistValue(Constants.SMART_TYPING_PAIRS_KEY))
        ?.map { TextMateAutoClosingPair(it.left, it.right, 0) }
        ?.toSet()
      val indentationRules = PreferencesReadUtil.loadIndentationRules(settings)
      val variables = settings.getPlistValue(Constants.SHELL_VARIABLES_KEY)?.let { variables ->
        variables.array.map { variable ->
          val variablePlist = variable.plist
          TextMateShellVariable(scopeName,
                                variablePlist.getPlistValue(Constants.NAME_KEY, "").string!!,
                                variablePlist.getPlistValue(Constants.VALUE_KEY, "").string!!)
        }
      } ?: emptyList()
      val customHighlightingAttributes = TextMateTextAttributes.fromPlist(settings)
      TextMatePreferences(scopeName = scopeName,
                          variables = variables,
                          highlightingPairs = highlightingPairs ?: Constants.DEFAULT_HIGHLIGHTING_BRACE_PAIRS,
                          smartTypingPairs = smartTypingPairs ?: Constants.DEFAULT_SMART_TYPING_BRACE_PAIRS,
                          surroundingPairs = null,
                          autoCloseBefore = null,
                          indentationRules = indentationRules,
                          customHighlightingAttributes = customHighlightingAttributes,
                          onEnterRules = null)
    }
  }
}

private fun TextMateResourceReader.readPlistInDirectory(
  plistReader: PlistReader,
  relativePath: String,
  filter: (String) -> Boolean = { true },
): Sequence<Plist> {
  return list(relativePath).asSequence().filter(filter).mapNotNull { childName ->
    read("$relativePath/$childName")?.use { inputStream ->
      readPlist(inputStream.buffered(), plistReader, "$relativePath/$childName")
    }
  }
}

internal fun readPlist(inputStream: InputStream, plistReader: PlistReader, debugName: String): Plist? {
  return runCatching {
    inputStream.use { plistReader.read(it) }
  }.onFailure { e ->
    TextMateBundleReader.logger.warn(e) { "Can't load plist from file: $debugName" }
  }.getOrNull()
}
