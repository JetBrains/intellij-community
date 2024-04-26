package com.intellij.python.markdown

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.lang.Language
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.documentation.doctest.PyDocstringLanguageDialect
import org.intellij.plugins.markdown.injection.CodeFenceLanguageProvider

/**
 * Defines major Python dialects to use in Markdown code fence blocks.
 *
 * These dialects are listed as classes in [highlight.js][1] and in [Linguist][2] used by GitHub.
 *
 * [1]: https://github.com/highlightjs/highlight.js/blob/main/SUPPORTED_LANGUAGES.md
 * [2]: https://github.com/github/linguist/blob/master/lib/linguist/languages.yml
 */
class PyCodeFenceLanguageProvider : CodeFenceLanguageProvider {
  override fun getLanguageByInfoString(infoString: String): Language? =
    when (infoString.toLowerCase()) {
      "pycon", "python-repl" -> PyDocstringLanguageDialect.getInstance()
      "py", "python3" -> PythonLanguage.INSTANCE
      else -> null
    }

  override fun getCompletionVariantsForInfoString(parameters: CompletionParameters): List<LookupElement> {
    return listOf(
      LookupElementBuilder
        .create("pycon")
        .withIcon(PyDocstringLanguageDialect.getInstance().associatedFileType?.icon)
        .withTypeText("Python console"),
    )
  }
}