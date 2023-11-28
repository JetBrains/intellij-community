package com.jetbrains.python.codeInsight.mlcompletion.correctness.finalizer

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.*
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.psi.*
import com.intellij.platform.ml.impl.correctness.finalizer.SuggestionFinalizerBase
import org.jetbrains.annotations.ApiStatus

@ApiStatus.ScheduledForRemoval
@Deprecated("Do not use it")
class PythonSuggestionFinalizer : SuggestionFinalizerBase(PythonLanguage.INSTANCE) {
  override fun getTraverseOrder(insertedElement: PsiElement): Sequence<PsiElement> {
    return sequence {
      val lastNotComment = generateSequence(insertedElement) { it.prevLeaf() }.firstOrNull { it.text.isNotBlank() && it !is PsiComment }
                           ?: return@sequence
      if (insertedElement is PsiComment) {
        yield(insertedElement)
      }
      if (lastNotComment.node.elementType == PyTokenTypes.BACKSLASH) {
        val lastNotBackslash = generateSequence(lastNotComment) { it.prevLeaf() }.firstOrNull {
          it.node.elementType != PyTokenTypes.BACKSLASH && it !is PsiWhiteSpace
        }
        if (lastNotBackslash != null) {
          yield(lastNotComment)
          yieldAll(lastNotBackslash.parents(withSelf = true))
          return@sequence
        }
      }
      yieldAll(lastNotComment.parents(withSelf = true))
    }
  }

  override fun getFinalizationCandidate(element: PsiElement): String? = when (element) {
    is PyReferenceExpression -> referenceExpression(element)
    is PyStringElement -> stringElement(element)
    is PyFStringFragment -> fStringFragment(element)
    is PyTryPart, is PyFinallyPart -> tryAndFinallyPart(element as PyStatementPart)
    is PyExceptPart -> exceptPart(element)
    is PyTryExceptStatement -> tryExceptStatement(element)
    is PyArgumentList -> argumentList(element)
    is PyDecorator -> decorator(element)
    is PyForPart -> forPart(element)
    is PyWhilePart -> whilePart(element)
    is PyIfPart, is PyElsePart -> ifElsePart(element)
    is PyConditionalExpression -> conditionalExpression(element)
    is PyPrefixExpression -> prefixExpression(element)
    is PyBinaryExpression -> binaryExpression(element)
    is PyAssignmentStatement -> assignmentStatement(element)
    is PyAugAssignmentStatement -> augAssignmentStatement(element)
    is PyWithStatement -> withStatement(element)
    is PySubscriptionExpression -> subscriptionExpression(element)
    is PySliceExpression -> sliceExpression(element)
    is PyComprehensionElement -> comprehensionElement(element)
    is PyTupleExpression, is PyParenthesizedExpression -> tupleAndParenthesizedExpression(element)
    is PyListLiteralExpression -> listLiteral(element)
    is PySetLiteralExpression -> setLiteral(element)
    is PyDictLiteralExpression -> dictLiteral(element)
    is PyImportStatement -> importStatement(element)
    is PyFromImportStatement -> fromImport(element)
    is PyClass -> classDeclaration(element)
    is PyFunction -> functionDeclaration(element)
    is PyNamedParameter -> namedParameter(element)
    is PyStarArgument -> starArgument(element)
    is PyLambdaExpression -> lambdaExpression(element)
    is PyAssertStatement -> assertStatement(element)
    is PyDelStatement -> delStatement(element)
    is PyAnnotation -> annotation(element)
    is PsiComment -> psiComment()
    is LeafPsiElement -> leafPsi(element)
    else -> null
  }

  private fun referenceExpression(element: PyReferenceExpression): String = when {
    element.text.endsWith('.') -> "x"
    else -> ""
  }

  private fun stringElement(element: PyStringElement): String = when {
    !element.isTerminated -> " " + element.quote
    else -> ""
  }

  private fun fStringFragment(element: PyFStringFragment): String = when {
    element.expression == null -> "x}"
    element.closingBrace == null -> "}"
    else -> ""
  }

  private fun tryAndFinallyPart(element: PyStatementPart): String {
    val indent = PyIndentUtil.getElementIndent(element)
    return when {
      element.node.findChildByType(PyTokenTypes.COLON) == null -> ": pass\n"
      element.statementList.statements.isEmpty() -> "$indent pass\n"
      else -> ""
    }
  }

  private fun exceptPart(element: PyExceptPart): String {
    val indent = PyIndentUtil.getElementIndent(element)
    return when {
      element.node.findChildByType(PyTokenTypes.AS_KEYWORD) != null && element.target == null -> " x: pass\n"
      element.node.findChildByType(PyTokenTypes.COLON) == null -> ": pass\n"
      element.statementList.statements.isEmpty() -> "$indent pass\n"
      else -> ""
    }
  }

  private fun tryExceptStatement(element: PyTryExceptStatement): String = when {
    element.exceptParts.isEmpty() && element.finallyPart == null -> {
      val indent = PyIndentUtil.getElementIndent(element)
      "\n${indent}except: pass"
    }
    else -> ""
  }

  private fun argumentList(element: PyArgumentList): String {
    val lastArgument = element.arguments.lastOrNull()
    return when {
      lastArgument is PyKeywordArgument && lastArgument.valueExpression == null -> "x)"
      element.closingParen == null -> ")"
      else -> ""
    }
  }

  private fun decorator(element: PyDecorator): String = when (element.expression) {
    null -> "x\n"
    else -> ""
  }

  private fun forPart(element: PyForPart): String {
    val indent = PyIndentUtil.getElementIndent(element)
    return when {
      element.target == null -> " x in x: pass"
      element.source == null && element.node.findChildByType(PyTokenTypes.IN_KEYWORD) == null -> " in x: pass"
      element.source == null -> " x: pass"
      element.node.findChildByType(PyTokenTypes.COLON) == null -> ": pass"
      (element.lastChild as? PyStatementList)?.statements?.isEmpty() == true -> "$indent pass"
      else -> ""
    }
  }

  private fun whilePart(element: PyWhilePart): String {
    val indent = PyIndentUtil.getElementIndent(element)
    return when {
      element.condition == null -> " x: pass"
      element.node.findChildByType(PyTokenTypes.COLON) == null -> ": pass"
      (element.lastChild as? PyStatementList)?.statements?.isEmpty() == true -> "$indent pass"
      else -> ""
    }
  }

  private fun ifElsePart(element: PsiElement): String {
    val indent = PyIndentUtil.getElementIndent(element)
    return when {
      element is PyIfPart && element.condition == null -> " x: pass"
      element.node.findChildByType(PyTokenTypes.COLON) == null -> ": pass"
      (element.lastChild as? PyStatementList)?.statements?.isEmpty() == true -> "$indent pass"
      else -> ""
    }
  }

  private fun conditionalExpression(element: PyConditionalExpression): String = when {
    element.condition == null -> " x else x"
    element.falsePart == null && element.node.findChildByType(PyTokenTypes.ELSE_KEYWORD) == null -> " else x"
    element.falsePart == null -> " x"
    else -> ""
  }

  private fun prefixExpression(element: PyPrefixExpression): String = when (element.operand) {
    null -> "x"
    else -> ""
  }

  private fun binaryExpression(element: PyBinaryExpression): String = when (element.rightExpression) {
    null -> " x"
    else -> ""
  }

  private fun assignmentStatement(element: PyAssignmentStatement): String = when (element.assignedValue) {
    null -> " x"
    else -> ""
  }

  private fun augAssignmentStatement(element: PyAugAssignmentStatement): String = when (element.value) {
    null -> " x"
    else -> ""
  }

  private fun withStatement(element: PyWithStatement): String {
    val indent = PyIndentUtil.getElementIndent(element)
    return when {
      element.withItems.isEmpty() -> " x: pass"
      element.withItems.last().node.findChildByType(
        PyTokenTypes.AS_KEYWORD) != null && element.withItems.last().target == null -> " x: pass"
      element.node.findChildByType(PyTokenTypes.COLON) == null -> ": pass"
      element.statementList.statements.isEmpty() -> "$indent pass"
      else -> ""
    }
  }

  private fun subscriptionExpression(element: PySubscriptionExpression): String = when {
    element.indexExpression == null -> "x]"
    element.node.findChildByType(PyTokenTypes.RBRACKET) == null -> "]"
    else -> ""
  }

  private fun sliceExpression(element: PySliceExpression): String = when {
    element.node.findChildByType(PyTokenTypes.RBRACKET) == null -> "]"
    else -> ""
  }

  private fun comprehensionElement(element: PyComprehensionElement): String {
    val (closeBracketType, closeBracketText) = when (element) {
      is PyListCompExpression -> PyTokenTypes.RBRACKET to "]"
      is PyGeneratorExpression -> {
        val brace = if (element.firstChild.node.elementType == PyTokenTypes.LPAR) ")" else ""
        PyTokenTypes.RPAR to brace
      }
      else -> PyTokenTypes.RBRACE to "}"
    }

    return when {
      element.forComponents.isEmpty() && element.node.findChildByType(PyTokenTypes.FOR_KEYWORD)!!.psi.siblings(
        withSelf = false).none { it is PyExpression } -> " x in x$closeBracketText"
      element.forComponents.isEmpty() && element.node.findChildByType(
        PyTokenTypes.IN_KEYWORD) == null -> " in x$closeBracketText"
      element.forComponents.isEmpty() -> " x$closeBracketText"
      element.ifComponents.isEmpty() && element.node.findChildByType(
        PyTokenTypes.IF_KEYWORD) != null -> " x$closeBracketText"
      element.node.findChildByType(closeBracketType) == null -> closeBracketText
      else -> ""
    }
  }

  private fun tupleAndParenthesizedExpression(element: PsiElement): String = when {
    element.firstChild.node.elementType == PyTokenTypes.LPAR && element.lastChild.node.elementType != PyTokenTypes.RPAR -> ")"
    else -> ""
  }

  private fun listLiteral(element: PyListLiteralExpression): String = when {
    element.lastChild.node.elementType != PyTokenTypes.RBRACKET -> "]"
    else -> ""
  }

  private fun setLiteral(element: PySetLiteralExpression): String = when {
    element.lastChild.node.elementType != PyTokenTypes.RBRACE -> "}"
    else -> ""
  }

  private fun dictLiteral(element: PyDictLiteralExpression): String {
    val nonLeafChildren = element.children.filter { child -> child.firstChild != null }
    val lastExpr = element.elements.lastOrNull()
    return when {
      element.node.findChildByType(PyTokenTypes.COLON) != null || (lastExpr != null && lastExpr.value == null) -> "x}"
      nonLeafChildren.size > element.elements.size -> ":x}"
      element.lastChild.node.elementType != PyTokenTypes.RBRACE -> "}"
      else -> ""
    }
  }

  private fun importStatement(element: PyImportStatement): String {
    val lastImport = element.importElements.lastOrNull()
    val commaAfterLastImport = lastImport?.siblings(withSelf = false)?.firstOrNull { it.node.elementType == PyTokenTypes.COMMA }
    return when {
      lastImport == null -> " x"
      commaAfterLastImport != null -> " x"
      lastImport.node.findChildByType(PyTokenTypes.AS_KEYWORD) != null && lastImport.asNameElement == null -> " x"
      else -> ""
    }
  }

  private fun fromImport(element: PyFromImportStatement): String {
    val lastImport = element.importElements.lastOrNull()
    return when {
      element.importSource == null && element.node.findChildByType(PyTokenTypes.IMPORT_KEYWORD) == null -> " x import x"
      element.node.findChildByType(PyTokenTypes.IMPORT_KEYWORD) == null -> " import x"
      element.leftParen == null && element.starImportElement == null && lastImport != null && lastImport.importedQName == null -> " x"
      element.leftParen == null && element.starImportElement == null && lastImport != null && lastImport.node.findChildByType(
        PyTokenTypes.AS_KEYWORD) != null && lastImport.asNameElement == null -> " x"
      element.leftParen != null && element.rightParen == null && lastImport != null && lastImport.importedQName == null -> " x)"
      element.leftParen != null && element.rightParen == null && lastImport != null && lastImport.node.findChildByType(
        PyTokenTypes.AS_KEYWORD) != null && lastImport.asNameElement == null -> " x)"
      element.leftParen != null && element.rightParen == null -> ")"
      else -> ""
    }
  }

  private fun classDeclaration(element: PyClass): String {
    val indent = PyIndentUtil.getElementIndent(element)
    return when {
      element.nameNode == null -> " x: pass"
      element.node.findChildByType(PyTokenTypes.COLON) == null -> ": pass"
      element.statementList.statements.isEmpty() -> "$indent pass"
      else -> ""
    }
  }

  private fun functionDeclaration(element: PyFunction): String {
    val indent = PyIndentUtil.getElementIndent(element)
    return when {
      element.node.findChildByType(PyTokenTypes.DEF_KEYWORD) == null -> "\n${indent}def x(): pass"
      element.nameNode == null -> " x(): pass"
      element.parameterList.node.findChildByType(PyTokenTypes.LPAR) == null -> "(): pass"
      element.parameterList.node.findChildByType(PyTokenTypes.RPAR) == null -> "): pass"
      element.node.findChildByType(PyTokenTypes.COLON) == null -> ": pass"
      element.statementList.statements.isEmpty() -> "$indent pass"
      else -> ""
    }
  }

  private fun namedParameter(element: PyNamedParameter): String = when {
    element.node.findChildByType(PyTokenTypes.EQ) != null && !element.hasDefaultValue() -> "x"
    else -> ""
  }

  private fun starArgument(element: PyStarArgument): String = when {
    PsiTreeUtil.getChildOfType(element, PyReferenceExpression::class.java) == null -> "x"
    else -> ""
  }

  private fun lambdaExpression(element: PyLambdaExpression): String = when {
    element.node.findChildByType(PyTokenTypes.COLON) == null -> ": 0"
    element.body == null -> "0"
    else -> ""
  }

  private fun assertStatement(element: PyAssertStatement): String = when {
    element.arguments.isEmpty() -> " x"
    else -> ""
  }

  private fun delStatement(element: PyDelStatement): String = when {
    element.targets.isEmpty() -> " x"
    else -> ""
  }

  private fun annotation(element: PyAnnotation): String = when (element.value) {
    null -> "x"
    else -> ""
  }

  private fun psiComment(): String = "\n"

  private fun leafPsi(element: LeafPsiElement): String = when {
    element.textContains('\\') -> "\n"
    element.node.elementType == PyTokenTypes.EXP && element.parent?.parent is PyParameterList -> "x"
    else -> ""
  }
}