// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.typeignore

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.patterns.PlatformPatterns.psiComment
import com.intellij.psi.PsiFile
import com.intellij.util.ProcessingContext
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.inspections.PyIgnoreCommentUtil
import com.jetbrains.python.inspections.PySuppressionUtil
import com.jetbrains.python.inspections.PyTypeCheckerSuppressionCode
import com.jetbrains.python.psi.PyFile

private const val TYPE_CHECKER_SUPPRESS_ID = "PyTypeChecker"
private val PYCHARM_NAMESPACE_PREFIX = Regex("^pycharm\\s*:\\s*", RegexOption.IGNORE_CASE)

/**
 * Completes the inspection codes in the brackets of a `# type: ignore[...]` or `# pycharm: ignore[...]` comment.
 *
 * The variants are the granular type-checker codes and one code for each Python inspection: its kebab-case
 * alias, or its suppress id when it has no alias. The suppress id also matches the alias. A code that the
 * comment already lists, in any spelling, is not offered again. After a `pycharm:` namespace, the variants
 * complete the text after it.
 */
class PyIgnoreCodeCompletionContributor : CompletionContributor() {
  init {
    extend(CompletionType.BASIC, psiComment().withLanguage(PythonLanguage.INSTANCE), PyIgnoreCodeCompletionProvider())
  }
}

private class PyIgnoreCodeCompletionProvider : CompletionProvider<CompletionParameters>() {
  override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
    val comment = parameters.position
    val textBeforeCaret = comment.text.substring(0, parameters.offset - comment.textRange.startOffset)
    val matcher = PyIgnoreCommentUtil.UNCLOSED_CODES_PATTERN.matcher(textBeforeCaret)
    if (!matcher.matches()) return
    result.stopHere()

    val isPyCharmDirective = matcher.group(1).equals("pycharm", ignoreCase = true)
    val directive = if (isPyCharmDirective) PyIgnoreCommentUtil.Directive.PYCHARM else PyIgnoreCommentUtil.Directive.TYPE
    val entries = matcher.group(2).split(',')
    val listedCodes = entries.dropLast(1).flatMapTo(HashSet()) { listedSpellings(directive, it.trim()) }
    val codeResult = result.withPrefixMatcher(codePrefix(entries.last()))

    val inspections = PyTypeIgnoreSuppressIds.getInstance().pythonInspections()
    val typeCheckerName = inspections.firstOrNull { it.suppressId == TYPE_CHECKER_SUPPRESS_ID }?.displayName ?: TYPE_CHECKER_SUPPRESS_ID
    for (granular in PyTypeCheckerSuppressionCode.entries) {
      if (granular.id in listedCodes) continue
      codeResult.addElement(LookupElementBuilder.create(granular.id).withTypeText(typeCheckerName, true))
    }
    for (inspection in inspections) {
      if (inspection.suppressId in listedCodes) continue
      val element = LookupElementBuilder.create(inspection.kebabAlias ?: inspection.suppressId)
        .withTypeText(inspection.displayName, true)
        .withCaseSensitivity(false)
      codeResult.addElement(if (inspection.kebabAlias != null) element.withLookupString(inspection.suppressId) else element)
    }
  }
}

/** The part of the last code before the caret. A `pycharm:` namespace and the spaces around it are not part of it. */
private fun codePrefix(lastEntry: String): String = lastEntry.trimStart().replaceFirst(PYCHARM_NAMESPACE_PREFIX, "")

/** The spellings of a listed code: its name, and for an inspection its suppress id and its kebab-case alias. */
private fun listedSpellings(directive: PyIgnoreCommentUtil.Directive, rawCode: String): List<String> {
  val ref = PyIgnoreCommentUtil.codeRef(directive, rawCode) ?: return emptyList()
  val resolution = PyIgnoreCodeResolver.resolve(ref) as? PyIgnoreCodeResolver.Resolution.Inspection ?: return listOf(ref.name)
  return listOfNotNull(ref.name, resolution.suppressId, PySuppressionUtil.toSuppressionCode(resolution.suppressId))
}

/** Opens the code completion after `[` or `,` in the open code list of an ignore comment. */
class PyIgnoreCodeTypedHandler : TypedHandlerDelegate() {
  override fun checkAutoPopup(charTyped: Char, project: Project, editor: Editor, file: PsiFile): Result {
    if (charTyped != '[' && charTyped != ',') return Result.CONTINUE
    if (file !is PyFile) return Result.CONTINUE
    val caret = editor.caretModel.offset
    if (caret == 0) return Result.CONTINUE
    val iterator = (editor as? EditorEx)?.highlighter?.createIterator(caret - 1) ?: return Result.CONTINUE
    if (iterator.atEnd() || iterator.tokenType != PyTokenTypes.END_OF_LINE_COMMENT) return Result.CONTINUE
    val commentBeforeCaret = editor.document.charsSequence.subSequence(iterator.start, caret).toString() + charTyped
    if (!PyIgnoreCommentUtil.UNCLOSED_CODES_PATTERN.matcher(commentBeforeCaret).matches()) return Result.CONTINUE
    AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
    return Result.STOP
  }
}
