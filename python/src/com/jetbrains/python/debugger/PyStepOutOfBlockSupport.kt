// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.text.CharArrayUtil
import com.intellij.xdebugger.XSourcePosition
import com.jetbrains.python.psi.PyBreakStatement
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyContinueStatement
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyLambdaExpression
import com.jetbrains.python.psi.PyLoopStatement
import com.jetbrains.python.psi.PyRaiseStatement
import com.jetbrains.python.psi.PyRecursiveElementVisitor
import com.jetbrains.python.psi.PyReturnStatement
import com.jetbrains.python.psi.PyStatement
import com.jetbrains.python.psi.PyStatementList
import com.jetbrains.python.psi.PyYieldExpression
import org.jetbrains.annotations.ApiStatus

/** A debug process that can step out of the code block of the paused line. */
@ApiStatus.Internal
interface PyStepOutOfBlockSupport {
  /**
   * Runs or steps over until the paused frame leaves the code block of the paused line.
   * When the paused line is directly in a function body, this is a step out.
   * See [PyCodeBlockStepper] for the choice between the run and the steps.
   */
  fun performStepOutOfBlock()
}

/**
 * The code block that a step out of the block leaves.
 *
 * The stepping continues while the thread stops in the same frame, in [file], on one of [lines].
 * [stackDepth] identifies the frame. A return, an exception or a call changes the depth.
 */
@ApiStatus.Internal
class PyCodeBlockStepping(
  val file: VirtualFile,
  val lines: IntRange,
  val stackDepth: Int,
  /**
   * The 0-based line of the statement after the block, or null when the block can end elsewhere.
   * A run to this line leaves the block much faster than the steps do.
   */
  val exitLine: Int?,
) {
  /** [line] is 0-based. */
  internal fun keepsStepping(file: VirtualFile?, line: Int, stackDepth: Int): Boolean =
    file == this.file && line in lines && stackDepth == this.stackDepth

  companion object {
    /**
     * Finds the code block of the statement at [position].
     *
     * The code block is the compound statement that holds the statement list of the statement, with all its clauses.
     * For a statement in a loop body, it is the whole loop, the loop header included.
     * On the header line of a loop, the code block is that loop, because each iteration stops there.
     * Returns null for a statement directly in a function body, in a class body or at the module level.
     */
    @JvmStatic
    @RequiresReadLock
    fun find(project: Project, position: XSourcePosition, stackDepth: Int): PyCodeBlockStepping? {
      val file = position.file
      val psiFile = PsiManager.getInstance(project).findFile(file) as? PyFile ?: return null
      val document = FileDocumentManager.getInstance().getDocument(file) ?: return null
      if (position.line !in 0 until document.lineCount) return null
      val offset = CharArrayUtil.shiftForward(document.charsSequence, document.getLineStartOffset(position.line), " \t")
      val element = psiFile.findElementAt(offset) ?: return null
      val loop = PsiTreeUtil.getParentOfType(element, PyLoopStatement::class.java)
      val block = if (loop != null && document.getLineNumber(loop.textRange.startOffset) == position.line) {
        loop
      }
      else {
        val statementList = PsiTreeUtil.getParentOfType(element, PyStatementList::class.java) ?: return null
        PsiTreeUtil.getParentOfType(statementList, PyStatement::class.java) ?: return null
      }
      if (block is PyFunction || block is PyClass) return null
      val range = block.textRange
      val lines = document.getLineNumber(range.startOffset)..document.getLineNumber(range.endOffset)
      return PyCodeBlockStepping(file, lines, stackDepth, findExitLine(block, document))
    }

    /**
     * Returns the line of the statement after [block] when the block can end only at its end.
     * A `return`, a `raise`, a `yield`, or a `break` or a `continue` of an outer loop ends the block elsewhere.
     * An exception from a called function can end it elsewhere too, and the run then does not stop.
     */
    private fun findExitLine(block: PyStatement, document: Document): Int? {
      val next = PsiTreeUtil.getNextSiblingOfType(block, PyStatement::class.java) ?: return null
      var endsOnlyAtEnd = true
      block.accept(object : PyRecursiveElementVisitor() {
        override fun visitPyFunction(node: PyFunction) {}
        override fun visitPyClass(node: PyClass) {}
        override fun visitPyLambdaExpression(node: PyLambdaExpression) {}
        override fun visitPyReturnStatement(node: PyReturnStatement) {
          endsOnlyAtEnd = false
        }
        override fun visitPyRaiseStatement(node: PyRaiseStatement) {
          endsOnlyAtEnd = false
        }
        override fun visitPyYieldExpression(node: PyYieldExpression) {
          endsOnlyAtEnd = false
        }
        override fun visitPyBreakStatement(node: PyBreakStatement) {
          if (!isInBlock(node.loopStatement)) endsOnlyAtEnd = false
        }
        override fun visitPyContinueStatement(node: PyContinueStatement) {
          if (!isInBlock(node.loopStatement)) endsOnlyAtEnd = false
        }
        private fun isInBlock(loop: PyLoopStatement?): Boolean = loop != null && PsiTreeUtil.isAncestor(block, loop, false)
      })
      return if (endsOnlyAtEnd) document.getLineNumber(next.textRange.startOffset) else null
    }
  }
}
