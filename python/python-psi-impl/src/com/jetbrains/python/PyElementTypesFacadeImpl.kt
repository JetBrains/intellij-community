// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.tree.IReparseableElementType
import com.jetbrains.python.psi.impl.*
import java.util.function.Function


typealias F = Function<in ASTNode, out PsiElement>

class PyElementTypesFacadeImpl : PyElementTypesFacade() {

  override val functionDeclaration: IStubElementType<*, *>
    get() = PyStubElementTypes.FUNCTION_DECLARATION
  override val classDeclaration: IStubElementType<*, *>
    get() = PyStubElementTypes.CLASS_DECLARATION
  override val parameterList: IStubElementType<*, *>
    get() = PyStubElementTypes.PARAMETER_LIST
  override val decoratorList: IStubElementType<*, *>
    get() = PyStubElementTypes.DECORATOR_LIST
  override val namedParameter: IStubElementType<*, *>
    get() = PyStubElementTypes.NAMED_PARAMETER
  override val tupleParameter: IStubElementType<*, *>
    get() = PyStubElementTypes.TUPLE_PARAMETER
  override val slashParameter: IStubElementType<*, *>
    get() = PyStubElementTypes.SLASH_PARAMETER
  override val singleStarParameter: IStubElementType<*, *>
    get() = PyStubElementTypes.SINGLE_STAR_PARAMETER
  override val decoratorCall: IStubElementType<*, *>
    get() = PyStubElementTypes.DECORATOR_CALL
  override val importElement: IStubElementType<*, *>
    get() = PyStubElementTypes.IMPORT_ELEMENT
  override val annotation: IStubElementType<*, *>
    get() = PyStubElementTypes.ANNOTATION
  override val starImportElement: IStubElementType<*, *>
    get() = PyStubElementTypes.STAR_IMPORT_ELEMENT
  override val exceptPart: IStubElementType<*, *>
    get() = PyStubElementTypes.EXCEPT_PART
  override val fromImportStatement: IStubElementType<*, *>
    get() = PyStubElementTypes.FROM_IMPORT_STATEMENT
  override val importStatement: IStubElementType<*, *>
    get() = PyStubElementTypes.IMPORT_STATEMENT
  override val targetExpression: IStubElementType<*, *>
    get() = PyStubElementTypes.TARGET_EXPRESSION
  override val typeParameter: IStubElementType<*, *>
    get() = PyStubElementTypes.TYPE_PARAMETER
  override val typeParameterList: IStubElementType<*, *>
    get() = PyStubElementTypes.TYPE_PARAMETER_LIST
  override val typeAliasStatement: IStubElementType<*, *>
    get() = PyStubElementTypes.TYPE_ALIAS_STATEMENT

  override val statementList: IReparseableElementType
    get() = PyStatementListElementType()

  override val argumentListConstructor: F
    get() = F { node: ASTNode -> PyArgumentListImpl(node) }
  override val printTargetConstructor: F
    get() = F { node: ASTNode -> PyPrintTargetImpl(node) }
  override val decoratorConstructor: F
    get() = F { node: ASTNode -> PyDecoratorImpl(node) }

  override val expressionStatementConstructor: F
    get() = F { node -> PyExpressionStatementImpl(node) }
  override val assignmentStatementConstructor: F
    get() = F { node -> PyAssignmentStatementImpl(node) }
  override val augAssignmentStatementConstructor: F
    get() = F { node -> PyAugAssignmentStatementImpl(node) }
  override val assertStatementConstructor: F
    get() = F { node -> PyAssertStatementImpl(node) }
  override val breakStatementConstructor: F
    get() = F { node -> PyBreakStatementImpl(node) }
  override val continueStatementConstructor: F
    get() = F { node -> PyContinueStatementImpl(node) }
  override val delStatementConstructor: F
    get() = F { node -> PyDelStatementImpl(node) }
  override val execStatementConstructor: F
    get() = F { node -> PyExecStatementImpl(node) }
  override val forStatementConstructor: F
    get() = F { node -> PyForStatementImpl(node) }
  override val typeDeclarationStatementConstructor: F
    get() = F { node -> PyTypeDeclarationStatementImpl(node) }
  override val globalStatementConstructor: F
    get() = F { node -> PyGlobalStatementImpl(node) }
  override val ifStatementConstructor: F
    get() = F { node -> PyIfStatementImpl(node) }
  override val passStatementConstructor: F
    get() = F { node -> PyPassStatementImpl(node) }
  override val printStatementConstructor: F
    get() = F { node -> PyPrintStatementImpl(node) }
  override val raiseStatementConstructor: F
    get() = F { node -> PyRaiseStatementImpl(node) }
  override val returnStatementConstructor: F
    get() = F { node -> PyReturnStatementImpl(node) }
  override val tryExceptStatementConstructor: F
    get() = F { node -> PyTryExceptStatementImpl(node) }
  override val withStatementConstructor: F
    get() = F { node -> PyWithStatementImpl(node) }
  override val whileStatementConstructor: F
    get() = F { node -> PyWhileStatementImpl(node) }
  override val nonlocalStatementConstructor: F
    get() = F { node -> PyNonlocalStatementImpl(node) }
  override val withItemConstructor: F
    get() = F { node -> PyWithItemImpl(node) }
  override val emptyExpressionConstructor: F
    get() = F { node -> PyEmptyExpressionImpl(node) }
  override val referenceExpressionConstructor: F
    get() = F { node -> PyReferenceExpressionImpl(node) }
  override val integerLiteralExpressionConstructor: F
    get() = F { node -> PyNumericLiteralExpressionImpl(node) }
  override val floatLiteralExpressionConstructor: F
    get() = F { node -> PyNumericLiteralExpressionImpl(node) }
  override val imaginaryLiteralExpressionConstructor: F
    get() = F { node -> PyNumericLiteralExpressionImpl(node) }
  override val stringLiteralExpressionConstructor: F
    get() = F { node -> PyStringLiteralExpressionImpl(node) }
  override val noneLiteralExpressionConstructor: F
    get() = F { node -> PyNoneLiteralExpressionImpl(node) }
  override val boolLiteralExpressionConstructor: F
    get() = F { node -> PyBoolLiteralExpressionImpl(node) }
  override val parenthesizedExpressionConstructor: F
    get() = F { node -> PyParenthesizedExpressionImpl(node) }
  override val subscriptionExpressionConstructor: F
    get() = F { node -> PySubscriptionExpressionImpl(node) }
  override val sliceExpressionConstructor: F
    get() = F { node -> PySliceExpressionImpl(node) }
  override val sliceItemConstructor: F
    get() = F { node -> PySliceItemImpl(node) }
  override val binaryExpressionConstructor: F
    get() = F { node -> PyBinaryExpressionImpl(node) }
  override val prefixExpressionConstructor: F
    get() = F { node -> PyPrefixExpressionImpl(node) }
  override val callExpressionConstructor: F
    get() = F { node -> PyCallExpressionImpl(node) }
  override val listLiteralExpressionConstructor: F
    get() = F { node -> PyListLiteralExpressionImpl(node) }
  override val tupleExpressionConstructor: F
    get() = F { node -> PyTupleExpressionImpl(node) }
  override val keywordArgumentExpressionConstructor: F
    get() = F { node -> PyKeywordArgumentImpl(node) }
  override val starArgumentExpressionConstructor: F
    get() = F { node -> PyStarArgumentImpl(node) }
  override val lambdaExpressionConstructor: F
    get() = F { node -> PyLambdaExpressionImpl(node) }
  override val listCompExpressionConstructor: F
    get() = F { node -> PyListCompExpressionImpl(node) }
  override val dictLiteralExpressionConstructor: F
    get() = F { node -> PyDictLiteralExpressionImpl(node) }
  override val keyValueExpressionConstructor: F
    get() = F { node -> PyKeyValueExpressionImpl(node) }
  override val reprExpressionConstructor: F
    get() = F { node -> PyReprExpressionImpl(node) }
  override val generatorExpressionConstructor: F
    get() = F { node -> PyGeneratorExpressionImpl(node) }
  override val conditionalExpressionConstructor: F
    get() = F { node -> PyConditionalExpressionImpl(node) }
  override val yieldExpressionConstructor: F
    get() = F { node -> PyYieldExpressionImpl(node) }
  override val starExpressionConstructor: F
    get() = F { node -> PyStarExpressionImpl(node) }
  override val doubleStarExpressionConstructor: F
    get() = F { node -> PyDoubleStarExpressionImpl(node) }
  override val assignmentExpressionConstructor: F
    get() = F { node -> PyAssignmentExpressionImpl(node) }
  override val setLiteralExpressionConstructor: F
    get() = F { node -> PySetLiteralExpressionImpl(node) }
  override val setCompExpressionConstructor: F
    get() = F { node -> PySetCompExpressionImpl(node) }
  override val dictCompExpressionConstructor: F
    get() = F { node -> PyDictCompExpressionImpl(node) }
  override val ifPartIfConstructor: F
    get() = F { node -> PyIfPartIfImpl(node) }
  override val ifPartElifConstructor: F
    get() = F { node -> PyIfPartElifImpl(node) }
  override val forPartConstructor: F
    get() = F { node -> PyForPartImpl(node) }
  override val whilePartConstructor: F
    get() = F { node -> PyWhilePartImpl(node) }
  override val tryPartConstructor: F
    get() = F { node -> PyTryPartImpl(node) }
  override val finallyPartConstructor: F
    get() = F { node -> PyFinallyPartImpl(node) }
  override val elsePartConstructor: F
    get() = F { node -> PyElsePartImpl(node) }
  override val fStringNodeConstructor: F
    get() = F { node -> PyFormattedStringElementImpl(node) }
  override val fStringFragmentConstructor: F
    get() = F { node -> PyFStringFragmentImpl(node) }
  override val fStringFragmentFormatPartConstructor: F
    get() = F { node -> PyFStringFragmentFormatPartImpl(node) }
  override val matchStatementConstructor: F
    get() = F { node -> PyMatchStatementImpl(node) }
  override val caseClauseConstructor: F
    get() = F { node -> PyCaseClauseImpl(node) }
  override val literalPatternConstructor: F
    get() = F { node -> PyLiteralPatternImpl(node) }
  override val valuePatternConstructor: F
    get() = F { node -> PyValuePatternImpl(node) }
  override val capturePatternConstructor: F
    get() = F { node -> PyCapturePatternImpl(node) }
  override val wildcardPatternConstructor: F
    get() = F { node -> PyWildcardPatternImpl(node) }
  override val groupPatternConstructor: F
    get() = F { node -> PyGroupPatternImpl(node) }
  override val sequencePatternConstructor: F
    get() = F { node -> PySequencePatternImpl(node) }
  override val singleStarPatternConstructor: F
    get() = F { node -> PySingleStarPatternImpl(node) }
  override val doubleStarPatternConstructor: F
    get() = F { node -> PyDoubleStarPatternImpl(node) }
  override val mappingPatternConstructor: F
    get() = F { node -> PyMappingPatternImpl(node) }
  override val keyValuePatternConstructor: F
    get() = F { node -> PyKeyValuePatternImpl(node) }
  override val classPatternConstructor: F
    get() = F { node -> PyClassPatternImpl(node) }
  override val patternArgumentListConstructor: F
    get() = F { node -> PyPatternArgumentListImpl(node) }
  override val keywordPatternConstructor: F
    get() = F { node -> PyKeywordPatternImpl(node) }
  override val orPatternConstructor: F
    get() = F { node -> PyOrPatternImpl(node) }
  override val asPatternConstructor: F
    get() = F { node -> PyAsPatternImpl(node) }
}