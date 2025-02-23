package org.jetbrains.plugins.textmate

import com.intellij.openapi.application.PathManager
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.textmate.bundles.*
import org.jetbrains.plugins.textmate.bundles.BundleType.Companion.detectBundleType
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateScope
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.jvm.JvmStatic

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

  @JvmStatic
  fun getBundleDirectory(bundleName: String): Path {
    val bundleDirectory = Path.of(PathManager.getCommunityHomePath()).resolve("plugins/textmate/testData/bundles").resolve(bundleName)
    return if (bundleDirectory.exists()) {
      bundleDirectory
    }
    else {
      return Path.of(PathManager.getCommunityHomePath()).resolve("plugins/textmate/lib/bundles").resolve(bundleName)
    }
  }

  @JvmStatic
  fun readBundle(bundleName: String): TextMateBundleReader {
    val bundleDirectory = getBundleDirectory(bundleName)
    val bundleType = detectBundleType(bundleDirectory)
    return when (bundleType) {
      BundleType.TEXTMATE -> readTextMateBundle(bundleDirectory)
      BundleType.SUBLIME -> readSublimeBundle(bundleDirectory)
      BundleType.VSCODE -> readVSCBundle { relativePath ->
        bundleDirectory.resolve(relativePath).inputStream().buffered()
      } ?: error("Cannot read VSCBundle from $bundleDirectory")
      BundleType.UNDEFINED -> error("Unknown bundle type: $bundleName")
    }
  }

  @JvmStatic
  fun scopeFromString(scopeString: String): TextMateScope {
    return scopeString.split(' ').dropLastWhile { it.isEmpty() }.fold(TextMateScope.EMPTY) { acc, i -> acc.add(i) }
  }
}
