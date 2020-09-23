@file:JvmName("PyNamespacePackageUtil")
package com.jetbrains.python.psi.resolve

import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.lexer.PythonLexer
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyUtil
import java.util.regex.Pattern


fun isNamespacePackage(element: PsiElement): Boolean {
  if (element is PsiDirectory) {
    val level = LanguageLevel.forElement(element)
    val initFile = PyUtil.turnDirIntoInit(element) ?: return !level.isPython2

    val lexer = PythonLexer()
    lexer.start(initFile.text)
    while (lexer.tokenType in TOKENS_TO_SKIP) {
      lexer.advance()
    }

    val codeStart = initFile.text.substring(lexer.tokenStart)
    var nextPattern: Pattern? = null
    for (line in codeStart.lineSequence()) {
      val trimmed = line.trim()
      if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
      else if (nextPattern != null) return nextPattern.matcher(trimmed).matches()
      else if (ONE_LINE_NAMESPACE_DECLARATIONS.any { it.matcher(trimmed).matches() }) return true
      else {
        val matched = TWO_LINE_NAMESPACE_DECLARATIONS.find { it[0].matcher(trimmed).matches() }
        nextPattern = matched?.get(1) ?: return false
      }
    }
  }
  return false
}

private val TOKENS_TO_SKIP = TokenSet.create(PyTokenTypes.DOCSTRING,
                                             PyTokenTypes.END_OF_LINE_COMMENT,
                                             PyTokenTypes.LINE_BREAK,
                                             PyTokenTypes.SPACE,
                                             PyTokenTypes.TRY_KEYWORD,
                                             PyTokenTypes.COLON)

private val TWO_LINE_NAMESPACE_DECLARATIONS = listOf(
  patterns("^from pkgutil import extend_path.*", "^__path__[ ]?=[ ]?extend_path\\(__path__,[ ]?__name__\\).*"),
  patterns("^import pkgutil.*", "^__path__[ ]?=[ ]?pkgutil\\.extend_path\\(__path__,[ ]?__name__\\).*"),
  patterns("^from pkg_resources import declare_namespace.*", "^declare_namespace\\(__name__\\).*"),
  patterns("^import pkg_resources.*", "^pkg_resources.declare_namespace\\(__name__\\).*")
)

private val ONE_LINE_NAMESPACE_DECLARATIONS = patterns(
  "^__path__[ ]?=[ ]?__import__\\(['\"]pkgutil['\"]\\).extend_path\\(__path__, __name__\\).*",
  "^__import__\\(['\"]pkg_resources['\"]\\).declare_namespace\\(__name__\\).*"
)

private fun patterns(vararg patterns: String) = patterns.map { it.toPattern() }