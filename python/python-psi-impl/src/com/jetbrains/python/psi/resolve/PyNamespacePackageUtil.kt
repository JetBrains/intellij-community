@file:JvmName("PyNamespacePackageUtil")
package com.jetbrains.python.psi.resolve

import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
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
    lexer.tokenType
    while (lexer.tokenType in tokensToSkip) {
      lexer.advance()
    }

    val codeStart = initFile.text.substring(lexer.tokenStart)
    var nextPattern: Pattern? = null
    for (line in codeStart.lineSequence()) {
      val trimmed = line.trim()
      if (trimmed.startsWith("#")) continue
      else if (nextPattern != null && nextPattern.matcher(trimmed).matches()) return true
      else if (oneLineNamespaceDeclarations.any { it.matcher(trimmed).matches() }) return true
      else if (nextPattern == null) nextPattern = multilineNamespaceDeclarations.find { it[0].matcher(trimmed).matches() }?.get(1)
      else return false
    }
  }
  return false
}

private val tokensToSkip = setOf(PyTokenTypes.DOCSTRING,
                                 PyTokenTypes.END_OF_LINE_COMMENT,
                                 PyTokenTypes.LINE_BREAK,
                                 PyTokenTypes.SPACE,
                                 PyTokenTypes.TRY_KEYWORD,
                                 PyTokenTypes.COLON)

private val multilineNamespaceDeclarations = listOf(
  patterns("^from pkgutil import extend_path.*", "^__path__[ ]?=[ ]?extend_path\\(__path__,[ ]?__name__\\).*"),
  patterns("^import pkgutil.*", "^__path__[ ]?=[ ]?pkgutil\\.extend_path\\(__path__,[ ]?__name__\\).*"),
  patterns("^from pkg_resources import declare_namespace.*", "^declare_namespace\\(__name__\\).*"),
  patterns("^import pkg_resources.*", "^pkg_resources.declare_namespace\\(__name__\\).*")
)

private val oneLineNamespaceDeclarations = patterns(
  "^__path__[ ]?=[ ]?__import__\\(['\"]pkgutil['\"]\\).extend_path\\(__path__, __name__\\).*",
  "^__import__\\(['\"]pkg_resources['\"]\\).declare_namespace\\(__name__\\).*"
)

private fun patterns(vararg patterns: String) = patterns.map { it.toPattern() }