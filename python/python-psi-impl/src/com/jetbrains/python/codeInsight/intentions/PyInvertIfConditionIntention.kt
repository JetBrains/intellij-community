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
import com.intellij.psi.util.parents
import com.intellij.psi.util.parentsOfType
import com.intellij.psi.util.siblings
import com.intellij.util.IncorrectOperationException
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache
import com.jetbrains.python.codeInsight.controlflow.ReadWriteInstruction
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner
import com.jetbrains.python.codeInsight.getInvertedConditionExpression
import com.jetbrains.python.codeInsight.isValidConditionExpression
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
             conditionalExpression.falsePart != null &&
             isValidConditionExpression(conditionalExpression.condition!!)
    }

    val ifStatement = element.parentsOfType<PyIfStatement>().firstOrNull()
    if (ifStatement != null) {
      return ifStatement.elifParts.isEmpty() &&
             isAvailableForIfStatement(element, ifStatement) &&
             ifStatement.ifPart.condition?.let(::isValidConditionExpression) ?: true
    }

    return false
  }

  private fun isAvailableForIfStatement(element: PsiElement, statement: PyIfStatement): Boolean {
    // Checking correct if part
    val containsConditionErrors =
      statement.ifPart.condition != null &&
      statement.ifPart.children
        .dropWhile { it != statement.ifPart.condition }.drop(1)
        .takeWhile { it != statement.ifPart.statementList }
        .any { it is PsiErrorElement }
    if (containsConditionErrors) {
      return false
    }

    // Checking current element nesting
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

      val ifIsTerminated = ifStatement.ifPart.statementList.isTerminated
      if (!ifIsTerminated && ifStatement.parent.lastSignificantChild != ifStatement) {
        invertIfStatementIncomplete(project, editor, file, ifStatement)
        return
      }

      if (ifIsTerminated && ifStatement.parentStatementListContainer.let {
          !it.isTerminableStatement && !it.statementList.isTerminated
        }) {
        invertIfStatementIncomplete(project, editor, file, ifStatement)
        return
      }

      invertIfStatementFollowup(project, file, ifStatement, terminableStatement)
      return
    }

    throw IncorrectOperationException("Is not a condition")
  }

  private fun invertConditional(project: Project, file: PsiFile, expression: PyConditionalExpression) {
    val falsePart = expression.falsePart
    if (falsePart != null) {
      expression.condition?.let { it.replace(getInvertedConditionExpression(project, file, it)) }
      val originalFalsePart = falsePart.copy()
      falsePart.replace(expression.truePart)
      expression.truePart.replace(originalFalsePart)
    }
  }

  private fun invertIfStatementComplete(project: Project, file: PsiFile, statement: PyIfStatement) {
    statement.ifPart.condition?.let { it.replace(getInvertedConditionExpression(project, file, it)) }

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
    val level = LanguageLevel.forElement(file)
    val generator = PyElementGenerator.getInstance(project)

    // Switching statements
    val completeStatement = generator.createFromText(level, PyIfStatement::class.java, "if a:\n\tpass\nelse:\n\tpass")

    val condition = statement.ifPart.condition
    if (condition != null) {
      val invertedCondition = getInvertedConditionExpression(project, file, condition)
      completeStatement.ifPart.condition!!.replace(invertedCondition)
    }
    else {
      completeStatement.ifPart.condition!!.delete()
    }

    completeStatement.elsePart!!.statementList.replace(statement.ifPart.statementList)

    // Moving inline comment
    val ifComment = statement.ifPart.childComment
    if (ifComment != null) {
      completeStatement.elsePart!!.appendInlineComment(ifComment)
    }

    val newStatement = statement.replace(completeStatement) as PyIfStatement

    switchAttachedComments(newStatement, newStatement.elsePart!!)

    // Highlighting placeholder
    val passStatementRange = newStatement.ifPart.statementList.statements[0].textRange
    editor.caretModel.primaryCaret.setSelection(passStatementRange.startOffset, passStatementRange.endOffset)
    editor.caretModel.primaryCaret.moveToOffset(passStatementRange.endOffset)
  }

  private fun invertIfStatementFollowup(
    project: Project, file: PsiFile, statement: PyIfStatement, terminableStatement: PyStatementListContainer) {
    statement.ifPart.condition?.let { it.replace(getInvertedConditionExpression(project, file, it)) }

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
      ifStatements.add(createTerminationStatement(file, project, terminableStatement))
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
      elsePart.appendInlineComment(ifComment)
      ifComment.delete()
    }

    if (elseComment != null) {
      ifPart.appendInlineComment(elseComment)
      elseComment.delete()
    }
  }

  private val PyStatementList.isTerminated: Boolean
    get() {
      val controlFlow = ControlFlowCache.getControlFlow(parentsOfType<ScopeOwner>().first())
      val currentElement = this
      val currentInstruction = controlFlow.instructions.first { it.element == currentElement }
      var result = true
      ControlFlowUtil.iterate(currentInstruction.num(), controlFlow.instructions, { instruction ->
        when {
          instruction == currentInstruction -> ControlFlowUtil.Operation.NEXT
          instruction is ReadWriteInstruction -> ControlFlowUtil.Operation.NEXT
          instruction.element == null || !instruction.element!!.parents.contains(currentElement) -> {
            result = false
            ControlFlowUtil.Operation.BREAK
          }
          instruction.element.isTerminationStatement -> ControlFlowUtil.Operation.CONTINUE
          else -> ControlFlowUtil.Operation.NEXT
        }
      }, false)
      return result
    }

  /**
   * Terminable statement is a statement which execution could be terminated
   */
  private val PsiElement?.isTerminableStatement: Boolean
    get() = this is PyFunction ||
            this is PyForPart ||
            this is PyWhilePart

  /**
   * Termination statement is a statement which interrupts the execution flow
   */
  private val PsiElement?.isTerminationStatement: Boolean
    get() = this is PyReturnStatement ||
            this is PyRaiseStatement ||
            this is PyContinueStatement ||
            this is PyBreakStatement

  private fun PsiElement.findTerminableParent() = parents.firstOrNull { it.isTerminableStatement } as PyStatementListContainer?

  private fun PsiElement.findTerminationStatement() = children.firstOrNull { it.isTerminationStatement }

  /**
   * Termination statement is valuable if it's removal changes the overall logic
   */
  private fun PsiElement.isValuableTerminationStatement(terminableStatement: PyStatementListContainer): Boolean =
    this is PyReturnStatement && (expression != null || terminableStatement !is PyFunction) ||
    this is PyRaiseStatement ||
    this is PyBreakStatement

  private fun createTerminationStatement(file: PsiFile, project: Project, targetStatement: PyStatementListContainer): PsiElement {
    val level = LanguageLevel.forElement(file)
    val generator = PyElementGenerator.getInstance(project)
    return when (targetStatement) {
      is PyFunction -> generator.createFromText(level, PyReturnStatement::class.java, "return")
      is PyForPart -> generator.createFromText(level, PyContinueStatement::class.java, "continue")
      is PyWhilePart -> generator.createFromText(level, PyContinueStatement::class.java, "continue")
      else -> throw IncorrectOperationException("${javaClass.name} is not a terminable statement")
    }
  }

  private val PsiElement.parentStatementListContainer
    get() = parentsOfType<PyStatementListContainer>().first()

  private val PsiElement.attachedComments: List<PsiComment>
    get() = PyPsiUtils.getPrecedingComments(this).takeWhile { !it.isAttachedServiceComment }

  private val PsiElement.childComment
    get() = node.getChildren(null).filterIsInstance<PsiComment>().firstOrNull()

  private fun PyStatementPart.appendInlineComment(comment: PsiComment) {
    val trailingColonNode = statementList.node.prevSignificantNode!!
    CodeEditUtil.addChild(node, comment.node.copyElement(), trailingColonNode.treeNext)
  }

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
    val trailingWhiteSpace = node.getChildren(null)
      .takeLastWhile { it is PsiWhiteSpace }
      .map { it as PsiWhiteSpace }
    if (trailingWhiteSpace.any()) {
      deleteChildRange(trailingWhiteSpace.first(), trailingWhiteSpace.last())
    }
  }

}