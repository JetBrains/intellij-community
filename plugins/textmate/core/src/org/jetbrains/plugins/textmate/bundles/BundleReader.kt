package org.jetbrains.plugins.textmate.bundles

import com.intellij.openapi.diagnostic.LoggerRt
import org.jetbrains.plugins.textmate.Constants
import org.jetbrains.plugins.textmate.language.PreferencesReadUtil
import org.jetbrains.plugins.textmate.language.preferences.*
import org.jetbrains.plugins.textmate.plist.CompositePlistReader
import org.jetbrains.plugins.textmate.plist.Plist
import org.jetbrains.plugins.textmate.plist.PlistReader
import java.io.InputStream
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import kotlin.io.path.inputStream
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.pathString

interface TextMateBundleReader {
  companion object {
    internal val logger = LoggerRt.getInstance(TextMateBundleReader::class.java)
  }

  val bundleName: String

  fun readGrammars(): Sequence<TextMateGrammar>
  fun readPreferences(): Sequence<TextMatePreferences>
  fun readSnippets(): Sequence<TextMateSnippet>
}

typealias TextMateScopeName = CharSequence

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

fun readTextMateBundle(path: Path): TextMateBundleReader {
  val plistReader = CompositePlistReader()
  val infoPlistPath = path.resolve(Constants.BUNDLE_INFO_PLIST_NAME)
  val infoPlist = infoPlistPath.inputStream().buffered().use { plistReader.read(it) }
  val bundleName = infoPlist.getPlistValue(Constants.NAME_KEY, path.name).string

  return object : TextMateBundleReader {
    override val bundleName: String = bundleName

    override fun readGrammars(): Sequence<TextMateGrammar> {
      return readPlistInDirectory(path.resolve("Syntaxes"), plistReader = plistReader,
                                  glob = "*.{tmLanguage,plist,tmLanguage.json}").map { plist ->
        val fileNameMatchers = plist.getPlistValue(Constants.FILE_TYPES_KEY, emptyList<Any>()).stringArray.flatMap { s ->
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
      return readPlistInDirectory(path.resolve("Preferences"), plistReader = plistReader,
                                  glob = "*.{tmPreferences,plist}").mapNotNull { plist ->
        readPreferencesFromPlist(plist)
      }
    }

    override fun readSnippets(): Sequence<TextMateSnippet> {
      return readPlistInDirectory(path.resolve("Snippets"), plistReader = plistReader, glob = "*.{tmSnippet,plist}").mapNotNull { plist ->
        readSnippetFromPlist(plist, path.pathString)
      }
    }
  }
}

fun readSublimeBundle(path: Path): TextMateBundleReader {
  val plistReader = CompositePlistReader()
  return object : TextMateBundleReader {
    override val bundleName: String = path.name

    override fun readGrammars(): Sequence<TextMateGrammar> {
      return readPlistInDirectory(path, plistReader = plistReader, glob = "*.{tmLanguage,plist,tmLanguage.json}").map { plist ->
        val fileNameMatchers = plist.getPlistValue(Constants.FILE_TYPES_KEY, emptyList<Any>()).stringArray.flatMap { s ->
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
      return readPlistInDirectory(path, plistReader = plistReader, glob = "*.{tmPreferences,plist}").mapNotNull { plist ->
        readPreferencesFromPlist(plist)
      }
    }

    override fun readSnippets(): Sequence<TextMateSnippet> {
      return readPlistInDirectory(path, plistReader = plistReader, glob = "*.plist").mapNotNull { plist ->
        readSnippetFromPlist(plist, path.pathString)
      }
    }
  }
}

private fun readSnippetFromPlist(plist: Plist, explicitUuid: String): TextMateSnippet? {
  val key = plist.getPlistValue(Constants.TAB_TRIGGER_KEY, "").string.takeIf { it.isNotEmpty() } ?: return null
  val content = plist.getPlistValue(Constants.StringKey.CONTENT.value, "").string.takeIf { it.isNotEmpty() } ?: return null
  val name = plist.getPlistValue(Constants.NAME_KEY, "").string.takeIf { it.isNotEmpty() } ?: key
  val scope = plist.getPlistValue(Constants.SCOPE_KEY, "").string
  val description = plist.getPlistValue(Constants.DESCRIPTION_KEY, "").string //NON-NLS
  val uuid = plist.getPlistValue(Constants.UUID_KEY, explicitUuid).string
  return TextMateSnippet(key, content, scope, name, description, uuid)
}

private fun readPreferencesFromPlist(plist: Plist): TextMatePreferences? {
  return plist.getPlistValue(Constants.SCOPE_KEY)?.string?.let { scopeName ->
    plist.getPlistValue(Constants.SETTINGS_KEY)?.plist?.let { settings ->
      val highlightingPairs = PreferencesReadUtil.readPairs(settings.getPlistValue(Constants.HIGHLIGHTING_PAIRS_KEY))
      val smartTypingPairs = PreferencesReadUtil.readPairs(settings.getPlistValue(Constants.SMART_TYPING_PAIRS_KEY))
        ?.map { TextMateAutoClosingPair(it.left, it.right, null) }
        ?.toSet()
      val indentationRules = PreferencesReadUtil.loadIndentationRules(settings)
      val variables = settings.getPlistValue(Constants.SHELL_VARIABLES_KEY)?.let { variables ->
        variables.array.map { variable ->
          val variablePlist = variable.plist
          TextMateShellVariable(scopeName,
                                variablePlist.getPlistValue(Constants.NAME_KEY, "").string,
                                variablePlist.getPlistValue(Constants.VALUE_KEY, "").string)
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

private fun readPlistInDirectory(directory: Path, plistReader: PlistReader, glob: String = "*"): Sequence<Plist> {
  return runCatching {
    directory.listDirectoryEntries(glob = glob).asSequence().mapNotNull { child ->
      readPlist(child.inputStream().buffered(), plistReader, child.pathString)
    }
  }.getOrElse { e ->
    when (e) {
      is NoSuchFileException -> {}
      else -> TextMateBundleReader.logger.warn("Can't load plists from directory: " + directory.pathString, e)
    }
    emptySequence()
  }
}

internal fun readPlist(inputStream: InputStream, plistReader: PlistReader, debugName: String): Plist? {
  return runCatching {
    inputStream.use { plistReader.read(it) }
  }.onFailure { e ->
    TextMateBundleReader.logger.warn("Can't load plist from file: $debugName", e)
  }.getOrNull()
}
