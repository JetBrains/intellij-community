// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.intentions

import com.intellij.codeInsight.controlflow.ControlFlowUtil
import com.intellij.codeInspection.SuppressionUtil
import com.intellij.lang.ASTNode
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parents
import com.intellij.psi.util.parentsOfType
import com.intellij.psi.util.siblings
import com.intellij.util.IncorrectOperationException
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.codeInsight.ConditionUtil
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache
import com.jetbrains.python.codeInsight.controlflow.ReadWriteInstruction
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyPsiUtils

/**
 * Inverts 'if' condition branches
 *
 * @author Vasya Aksyonov
 */
class PyInvertIfConditionIntention : PyBaseIntentionAction() {
  private companion object {
    val insignificantTokenSet = TokenSet.create(TokenType.WHITE_SPACE, PyTokenTypes.END_OF_LINE_COMMENT)
    const val PYLINT_COMMENT_PREFIX = "# pylint:"
  }

  init {
    text = PyPsiBundle.message("INTN.invert.if.condition")
  }

  override fun getFamilyName(): String = text

  override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
    if (file !is PyFile) {
      return false
    }

    val element = file.findElementAt(editor.caretModel.offset) ?: return false

    val conditionalExpression = element.parentsOfType<PyConditionalExpression>().firstOrNull()
    if (conditionalExpression != null) {
      return conditionalExpression.condition != null &&
             conditionalExpression.falsePart != null
    }

    val ifStatement = element.parentsOfType<PyIfStatement>().firstOrNull()
    if (ifStatement != null) {
      return ifStatement.ifPart.condition != null &&
             ifStatement.elifParts.isEmpty() &&
             isAvailableForIfStatement(element, ifStatement)
    }

    return false
  }

  private fun isAvailableForIfStatement(element: PsiElement, statement: PyIfStatement): Boolean {
    val parents = element.parents.takeWhile { it != statement }

    if (parents.contains(statement.ifPart.statementList)) {
      return false
    }

    val elsePart = statement.elsePart
    if (elsePart != null && parents.contains(elsePart.statementList)) {
      return false
    }

    return true
  }

  override fun doInvoke(project: Project, editor: Editor, file: PsiFile) {
    val element = file.findElementAt(editor.caretModel.offset) ?: return

    val conditionalExpression = element.parentsOfType<PyConditionalExpression>().firstOrNull()
    if (conditionalExpression != null) {
      invertConditional(project, file, conditionalExpression)
      return
    }

    val ifStatement = element.parentsOfType<PyIfStatement>().firstOrNull()
    if (ifStatement != null) {
      if (ifStatement.ifPart.condition == null || ifStatement.elifParts.isNotEmpty()) {
        return
      }

      val elsePart = ifStatement.elsePart
      if (elsePart != null) {
        invertIfStatementComplete(project, file, ifStatement)
        return
      }

      val terminableStatement = ifStatement.findTerminableParent()
      if (terminableStatement == null) {
        invertIfStatementIncomplete(project, editor, file, ifStatement)
        return
      }

      if (!ifStatement.ifPart.statementList.isTerminated &&
          ifStatement.parent.lastSignificantChild != ifStatement) {
        invertIfStatementIncomplete(project, editor, file, ifStatement)
        return
      }

      invertIfStatementFollowup(project, file, ifStatement, terminableStatement)
      return
    }

    throw IncorrectOperationException("Is not a condition")
  }

  private fun invertConditional(project: Project, file: PsiFile, expression: PyConditionalExpression) {
    val condition = expression.condition
    val falsePart = expression.falsePart
    if (condition != null && falsePart != null) {
      ConditionUtil.invertConditionalExpression(project, file, condition)
      val originalFalsePart = falsePart.copy()
      falsePart.replace(expression.truePart)
      expression.truePart.replace(originalFalsePart)
    }
  }

  private fun invertIfStatementComplete(project: Project, file: PsiFile, statement: PyIfStatement) {
    ConditionUtil.invertConditionalExpression(project, file, statement.ifPart.condition!!)

    val ifStatements = statement.ifPart.statementList
    val elseStatements = statement.elsePart!!.statementList

    val ifLastChild = ifStatements.lastChild
    val elseLastChild = elseStatements.lastChild

    elseStatements.addRange(ifStatements.firstChild, ifLastChild)
    ifStatements.addRange(elseStatements.firstChild, elseLastChild)

    ifStatements.deleteChildRange(ifStatements.firstChild, ifLastChild)
    elseStatements.deleteChildRange(elseStatements.firstChild, elseLastChild)

    switchAttachedComments(statement, statement.elsePart!!)
    switchInlineComments(statement)
  }

  private fun invertIfStatementIncomplete(project: Project, editor: Editor, file: PsiFile, statement: PyIfStatement) {
    val invertedCondition = ConditionUtil.invertConditionalExpression(project, file, statement.ifPart.condition!!)

    val level = LanguageLevel.forElement(file)
    val generator = PyElementGenerator.getInstance(project)

    // Switching statements
    val completeStatement = generator.createFromText(level, PyIfStatement::class.java, "if a:\n\tpass\nelse:\n\tpass")
    completeStatement.ifPart.condition!!.replace(invertedCondition)
    completeStatement.elsePart!!.statementList.replace(statement.ifPart.statementList)
    val newStatement = statement.replace(completeStatement) as PyIfStatement

    switchAttachedComments(newStatement, newStatement.elsePart!!)
    switchInlineComments(newStatement)

    // Highlighting placeholder
    val passStatementRange = newStatement.ifPart.statementList.statements[0].textRange
    editor.caretModel.primaryCaret.setSelection(passStatementRange.startOffset, passStatementRange.endOffset)
  }

  private fun invertIfStatementFollowup(project: Project, file: PsiFile, statement: PyIfStatement, terminableStatement: PyStatement) {
    ConditionUtil.invertConditionalExpression(project, file, statement.ifPart.condition!!)

    val ifStatements = statement.ifPart.statementList

    // Switching statements
    val parent = statement.parent
    val ifFirstChild = ifStatements.firstChild
    val ifLastChild = ifStatements.lastChild

    val followupFirstChild = statement.nextMovableSibling
    val followupLastChild = parent.lastChild

    parent.addRange(ifFirstChild, ifLastChild)
    if (followupFirstChild != null) {
      ifStatements.addRange(followupFirstChild, followupLastChild)
    }

    switchAttachedComments(statement, statement.nextMovableSibling!!)

    ifStatements.deleteChildRange(ifFirstChild, ifLastChild)
    if (followupFirstChild != null) {
      parent.deleteChildRange(followupFirstChild, followupLastChild)
    }

    // Fixing terminations
    val followupTerminator = if (statement.parent.lastSignificantChild.isTerminationStatement)
      statement.parent.lastSignificantChild
    else
      null
    if (followupTerminator != null && !followupTerminator.isValuableTerminationStatement(terminableStatement)) {
      followupTerminator.delete()
      statement.parent.trimTrailingWhiteSpace()
    }

    if (!ifStatements.isTerminated &&
        statement.parent.lastSignificantChild != statement &&
        ifStatements.findTerminationStatement() == null) {
      ifStatements.add(terminableStatement.createTerminationStatement(file, project))
    }

    CodeEditUtil.markToReformat(parent.node, true)
    CodeEditUtil.markToReformat(ifStatements.node, true)
  }

  private fun switchAttachedComments(statement: PyIfStatement, oppositeAnchor: PsiElement) {
    val oppositeComments = oppositeAnchor.attachedComments
    val ifComments = statement.attachedComments

    if (ifComments.isNotEmpty()) {
      oppositeAnchor.parent.addRangeBefore(ifComments.first(), ifComments.last(), oppositeAnchor)
      statement.parent.deleteChildRange(ifComments.first(), ifComments.last())
    }

    if (oppositeComments.isNotEmpty()) {
      statement.parent.addRangeBefore(oppositeComments.first(), oppositeComments.last(), statement)
      oppositeAnchor.parent.deleteChildRange(oppositeComments.first(), oppositeComments.last())
    }
  }

  private fun switchInlineComments(statement: PyIfStatement) {
    val ifPart = statement.ifPart
    val elsePart = statement.elsePart ?: return

    val ifComment = ifPart.childComment
    val elseComment = elsePart.childComment
    if (ifComment?.isInlineServiceComment == true || elseComment?.isInlineServiceComment == true) {
      return
    }

    if (ifComment != null) {
      val trailingColonNode = elsePart.statementList.node.prevSignificantNode!!
      CodeEditUtil.addChild(elsePart.node, ifComment.node.copyElement(), trailingColonNode.treeNext)
      ifComment.delete()
    }

    if (elseComment != null) {
      val trailingColonNode = ifPart.statementList.node.prevSignificantNode!!
      CodeEditUtil.addChild(ifPart.node, elseComment.node.copyElement(), trailingColonNode.treeNext)
      elseComment.delete()
    }
  }

  private val PyStatementList.isTerminated: Boolean
    get() {
      val controlFlow = ControlFlowCache.getControlFlow(parentsOfType<ScopeOwner>().first())
      val currentInstruction = controlFlow.instructions.first { it.element == this }
      var result = true
      ControlFlowUtil.iterate(currentInstruction.num(), controlFlow.instructions, { instruction ->
        when {
          instruction == currentInstruction -> ControlFlowUtil.Operation.NEXT
          instruction is ReadWriteInstruction -> ControlFlowUtil.Operation.NEXT
          instruction.element == null || !instruction.element!!.parents.contains(this) -> {
            result = false
            ControlFlowUtil.Operation.BREAK
          }
          instruction.element.isTerminationStatement -> ControlFlowUtil.Operation.CONTINUE
          else -> ControlFlowUtil.Operation.NEXT
        }
      }, false)
      return result
    }

  private fun PsiElement.findTerminableParent() = PsiTreeUtil.getParentOfType(
    this, PyFunction::class.java, PyLoopStatement::class.java)

  private fun PsiElement.findTerminationStatement(): PsiElement? = children.firstOrNull { it.isTerminationStatement }

  private val PsiElement?.isTerminationStatement: Boolean
    get() = this is PyReturnStatement ||
            this is PyRaiseStatement ||
            this is PyContinueStatement ||
            this is PyBreakStatement

  private fun PsiElement.isValuableTerminationStatement(terminableStatement: PyStatement): Boolean =
    this is PyReturnStatement && (expression != null || terminableStatement !is PyFunction) ||
    this is PyRaiseStatement ||
    this is PyBreakStatement

  private fun PyStatement.createTerminationStatement(file: PsiFile, project: Project): PsiElement {
    val level = LanguageLevel.forElement(file)
    val generator = PyElementGenerator.getInstance(project)
    return when (this) {
      is PyFunction -> generator.createFromText(level, PyReturnStatement::class.java, "return")
      is PyLoopStatement -> generator.createFromText(level, PyContinueStatement::class.java, "continue")
      else -> throw IncorrectOperationException("${javaClass.name} is not a terminable statement")
    }
  }

  private val PsiElement.attachedComments: List<PsiComment>
    get() = PyPsiUtils.getPrecedingComments(this).takeWhile { !it.isAttachedServiceComment }

  private val PsiElement.childComment
    get() = node.getChildren(null).filterIsInstance<PsiComment>().firstOrNull()

  private val ASTNode.prevSignificantNode
    get() = PyPsiUtils.skipSiblingsBackward(this, insignificantTokenSet)

  private val PsiElement.lastSignificantChild
    get() = PyPsiUtils.getPrevNonCommentSibling(lastChild, false)

  private val PsiElement.nextMovableSibling
    get() = siblings(withSelf = false).firstOrNull {
      it !is PsiWhiteSpace &&
      (it !is PsiComment || it.isAttachedServiceComment)
    }

  private val PsiComment.isAttachedServiceComment
    get() = SuppressionUtil.isSuppressionComment(this) || isPylintComment

  private val PsiComment.isInlineServiceComment
    get() = PyTypeHintGenerationUtil.isTypeHintComment(this) || isPylintComment

  private val PsiComment.isPylintComment
    get() = text.startsWith(PYLINT_COMMENT_PREFIX)

  private fun PsiElement.trimTrailingWhiteSpace() {
    var lastChild = lastChild
    while (lastChild is PsiWhiteSpace) {
      val prevSibling = lastChild.prevSibling
      lastChild.delete()
      lastChild = prevSibling
    }
  }

}