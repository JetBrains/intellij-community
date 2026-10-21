package org.jetbrains.plugins.textmate.bundles

import org.jetbrains.plugins.textmate.Constants

/**
 * The name of the index file in a directory of bundles, see [TextMateBundlesIndex].
 */
const val BUNDLES_INDEX_FILE_NAME: String = "index.txt"

/**
 * The index of a directory of bundles: the scope name and the file name matchers of every grammar, per bundle.
 * The index gives the file name mapping without a read of a bundle directory.
 *
 * The index is a UTF-8 text file with LF line ends. A line is a key, one space, and a value:
 * - `bundle <directory name>` starts a bundle,
 * - `scope <scope name>` starts a grammar of the current bundle,
 * - `ext <extension>[;<extension>...]` lists the file extensions of the current grammar,
 * - `name <file name>[;<file name>...]` lists the file names of the current grammar,
 * - `pattern <glob>` adds a matcher to the current grammar.
 *
 * A blank line and a line that starts with `#` are ignored.
 */
data class TextMateBundlesIndex(val bundles: List<TextMateBundleIndexEntry>) {
  fun encode(): String {
    return buildString {
      append(HEADER).append('\n')
      for (bundle in bundles) {
        appendEntry(BUNDLE_KEY, bundle.name)
        for (grammar in bundle.grammars) {
          appendEntry(SCOPE_KEY, grammar.scopeName)
          appendList(EXTENSION_KEY, grammar.extensions)
          appendList(NAME_KEY, grammar.fileNames)
          grammar.patterns.forEach { appendEntry(PATTERN_KEY, it) }
        }
      }
    }
  }

  private fun StringBuilder.appendList(key: String, values: List<String>) {
    if (values.isEmpty()) {
      return
    }
    values.forEach { require(LIST_SEPARATOR !in it) { "A `$key` value must not contain `$LIST_SEPARATOR`: `$it`" } }
    appendEntry(key, values.joinToString(LIST_SEPARATOR.toString()))
  }

  private fun StringBuilder.appendEntry(key: String, value: String) {
    require('\n' !in value && '\r' !in value) { "A bundles index value must be one line: `$value`" }
    append(key).append(' ').append(value).append('\n')
  }

  companion object {
    private const val HEADER = "# The index of the bundles in this directory. loadVSCBundles.sh writes this file, and TextMateBundlesIndexTest checks it."
    private const val BUNDLE_KEY = "bundle"
    private const val SCOPE_KEY = "scope"
    private const val EXTENSION_KEY = "ext"
    private const val NAME_KEY = "name"
    private const val LIST_SEPARATOR = ';'
    private const val PATTERN_KEY = "pattern"

    fun decode(text: String): TextMateBundlesIndex {
      val bundles = ArrayList<TextMateBundleIndexEntry>()
      var bundleName: String? = null
      var grammars = ArrayList<TextMateGrammarIndexEntry>()
      var grammar: GrammarEntryBuilder? = null

      fun finishGrammar() {
        grammar?.let { grammars.add(it.build()) }
        grammar = null
      }

      fun finishBundle() {
        finishGrammar()
        bundleName?.let { bundles.add(TextMateBundleIndexEntry(it, grammars)) }
        bundleName = null
        grammars = ArrayList()
      }

      var lineNumber = 0
      for (line in text.lineSequence()) {
        lineNumber++
        if (line.isBlank() || line.startsWith('#')) {
          continue
        }
        val separator = line.indexOf(' ')
        require(separator > 0) { "Line $lineNumber of the bundles index has no key: `$line`" }
        val key = line.substring(0, separator)
        val value = line.substring(separator + 1)
        when (key) {
          BUNDLE_KEY -> {
            finishBundle()
            bundleName = value
          }
          SCOPE_KEY -> {
            require(bundleName != null) { "Line $lineNumber of the bundles index has a scope outside of a bundle" }
            finishGrammar()
            grammar = GrammarEntryBuilder(value)
          }
          else -> {
            val matchers = when (key) {
              EXTENSION_KEY -> grammar?.extensions
              NAME_KEY -> grammar?.fileNames
              PATTERN_KEY -> grammar?.patterns
              else -> throw IllegalArgumentException("Line $lineNumber of the bundles index has an unknown key `$key`")
            }
            require(matchers != null) { "Line $lineNumber of the bundles index has a matcher outside of a scope" }
            if (key == PATTERN_KEY) {
              matchers.add(value)
            }
            else {
              matchers.addAll(value.split(LIST_SEPARATOR))
            }
          }
        }
      }
      finishBundle()
      return TextMateBundlesIndex(bundles)
    }
  }
}

private class GrammarEntryBuilder(val scopeName: String) {
  val extensions = ArrayList<String>()
  val fileNames = ArrayList<String>()
  val patterns = ArrayList<String>()

  fun build(): TextMateGrammarIndexEntry = TextMateGrammarIndexEntry(scopeName, extensions, fileNames, patterns)
}

/**
 * @param name the name of the bundle directory
 */
data class TextMateBundleIndexEntry(val name: String, val grammars: List<TextMateGrammarIndexEntry>)

data class TextMateGrammarIndexEntry(
  val scopeName: String,
  val extensions: List<String> = emptyList(),
  val fileNames: List<String> = emptyList(),
  val patterns: List<String> = emptyList(),
) {
  fun fileNameMatchers(): List<TextMateFileNameMatcher> {
    return buildList {
      extensions.mapTo(this) { TextMateFileNameMatcher.Extension(it) }
      fileNames.mapTo(this) { TextMateFileNameMatcher.Name(it) }
      patterns.mapTo(this) { TextMateFileNameMatcher.Pattern(it) }
    }
  }
}

/**
 * Builds the index entry of a bundle from its grammars. A grammar without a scope name is skipped,
 * as [org.jetbrains.plugins.textmate.language.syntax.TextMateSyntaxTableBuilder.addSyntax] skips it.
 */
fun bundleIndexEntry(bundleName: String, grammars: List<TextMateGrammar>): TextMateBundleIndexEntry {
  return TextMateBundleIndexEntry(bundleName, grammars.mapNotNull { it.indexEntry() })
}

/**
 * The scope name of a VS Code grammar comes from `package.json`, so the grammar file stays unread.
 */
fun TextMateGrammar.scopeName(): String? {
  return overrideScopeName ?: plist.value.getPlistValue(Constants.StringKey.SCOPE_NAME.value)?.string
}

private fun TextMateGrammar.indexEntry(): TextMateGrammarIndexEntry? {
  val scopeName = scopeName() ?: return null
  return TextMateGrammarIndexEntry(
    scopeName = scopeName,
    extensions = fileNameMatchers.filterIsInstance<TextMateFileNameMatcher.Extension>().map { it.extension },
    fileNames = fileNameMatchers.filterIsInstance<TextMateFileNameMatcher.Name>().map { it.fileName },
    patterns = fileNameMatchers.filterIsInstance<TextMateFileNameMatcher.Pattern>().map { it.pattern },
  )
}
