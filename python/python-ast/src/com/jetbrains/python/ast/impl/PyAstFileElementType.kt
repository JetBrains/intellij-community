// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.ast.impl

import com.intellij.lang.ASTNode
import com.intellij.lang.Language
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.lang.PsiBuilderFactory
import com.intellij.psi.ParsingDiagnostics
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IFileElementType
import com.jetbrains.python.PyPsiElementFactory
import com.jetbrains.python.ast.PyAstFile
import com.jetbrains.python.parsing.PyParser
import com.jetbrains.python.psi.LanguageLevel
import org.jetbrains.annotations.ApiStatus

/**
 * The file node type of a Python file. It is a plain [IFileElementType]: the stub support of a language comes from the
 * `languageStubDefinition` and the file stub serializer that `intellij.python.psi.impl` registers.
 */
@ApiStatus.Internal
open class PyAstFileElementType(language: Language) : IFileElementType(language) {
  override fun parseContents(chameleon: ASTNode): ASTNode? = parseFileContents(this, chameleon)

  companion object {
    /**
     * Parses the contents of the file node [chameleon] of [fileElementType] with the language level of its file.
     * [PyPsiElementFactory.parseFileContents] handles a file that needs another parse, for example a console file.
     */
    @JvmStatic
    fun parseFileContents(fileElementType: IFileElementType, chameleon: ASTNode): ASTNode? {
      val psi = chameleon.psi ?: return null
      val languageLevel = languageLevelOf(psi)
      PyPsiElementFactory.current().parseFileContents(chameleon, languageLevel)?.let { return it }

      val project = psi.project
      val language = fileElementType.language
      val parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(language) ?: return null
      val lexer = parserDefinition.createLexer(project)
      val parser = parserDefinition.createParser(project)
      val builder = PsiBuilderFactory.getInstance().createBuilder(project, chameleon, lexer, language, chameleon.chars)
      if (parser is PyParser) {
        parser.setLanguageLevel(languageLevel)
      }
      val startTime = System.nanoTime()
      val result = parser.parse(fileElementType, builder).firstChildNode
      ParsingDiagnostics.registerParse(builder, language, System.nanoTime() - startTime)
      return result
    }

    private fun languageLevelOf(psi: PsiElement): LanguageLevel {
      val file = psi.containingFile
      if (file is PyAstFile) {
        return file.languageLevel
      }
      val context = file.context
      return if (context != null) languageLevelOf(context) else LanguageLevel.getDefault()
    }
  }
}
