/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.breadcrumbs

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.psi.*

class PyBreadcrumbsInfoProvider : BreadcrumbsProvider {

  companion object {
    private val LANGUAGES = arrayOf(PythonLanguage.getInstance())
    private val HELPERS = listOf<Helper<*>>(
      LambdaHelper,
      SimpleHelper(PyTryPart::class.java, "try"),
      ExceptHelper,
      SimpleHelper(PyFinallyPart::class.java, "finally"),
      SimpleHelper(PyElsePart::class.java, "else"),
      IfHelper,
      ForHelper,
      WhileHelper,
      WithHelper,
      ClassHelper,
      FunctionHelper,
      KeyValueHelper
    )
  }

  override fun getLanguages() = LANGUAGES

  override fun acceptElement(e: PsiElement) = getHelper(e) != null

  override fun getParent(e: PsiElement): PsiElement? {
    val default = e.parent

    val currentOffset = currentOffset(e) ?: return default
    if (!isElementToMoveBackward(e, currentOffset)) return default

    val nonWhiteSpace = moveBackward(e, currentOffset) ?: return default

    val psiFile = e.containingFile ?: return default
    val document = PsiDocumentManager.getInstance(e.project).getDocument(psiFile) ?: return default
    val sameLine = document.getLineNumber(nonWhiteSpace.textOffset) == document.getLineNumber(currentOffset)

    return if (sameLine) nonWhiteSpace.parent else default
  }

  override fun getElementInfo(e: PsiElement) = getHelper(e)!!.elementInfo(e as PyElement)
  override fun getElementTooltip(e: PsiElement) = getHelper(e)!!.elementTooltip(e as PyElement)

  private fun getHelper(e: PsiElement): Helper<in PyElement>? {
    if (e !is PyElement) return null

    @Suppress("UNCHECKED_CAST")
    return HELPERS.firstOrNull { it.type.isInstance(e) } as Helper<in PyElement>?
  }

  private fun currentOffset(e: PsiElement): Int? {
    val virtualFile = e.containingFile?.virtualFile ?: return null
    val selectedEditor = FileEditorManager.getInstance(e.project).getSelectedEditor(virtualFile) as? TextEditor ?: return null

    return selectedEditor.editor.caretModel.offset
  }

  private fun isElementToMoveBackward(e: PsiElement, currentOffset: Int): Boolean {
    if (e is PsiWhiteSpace) return true
    if (e !is LeafElement || e.startOffset < currentOffset) return false

    val elementType = e.elementType
    return elementType == PyTokenTypes.COMMA || elementType in PyTokenTypes.CLOSE_BRACES
  }

  private fun moveBackward(e: PsiElement, currentOffset: Int): PsiElement? {
    var result = PsiTreeUtil.prevLeaf(e)
    while (result != null && isElementToMoveBackward(result, currentOffset)) {
      result = PsiTreeUtil.prevLeaf(result)
    }
    return result
  }

  private abstract class Helper<T : PyElement>(val type: Class<T>) {
    fun elementInfo(e: T): String = getTruncatedPresentation(e, 32)
    fun elementTooltip(e: T): String = getTruncatedPresentation(e, 96)

    abstract fun getPresentation(e: T): String

    private fun getTruncatedPresentation(e: T, maxLength: Int) = StringUtil.shortenTextWithEllipsis(getPresentation(e), maxLength, 0, true)
  }

  private class SimpleHelper<T : PyElement>(type: Class<T>, val representation: String) : Helper<T>(type) {
    override fun getPresentation(e: T) = representation
  }

  private object LambdaHelper : Helper<PyLambdaExpression>(PyLambdaExpression::class.java) {
    override fun getPresentation(e: PyLambdaExpression) = "lambda ${e.parameterList.getPresentableText(false)}"
  }

  private object ExceptHelper : Helper<PyExceptPart>(PyExceptPart::class.java) {
    override fun getPresentation(e: PyExceptPart): String {
      val exceptClass = e.exceptClass ?: return "except"
      val target = e.target ?: return "except ${exceptClass.text}"

      return "except ${exceptClass.text} as ${target.text}"
    }
  }

  private object IfHelper : Helper<PyIfPart>(PyIfPart::class.java) {
    override fun getPresentation(e: PyIfPart): String {
      val prefix = if (e.isElif) "elif" else "if"
      val condition = e.condition ?: return prefix

      return "$prefix ${condition.text}"
    }
  }

  private object ForHelper : Helper<PyForPart>(PyForPart::class.java) {
    override fun getPresentation(e: PyForPart): String {
      val parent = e.parent
      val prefix = if (parent is PyForStatement && parent.isAsync) "async for" else "for"

      val target = e.target ?: return prefix
      val source = e.source ?: return prefix

      return "$prefix ${target.text} in ${source.text}"
    }
  }

  private object WhileHelper : Helper<PyWhilePart>(PyWhilePart::class.java) {
    override fun getPresentation(e: PyWhilePart): String {
      val condition = e.condition ?: return "while"

      return "while ${condition.text}"
    }
  }

  private object WithHelper : Helper<PyWithStatement>(PyWithStatement::class.java) {
    override fun getPresentation(e: PyWithStatement): String {
      val getItemPresentation = fun(item: PyWithItem): String? {
        val expression = item.expression ?: return null
        val target = item.target ?: return expression.text
        return "${expression.text} as ${target.text}"
      }

      val prefix = if (e.isAsync) "async with " else "with "

      return e.withItems
        .orEmpty()
        .asSequence()
        .map(getItemPresentation)
        .filterNotNull()
        .joinToString(prefix = prefix)
    }
  }

  private object ClassHelper : Helper<PyClass>(PyClass::class.java) {
    override fun getPresentation(e: PyClass) = e.name ?: "class"
  }

  private object FunctionHelper : Helper<PyFunction>(PyFunction::class.java) {
    override fun getPresentation(e: PyFunction): String {
      val prefix = if (e.isAsync) "async " else ""
      val name = e.name ?: return "function"

      return "$prefix$name()"
    }
  }

  private object KeyValueHelper : Helper<PyKeyValueExpression>(PyKeyValueExpression::class.java) {
    override fun getPresentation(e: PyKeyValueExpression): String = e.key.text ?: "key"
  }
}

