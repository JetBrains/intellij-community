// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.ast.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.TextRange
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.QualifiedName
import com.intellij.util.IncorrectOperationException
import com.jetbrains.python.PyPsiElementFactory
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.ast.PyAstAnnotation
import com.jetbrains.python.ast.PyAstArgumentList
import com.jetbrains.python.ast.PyAstAsPattern
import com.jetbrains.python.ast.PyAstAssertStatement
import com.jetbrains.python.ast.PyAstAssignmentExpression
import com.jetbrains.python.ast.PyAstAssignmentStatement
import com.jetbrains.python.ast.PyAstAugAssignmentStatement
import com.jetbrains.python.ast.PyAstBinaryExpression
import com.jetbrains.python.ast.PyAstBoolLiteralExpression
import com.jetbrains.python.ast.PyAstBreakStatement
import com.jetbrains.python.ast.PyAstCallExpression
import com.jetbrains.python.ast.PyAstCapturePattern
import com.jetbrains.python.ast.PyAstCaseClause
import com.jetbrains.python.ast.PyAstClass
import com.jetbrains.python.ast.PyAstClassPattern
import com.jetbrains.python.ast.PyAstConditionalExpression
import com.jetbrains.python.ast.PyAstContinueStatement
import com.jetbrains.python.ast.PyAstDecorator
import com.jetbrains.python.ast.PyAstDecoratorList
import com.jetbrains.python.ast.PyAstDelStatement
import com.jetbrains.python.ast.PyAstDictCompExpression
import com.jetbrains.python.ast.PyAstDictLiteralExpression
import com.jetbrains.python.ast.PyAstDoubleStarExpression
import com.jetbrains.python.ast.PyAstDoubleStarPattern
import com.jetbrains.python.ast.PyAstElement
import com.jetbrains.python.ast.PyAstElementVisitor
import com.jetbrains.python.ast.PyAstEllipsisLiteralExpression
import com.jetbrains.python.ast.PyAstElsePart
import com.jetbrains.python.ast.PyAstEmptyExpression
import com.jetbrains.python.ast.PyAstExceptPart
import com.jetbrains.python.ast.PyAstExecStatement
import com.jetbrains.python.ast.PyAstExpression
import com.jetbrains.python.ast.PyAstExpressionStatement
import com.jetbrains.python.ast.PyAstFStringFragment
import com.jetbrains.python.ast.PyAstFStringFragmentFormatPart
import com.jetbrains.python.ast.PyAstFinallyPart
import com.jetbrains.python.ast.PyAstForPart
import com.jetbrains.python.ast.PyAstForStatement
import com.jetbrains.python.ast.PyAstFormattedStringElement
import com.jetbrains.python.ast.PyAstFromImportStatement
import com.jetbrains.python.ast.PyAstFunction
import com.jetbrains.python.ast.PyAstGeneratorExpression
import com.jetbrains.python.ast.PyAstGlobalStatement
import com.jetbrains.python.ast.PyAstGroupPattern
import com.jetbrains.python.ast.PyAstIfPartElif
import com.jetbrains.python.ast.PyAstIfPartIf
import com.jetbrains.python.ast.PyAstIfStatement
import com.jetbrains.python.ast.PyAstImportElement
import com.jetbrains.python.ast.PyAstImportStatement
import com.jetbrains.python.ast.PyAstKeyValueExpression
import com.jetbrains.python.ast.PyAstKeyValuePattern
import com.jetbrains.python.ast.PyAstKeywordArgument
import com.jetbrains.python.ast.PyAstKeywordPattern
import com.jetbrains.python.ast.PyAstLambdaExpression
import com.jetbrains.python.ast.PyAstListCompExpression
import com.jetbrains.python.ast.PyAstListLiteralExpression
import com.jetbrains.python.ast.PyAstLiteralPattern
import com.jetbrains.python.ast.PyAstMappingPattern
import com.jetbrains.python.ast.PyAstMatchStatement
import com.jetbrains.python.ast.PyAstNamedParameter
import com.jetbrains.python.ast.PyAstNoneLiteralExpression
import com.jetbrains.python.ast.PyAstNonlocalStatement
import com.jetbrains.python.ast.PyAstNumericLiteralExpression
import com.jetbrains.python.ast.PyAstOrPattern
import com.jetbrains.python.ast.PyAstParameterList
import com.jetbrains.python.ast.PyAstParenthesizedExpression
import com.jetbrains.python.ast.PyAstPassStatement
import com.jetbrains.python.ast.PyAstPatternArgumentList
import com.jetbrains.python.ast.PyAstPlainStringElement
import com.jetbrains.python.ast.PyAstPrefixExpression
import com.jetbrains.python.ast.PyAstPrintStatement
import com.jetbrains.python.ast.PyAstPrintTarget
import com.jetbrains.python.ast.PyAstRaiseStatement
import com.jetbrains.python.ast.PyAstReferenceExpression
import com.jetbrains.python.ast.PyAstReprExpression
import com.jetbrains.python.ast.PyAstReturnStatement
import com.jetbrains.python.ast.PyAstSequencePattern
import com.jetbrains.python.ast.PyAstSetCompExpression
import com.jetbrains.python.ast.PyAstSetLiteralExpression
import com.jetbrains.python.ast.PyAstSingleStarParameter
import com.jetbrains.python.ast.PyAstSingleStarPattern
import com.jetbrains.python.ast.PyAstSlashParameter
import com.jetbrains.python.ast.PyAstSliceItem
import com.jetbrains.python.ast.PyAstStarArgument
import com.jetbrains.python.ast.PyAstStarExpression
import com.jetbrains.python.ast.PyAstStarImportElement
import com.jetbrains.python.ast.PyAstStringLiteralExpression
import com.jetbrains.python.ast.PyAstSubscriptionExpression
import com.jetbrains.python.ast.PyAstTargetExpression
import com.jetbrains.python.ast.PyAstTryExceptStatement
import com.jetbrains.python.ast.PyAstTryPart
import com.jetbrains.python.ast.PyAstTupleExpression
import com.jetbrains.python.ast.PyAstTupleParameter
import com.jetbrains.python.ast.PyAstTypeAliasStatement
import com.jetbrains.python.ast.PyAstTypeDeclarationStatement
import com.jetbrains.python.ast.PyAstTypeParameter
import com.jetbrains.python.ast.PyAstTypeParameterList
import com.jetbrains.python.ast.PyAstValuePattern
import com.jetbrains.python.ast.PyAstWhilePart
import com.jetbrains.python.ast.PyAstWhileStatement
import com.jetbrains.python.ast.PyAstWildcardPattern
import com.jetbrains.python.ast.PyAstWithItem
import com.jetbrains.python.ast.PyAstWithStatement
import com.jetbrains.python.ast.PyAstYieldExpression
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.pyi.PyiFileType
import com.jetbrains.python.pyi.PyiLanguageDialect
import java.util.function.Function

private typealias F = Function<in ASTNode, out PsiElement>

/** The base of the AST-only elements. It routes a [PyAstElementVisitor] to the element's own `visit` method. */
private abstract class PyAstBaseElementImpl(node: ASTNode) : ASTWrapperPsiElement(node), PyAstElement {
  override fun accept(visitor: PsiElementVisitor) {
    if (visitor is PyAstElementVisitor) {
      acceptPyVisitor(visitor)
    }
    else {
      super.accept(visitor)
    }
  }
}

/**
 * The AST-only Python PSI: each element is the `PyAst*` interface on an [ASTWrapperPsiElement], without a stub or resolve.
 * It is the open default of the [PyPsiElementFactory] service, so a process without `intellij.python.psi.impl` uses it:
 * the JetBrains Client frontend and JetBrains Light before the smart mode.
 */
class PyAstPsiElementFactory : PyPsiElementFactory() {

  // Elements with a stub in the stub-based PSI
  override val functionDeclarationConstructor: F = F { node ->
    object : PyAstBaseElementImpl(node), PyAstFunction {
      override fun getTextOffset(): Int = super<PyAstFunction>.getTextOffset()
      override fun getName(): String? = super<PyAstFunction>.getName()
      override fun setName(name: String): PsiElement = throw IncorrectOperationException()
      override val modifier: PyAstFunction.Modifier? = null

      @Volatile
      private var _isGenerator: Boolean? = null

      override val isGenerator: Boolean
        get() = _isGenerator ?: super<PyAstFunction>.isGenerator.also { _isGenerator = it }

      override fun subtreeChanged() {
        super.subtreeChanged()
        _isGenerator = null
      }
    }
  }
  override val classDeclarationConstructor: F = F { node ->
    object : PyAstBaseElementImpl(node), PyAstClass {
      override fun getName(): String? = super<PyAstClass>.getName()
      override fun setName(name: String): PsiElement = throw IncorrectOperationException()
    }
  }
  override val parameterListConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstParameterList {} }
  override val decoratorListConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstDecoratorList {} }
  override val namedParameterConstructor: F = F { node ->
    object : PyAstBaseElementImpl(node), PyAstNamedParameter {
      override fun getTextOffset(): Int = super<PyAstNamedParameter>.getTextOffset()
      override fun getName(): String? = super<PyAstNamedParameter>.getName()
      override fun setName(name: String): PsiElement = throw IncorrectOperationException()
    }
  }
  override val tupleParameterConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstTupleParameter {} }
  override val slashParameterConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstSlashParameter {} }
  override val singleStarParameterConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstSingleStarParameter {} }
  override val decoratorCallConstructor: F = F { node ->
    object : PyAstBaseElementImpl(node), PyAstDecorator {
      override fun getName(): String? = super<PyAstDecorator>.getName()
    }
  }
  override val importElementConstructor: F = F { node ->
    object : PyAstBaseElementImpl(node), PyAstImportElement {
      @Volatile
      private var _importedQName: ValueHolder<QualifiedName?>? = null
      @Volatile
      private var _asName: ValueHolder<String?>? = null
      @Volatile
      private var _visibleName: ValueHolder<String?>? = null

      override fun getImportedQName(): QualifiedName? =
        (_importedQName ?: ValueHolder(super<PyAstImportElement>.importedQName).also { _importedQName = it }).value

      override fun getAsName(): String? =
        (_asName ?: ValueHolder(super<PyAstImportElement>.asName).also { _asName = it }).value

      override fun getVisibleName(): String? =
        (_visibleName ?: ValueHolder(super<PyAstImportElement>.visibleName).also { _visibleName = it }).value

      override fun getName(): String? = super<PyAstImportElement>.getName()

      override fun subtreeChanged() {
        super.subtreeChanged()
        _importedQName = null
        _asName = null
        _visibleName = null
      }
    }
  }
  override val annotationConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstAnnotation {} }
  override val starImportElementConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstStarImportElement {} }
  override val exceptPartConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstExceptPart {} }
  override val fromImportStatementConstructor: F = F { node ->
    object : PyAstBaseElementImpl(node), PyAstFromImportStatement {
      @Volatile
      private var _importSourceQName: ValueHolder<QualifiedName?>? = null

      override fun getImportSourceQName(): QualifiedName? =
        (_importSourceQName ?: ValueHolder(super<PyAstFromImportStatement>.importSourceQName).also { _importSourceQName = it }).value

      override fun subtreeChanged() {
        super.subtreeChanged()
        _importSourceQName = null
      }
    }
  }
  override val importStatementConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstImportStatement {} }
  override val targetExpressionConstructor: F = F { node ->
    object : PyAstBaseElementImpl(node), PyAstTargetExpression {
      override fun getTextOffset(): Int = super<PyAstTargetExpression>.getTextOffset()
      override fun getName(): String? = super<PyAstTargetExpression>.getName()
      override fun asQualifiedName(): QualifiedName? = PyPsiUtilsCore.asQualifiedName(this)
    }
  }
  override val typeParameterConstructor: F = F { node ->
    object : PyAstBaseElementImpl(node), PyAstTypeParameter {
      override fun getName(): String? = super<PyAstTypeParameter>.getName()
      override fun setName(name: String): PsiElement = throw IncorrectOperationException()
    }
  }
  override val typeParameterListConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstTypeParameterList {} }
  override val typeAliasStatementConstructor: F = F { node ->
    object : PyAstBaseElementImpl(node), PyAstTypeAliasStatement {
      override fun setName(name: String): PsiElement = throw IncorrectOperationException()
    }
  }

  // Elements without a stub
  override val argumentListConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstArgumentList {} }
  override val printTargetConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstPrintTarget {} }
  override val decoratorConstructor: F = decoratorCallConstructor
  override val expressionStatementConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstExpressionStatement {} }
  override val assignmentStatementConstructor: F = F { node ->
    object : PyAstBaseElementImpl(node), PyAstAssignmentStatement {
      @Volatile
      private var _targets: Array<PyAstExpression>? = null

      override fun getTargets(): Array<PyAstExpression> =
        _targets ?: PyAstAssignmentStatement.calcTargets(this, false, PyAstExpression.EMPTY_ARRAY).also { _targets = it }

      override fun subtreeChanged() {
        super.subtreeChanged()
        _targets = null
      }
    }
  }
  override val augAssignmentStatementConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstAugAssignmentStatement {} }
  override val assertStatementConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstAssertStatement {} }
  override val breakStatementConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstBreakStatement {} }
  override val continueStatementConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstContinueStatement {} }
  override val delStatementConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstDelStatement {} }
  override val execStatementConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstExecStatement {} }
  override val forStatementConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstForStatement {} }
  override val typeDeclarationStatementConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstTypeDeclarationStatement {} }
  override val globalStatementConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstGlobalStatement {} }
  override val ifStatementConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstIfStatement {} }
  override val passStatementConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstPassStatement {} }
  override val printStatementConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstPrintStatement {} }
  override val raiseStatementConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstRaiseStatement {} }
  override val returnStatementConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstReturnStatement {} }
  override val tryExceptStatementConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstTryExceptStatement {} }
  override val withStatementConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstWithStatement {} }
  override val whileStatementConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstWhileStatement {} }
  override val nonlocalStatementConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstNonlocalStatement {} }
  override val withItemConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstWithItem {} }
  override val emptyExpressionConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstEmptyExpression {} }
  override val referenceExpressionConstructor: F = F { node ->
    object : PyAstBaseElementImpl(node), PyAstReferenceExpression {
      override fun getName(): String? = super<PyAstReferenceExpression>.getName()
      override fun asQualifiedName(): QualifiedName? = PyPsiUtilsCore.asQualifiedName(this)
    }
  }
  override val integerLiteralExpressionConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstNumericLiteralExpression {} }
  override val floatLiteralExpressionConstructor: F = integerLiteralExpressionConstructor
  override val imaginaryLiteralExpressionConstructor: F = integerLiteralExpressionConstructor
  override val stringLiteralExpressionConstructor: F = F { node ->
    object : PyAstBaseElementImpl(node), PyAstStringLiteralExpression {
      @Volatile
      private var _stringValue: String? = null
      @Volatile
      private var _stringValueTextRanges: List<TextRange>? = null
      @Volatile
      private var _decodedFragments: List<Pair<TextRange, String>>? = null

      override fun getStringValue(): String =
        _stringValue ?: super.getStringValue().also { _stringValue = it }

      override fun getStringValueTextRanges(): List<TextRange> =
        _stringValueTextRanges ?: super.getStringValueTextRanges().also { _stringValueTextRanges = it }

      override fun getDecodedFragments(): List<Pair<TextRange, String>> =
        _decodedFragments ?: super.getDecodedFragments().also { _decodedFragments = it }

      override fun subtreeChanged() {
        super.subtreeChanged()
        _stringValue = null
        _stringValueTextRanges = null
        _decodedFragments = null
      }
    }
  }
  override val noneLiteralExpressionConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstNoneLiteralExpression {} }
  override val ellipsisLiteralExpressionConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstEllipsisLiteralExpression {} }
  override val boolLiteralExpressionConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstBoolLiteralExpression {} }
  override val parenthesizedExpressionConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstParenthesizedExpression {} }
  override val subscriptionExpressionConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstSubscriptionExpression {} }
  override val sliceItemConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstSliceItem {} }
  override val binaryExpressionConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstBinaryExpression {} }
  override val prefixExpressionConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstPrefixExpression {} }
  override val callExpressionConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstCallExpression {} }
  override val listLiteralExpressionConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstListLiteralExpression {} }
  override val tupleExpressionConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstTupleExpression<PyAstExpression> {} }
  override val keywordArgumentExpressionConstructor: F = F { node ->
    object : PyAstBaseElementImpl(node), PyAstKeywordArgument {
      override fun getName(): String? = super<PyAstKeywordArgument>.getName()
      override fun setName(name: String): PsiElement = throw IncorrectOperationException()
    }
  }
  override val starArgumentExpressionConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstStarArgument {} }
  override val lambdaExpressionConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstLambdaExpression {} }
  override val listCompExpressionConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstListCompExpression {} }
  override val dictLiteralExpressionConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstDictLiteralExpression {} }
  override val keyValueExpressionConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstKeyValueExpression {} }
  override val reprExpressionConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstReprExpression {} }
  override val generatorExpressionConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstGeneratorExpression {} }
  override val conditionalExpressionConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstConditionalExpression {} }
  override val yieldExpressionConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstYieldExpression {} }
  override val starExpressionConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstStarExpression {} }
  override val doubleStarExpressionConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstDoubleStarExpression {} }
  override val assignmentExpressionConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstAssignmentExpression {} }
  override val setLiteralExpressionConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstSetLiteralExpression {} }
  override val setCompExpressionConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstSetCompExpression {} }
  override val dictCompExpressionConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstDictCompExpression {} }
  override val ifPartIfConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstIfPartIf {} }
  override val ifPartElifConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstIfPartElif {} }
  override val forPartConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstForPart {} }
  override val whilePartConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstWhilePart {} }
  override val tryPartConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstTryPart {} }
  override val finallyPartConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstFinallyPart {} }
  override val elsePartConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstElsePart {} }
  override val fStringNodeConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstFormattedStringElement {} }
  override val fStringFragmentConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstFStringFragment {} }
  override val fStringFragmentFormatPartConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstFStringFragmentFormatPart {} }
  override val matchStatementConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstMatchStatement {} }
  override val caseClauseConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstCaseClause {} }
  override val literalPatternConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstLiteralPattern {} }
  override val valuePatternConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstValuePattern {} }
  override val capturePatternConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstCapturePattern {} }
  override val wildcardPatternConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstWildcardPattern {} }
  override val groupPatternConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstGroupPattern {} }
  override val sequencePatternConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstSequencePattern {} }
  override val singleStarPatternConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstSingleStarPattern {} }
  override val doubleStarPatternConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstDoubleStarPattern {} }
  override val mappingPatternConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstMappingPattern {} }
  override val keyValuePatternConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstKeyValuePattern {} }
  override val classPatternConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstClassPattern {} }
  override val patternArgumentListConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstPatternArgumentList {} }
  override val keywordPatternConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstKeywordPattern {} }
  override val orPatternConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstOrPattern {} }
  override val asPatternConstructor: F = F { object : PyAstBaseElementImpl(it), PyAstAsPattern {} }

  override fun createPythonFile(viewProvider: FileViewProvider): PsiFile =
    PyAstFileImpl(viewProvider, PythonLanguage.INSTANCE, PythonFileType.INSTANCE)

  override fun createPyiFile(viewProvider: FileViewProvider): PsiFile =
    object : PyAstFileImpl(viewProvider, PyiLanguageDialect.getInstance(), PyiFileType.INSTANCE) {
      override fun getLanguageLevel(): LanguageLevel = LanguageLevel.getLatest()
    }

  override fun createStatementListNode(type: IElementType, text: CharSequence?): ASTNode = PyAstStatementListImpl(type, text)

  override fun createStringLeaf(type: IElementType, text: CharSequence): LeafElement =
    object : LeafPsiElement(type, text), PyAstPlainStringElement {}
}
