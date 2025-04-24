package org.jetbrains.plugins.textmate

import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.textmate.bundles.*
import org.jetbrains.plugins.textmate.bundles.BundleType.Companion.detectBundleType
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateScope
import org.jetbrains.plugins.textmate.plist.JsonOrXmlPlistReader
import org.jetbrains.plugins.textmate.plist.JsonPlistReader
import org.jetbrains.plugins.textmate.plist.PlistReader

object TestUtil {
  const val BAT: @NonNls String = "bat"
  const val GO: @NonNls String = "go"
  const val TERRAFORM: @NonNls String = "terraform"
  const val MAKE: @NonNls String = "make"
  const val JAVA: @NonNls String = "java"
  const val LOG: @NonNls String = "log"
  const val JAVASCRIPT: @NonNls String = "javascript"
  const val CHEF: @NonNls String = "chef"
  const val HTML: @NonNls String = "html"
  const val HTML_VSC: @NonNls String = "html_vsc"
  const val CSS_VSC: @NonNls String = "css_vsc"
  const val DOCKER: @NonNls String = "docker"
  const val MARKDOWN_SUBLIME: @NonNls String = "markdown_sublime"
  const val MARKDOWN_TEXTMATE: @NonNls String = "markdown_textmate"
  const val MARKDOWN_VSC: @NonNls String = "markdown-basics"
  const val FSHARP: @NonNls String = "fsharp"
  const val LARAVEL_BLADE: @NonNls String = "laravel-blade"
  const val INVALID_BUNDLE: @NonNls String = "invalid_bundle"
  const val LATEX: @NonNls String = "latex"
  const val PERL: @NonNls String = "perl"
  const val SHELLSCRIPT: @NonNls String = "shellscript"
  const val ELIXIR: @NonNls String = "elixir"
  const val COLD_FUSION: @NonNls String = "coldfusion"
  const val PREFERENCES_TEST_BUNDLE: @NonNls String = "preferences_test"
  const val MARKDOWN_BLOGGING: @NonNls String = "markdown_blogging"
  const val PYTHON: @NonNls String = "python"
  const val RUBY: @NonNls String = "ruby"
  const val PHP: @NonNls String = "php"
  const val PHP_VSC: @NonNls String = "php_vsc"
  const val SMARTY: @NonNls String = "smarty"
  const val TURTLE: @NonNls String = "turtle"
  const val GIT: @NonNls String = "git-base"
  const val RESTRUCTURED_TEXT: @NonNls String = "restructuredtext"

  fun readBundle(bundleName: String, xmlPlistReader: PlistReader): TextMateBundleReader {
    val resourceReader = TestUtilMultiplatform.getResourceReader(bundleName)
    val bundleType = detectBundleType(resourceReader, bundleName)
    val plistReader = JsonOrXmlPlistReader(jsonReader = JsonPlistReader(), xmlReader = xmlPlistReader)
    return when (bundleType) {
      BundleType.TEXTMATE -> readTextMateBundle(bundleName, plistReader, resourceReader)
      BundleType.SUBLIME -> readSublimeBundle(bundleName, plistReader, resourceReader)
      BundleType.VSCODE -> readVSCBundle(plistReader, resourceReader) ?: error("Cannot read VSCBundle")
      BundleType.UNDEFINED -> error("Unknown bundle type: $bundleName")
    }
  }

  fun scopeFromString(scopeString: String): TextMateScope {
    return scopeString.split(' ').dropLastWhile { it.isEmpty() }.fold(TextMateScope.EMPTY) { acc, i -> acc.add(i) }
  }
}
