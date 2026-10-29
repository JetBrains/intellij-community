// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.regexp

import com.intellij.lang.Language
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import org.intellij.lang.regexp.RegExpCapability
import org.intellij.lang.regexp.RegExpFile
import org.intellij.lang.regexp.RegExpLanguage
import org.intellij.lang.regexp.RegExpParserDefinition
import org.intellij.lang.regexp.RegExpSyntaxHighlighterFactory
import java.util.EnumSet

/** The dialect of the third-party `regex` module by Matthew Barnett (mrab-regex). */
internal object PythonMrabRegexpLanguage : Language(RegExpLanguage.INSTANCE, "PythonMrabRegExp")

/** The [PythonMrabRegexpLanguage] dialect with the `VERBOSE` flag. */
internal object PythonMrabVerboseRegexpLanguage : Language(RegExpLanguage.INSTANCE, "PythonMrabVerboseRegExp")

internal fun isMrabRegexp(context: PsiElement): Boolean {
  val language = context.containingFile.language
  return language == PythonMrabRegexpLanguage || language == PythonMrabVerboseRegexpLanguage
}

internal open class PythonMrabRegexpParserDefinition : RegExpParserDefinition() {
  override fun getCapabilities(): EnumSet<RegExpCapability> = CAPABILITIES

  override fun getFileNodeType(): IFileElementType = FILE

  override fun createFile(viewProvider: FileViewProvider): PsiFile = RegExpFile(viewProvider, PythonMrabRegexpLanguage)

  private companion object {
    val FILE = IFileElementType("PYTHON_MRAB_REGEXP_FILE", PythonMrabRegexpLanguage)
  }
}

internal class PythonMrabVerboseRegexpParserDefinition : PythonMrabRegexpParserDefinition() {
  override fun getCapabilities(): EnumSet<RegExpCapability> = VERBOSE_CAPABILITIES

  override fun getFileNodeType(): IFileElementType = FILE

  override fun createFile(viewProvider: FileViewProvider): PsiFile = RegExpFile(viewProvider, PythonMrabVerboseRegexpLanguage)

  private companion object {
    val FILE = IFileElementType("PYTHON_MRAB_VERBOSE_REGEXP_FILE", PythonMrabVerboseRegexpLanguage)
  }
}

internal class PythonMrabRegexpSyntaxHighlighterFactory : RegExpSyntaxHighlighterFactory(PythonMrabRegexpLanguage)

internal class PythonMrabVerboseRegexpSyntaxHighlighterFactory : RegExpSyntaxHighlighterFactory(PythonMrabVerboseRegexpLanguage)

private val CAPABILITIES: EnumSet<RegExpCapability> = EnumSet.of(
  RegExpCapability.DANGLING_METACHARACTERS,
  RegExpCapability.OCTAL_NO_LEADING_ZERO,
  RegExpCapability.OMIT_NUMBERS_IN_QUANTIFIERS,
  RegExpCapability.OMIT_BOTH_NUMBERS_IN_QUANTIFIERS,
  RegExpCapability.MIN_OCTAL_3_DIGITS,
  RegExpCapability.SHORTHAND_CLASS_RANGE_START_ERROR,
  RegExpCapability.SHORTHAND_CLASS_RANGE_END_ERROR,
  RegExpCapability.ALLOW_HORIZONTAL_WHITESPACE_CLASS,
  RegExpCapability.POSIX_BRACKET_EXPRESSIONS,
  RegExpCapability.UNICODE_CATEGORY_SHORTHAND,
  RegExpCapability.CARET_NEGATED_PROPERTIES,
  RegExpCapability.PROPERTY_VALUES,
  RegExpCapability.PCRE_BACK_REFERENCES,
  RegExpCapability.PCRE_NUMBERED_GROUP_REF,
  RegExpCapability.PCRE_CONDITIONS,
)

private val VERBOSE_CAPABILITIES: EnumSet<RegExpCapability> = EnumSet.copyOf(CAPABILITIES).apply { add(RegExpCapability.COMMENT_MODE) }
