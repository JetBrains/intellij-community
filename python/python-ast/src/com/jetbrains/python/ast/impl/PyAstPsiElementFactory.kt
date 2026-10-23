package com.jetbrains.python.ast.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.QualifiedName
import com.intellij.util.IncorrectOperationException
import com.jetbrains.python.PyPsiElementFactory
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.ast.PyAstPlainStringElement
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
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
import kotlin.concurrent.Volatile


private typealias F = Function<ASTNode, PsiElement>


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

class PyAstPsiElementFactory : PyPsiElementFactory() {

  override val functionDeclarationConstructor: Function<in ASTNode, out PsiElement> = F {
       object : PyAstBaseElementImpl(it), PyAstFunction {
         override fun getTextOffset(): Int = super<PyAstFunction>.getTextOffset()
         override fun getName(): String? = super<PyAstFunction>.getName()
         override fun setName(name: String): PsiElement = throw IncorrectOperationException()
         override val modifier: PyAstFunction.Modifier? = null
       }
    }
  override val classDeclarationConstructor: Function<in ASTNode, out PsiElement> = F {
      object : PyAstBaseElementImpl(it), PyAstClass {
        override fun getName(): String? = super<PyAstClass>.getName()
        override fun setName(name: String): PsiElement = throw IncorrectOperationException()
      }
    }
  override val parameterListConstructor: Function<in ASTNode, out PsiElement> = F {
      object : PyAstBaseElementImpl(it), PyAstParameterList {}
    }
  override val decoratorListConstructor: Function<in ASTNode, out PsiElement> = F {
      object : PyAstBaseElementImpl(it), PyAstDecoratorList {}
    }
  override val namedParameterConstructor: Function<in ASTNode, out PsiElement> = F {
      object : PyAstBaseElementImpl(it), PyAstNamedParameter {
        override fun getTextOffset(): Int = super<PyAstNamedParameter>.getTextOffset()
        override fun getName(): String? = super<PyAstNamedParameter>.getName()
        override fun setName(name: String): PsiElement = throw IncorrectOperationException()
      }
    }
  override val tupleParameterConstructor: Function<in ASTNode, out PsiElement> = F {
      object : PyAstBaseElementImpl(it), PyAstTupleParameter {
        override fun getContents(): Array<PyAstParameter> = childrenToPsi(PythonDialectsTokenSetProvider.getInstance().parameterTokens,
                                                                          emptyArray())
      }
    }
  override val slashParameterConstructor: Function<in ASTNode, out PsiElement> = F {
      object : PyAstBaseElementImpl(it), PyAstSlashParameter {}
    }
  override val singleStarParameterConstructor: Function<in ASTNode, out PsiElement> = F {
      object : PyAstBaseElementImpl(it), PyAstSingleStarParameter {}
    }
  override val decoratorCallConstructor: Function<in ASTNode, out PsiElement> = F {
      object : PyAstBaseElementImpl(it), PyAstDecorator {
        override fun getName(): String? = super<PyAstDecorator>.getName()
      }
    }
  override val importElementConstructor: Function<in ASTNode, out PsiElement> = F {
      object : PyAstBaseElementImpl(it), PyAstImportElement {
        @Volatile
        private var _importedQName: ValueHolder<QualifiedName?>? = null
        @Volatile
        private var _asName: ValueHolder<String?>? = null
        @Volatile
        private var _visibleName: ValueHolder<String?>? = null

        override fun subtreeChanged() {
          super.subtreeChanged()
          _importedQName = null
          _asName = null
          _visibleName = null
        }

        override fun getImportedQName(): QualifiedName? {
          var result = _importedQName
          if (result == null) {
            result = ValueHolder(super<PyAstImportElement>.importedQName)
            _importedQName = result
          }
          return result.value
        }

        override fun getAsName(): String? {
          var result = _asName
          if (result == null) {
            result = ValueHolder(super<PyAstImportElement>.asName)
            _asName = result
          }
          return result.value
        }

        override fun getVisibleName(): String? {
          var result = _visibleName
          if (result == null) {
            result = ValueHolder(super<PyAstImportElement>.visibleName)
            _visibleName = result
          }
          return result.value
        }

        override fun getName(): String? = super<PyAstImportElement>.getName()
      }
    }
  override val annotationConstructor: Function<in ASTNode, out PsiElement> = F {
      object : PyAstBaseElementImpl(it), PyAstAnnotation {}
    }
  override val starImportElementConstructor: Function<in ASTNode, out PsiElement> = F {
      object : PyAstBaseElementImpl(it), PyAstStarImportElement {}
    }
  override val exceptPartConstructor: Function<in ASTNode, out PsiElement> = F {
      object : PyAstBaseElementImpl(it), PyAstExceptPart {}
    }
  override val fromImportStatementConstructor: Function<in ASTNode, out PsiElement> = F {
      object : PyAstBaseElementImpl(it), PyAstFromImportStatement {
        @Volatile
        private var _importSourceQName: ValueHolder<QualifiedName?>? = null
        override fun subtreeChanged() {
          super.subtreeChanged()
          _importSourceQName = null
        }
        override fun getImportSourceQName(): QualifiedName? {
          var result = _importSourceQName
          if (result == null) {
            result = ValueHolder(super<PyAstFromImportStatement>.importSourceQName)
            _importSourceQName = result
          }
          return result.value
        }
      }
    }

  override val importStatementConstructor: Function<in ASTNode, out PsiElement> = F {
      object : PyAstBaseElementImpl(it), PyAstImportStatement {}
    }
  override val targetExpressionConstructor: Function<in ASTNode, out PsiElement> = F {
      object : PyAstBaseElementImpl(it), PyAstTargetExpression {
        override fun getTextOffset(): Int = super<PyAstTargetExpression>.getTextOffset()
        override fun getName(): String? = super<PyAstTargetExpression>.getName()
        override fun asQualifiedName(): QualifiedName? = PyPsiUtilsCore.asQualifiedName(this)
      }
    }
  override val typeParameterConstructor: Function<in ASTNode, out PsiElement> = F {
      object : PyAstBaseElementImpl(it), PyAstTypeParameter {
        override fun getName(): String? = super<PyAstTypeParameter>.getName()
        override fun setName(name: String): PsiElement = throw IncorrectOperationException()
      }
    }
  override val typeParameterListConstructor: Function<in ASTNode, out PsiElement> = F {
      object : PyAstBaseElementImpl(it), PyAstTypeParameterList {}
    }
  override val typeAliasStatementConstructor: Function<in ASTNode, out PsiElement> = F {
      object : PyAstBaseElementImpl(it), PyAstTypeAliasStatement {
        override fun setName(name: String): PsiElement = throw IncorrectOperationException()
      }
    }
  override val printTargetConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstPrintTarget {} }
  override val decoratorConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstDecorator {
      override fun getName(): String? = super<PyAstDecorator>.getName()
    } }
  override val expressionStatementConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstExpressionStatement {} }
  override val assignmentStatementConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstAssignmentStatement {
      @Volatile
      private var _targets: Array<PyAstExpression>? = null

      override fun getTargets(): Array<PyAstExpression> {
        var result = _targets
        if (result == null) {
          result = PyAstAssignmentStatement.calcTargets(this, false, PyAstExpression.EMPTY_ARRAY)
          _targets = result
        }
        return result
      }

      override fun subtreeChanged() {
        super.subtreeChanged()
        _targets = null
      }
    } }
  override val augAssignmentStatementConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstAugAssignmentStatement {} }
  override val assertStatementConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstAssertStatement {} }
  override val breakStatementConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstBreakStatement {} }
  override val continueStatementConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstContinueStatement {} }
  override val delStatementConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstDelStatement {} }
  override val execStatementConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstExecStatement {} }
  override val forStatementConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstForStatement {} }
  override val typeDeclarationStatementConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstTypeDeclarationStatement {} }
  override val globalStatementConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstGlobalStatement {} }

  override val ifStatementConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstIfStatement {} }
  override val passStatementConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstPassStatement {} }
  override val printStatementConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstPrintStatement {} }
  override val raiseStatementConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstRaiseStatement {} }

  override val returnStatementConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstReturnStatement {} }
  override val tryExceptStatementConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstTryExceptStatement {} }
  override val withStatementConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstWithStatement {} }
  override val whileStatementConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstWhileStatement {} }
  override val nonlocalStatementConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstNonlocalStatement {} }
  override val withItemConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstWithItem {} }
  override val emptyExpressionConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstEmptyExpression {} }
  override val referenceExpressionConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstReferenceExpression {
      override fun getName(): String? = super<PyAstReferenceExpression>.getName()
      override fun asQualifiedName(): QualifiedName? = PyPsiUtilsCore.asQualifiedName(this)
    } }
  override val integerLiteralExpressionConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstNumericLiteralExpression {} }
  override val floatLiteralExpressionConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstNumericLiteralExpression {} }
  override val imaginaryLiteralExpressionConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstNumericLiteralExpression {} }
  override val stringLiteralExpressionConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstStringLiteralExpression {
      @Volatile
      private var stringValue: String? = null
      @Volatile
      private var valueTextRanges: List<TextRange>? = null
      @Volatile
      private var _decodedFragments: List<Pair<TextRange, String>>? = null

      override fun getStringValue(): String {
        var result = stringValue
        if (result == null) {
          result = super.getStringValue()
          stringValue = result
        }
        return result
      }

      override fun getStringValueTextRanges(): List<TextRange> {
        var result: List<TextRange>? = valueTextRanges
        if (result == null) {
          result = super.getStringValueTextRanges()
          valueTextRanges = result
        }
        return result
      }

      override fun getDecodedFragments(): List<Pair<TextRange, String>> {
        var result = _decodedFragments
        if (result == null) {
          result = super.getDecodedFragments()
          _decodedFragments = result
        }
        return result
      }

      override fun subtreeChanged() {
        super.subtreeChanged()
        stringValue = null
        valueTextRanges = null
        _decodedFragments = null
      }
    } }
  override val noneLiteralExpressionConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstNoneLiteralExpression {} }
  override val ellipsisLiteralExpressionConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstEllipsisLiteralExpression {} }
  override val boolLiteralExpressionConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstBoolLiteralExpression {} }
  override val parenthesizedExpressionConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstParenthesizedExpression {} }
  override val subscriptionExpressionConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstSubscriptionExpression {} }
  override val sliceItemConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstSliceItem {} }
  override val binaryExpressionConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstBinaryExpression {} }
  override val prefixExpressionConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstPrefixExpression {} }
  override val callExpressionConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstCallExpression {} }
  override val listLiteralExpressionConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstListLiteralExpression {} }
  override val tupleExpressionConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstTupleExpression<PyAstExpression> {} }
  override val keywordArgumentExpressionConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstKeywordArgument {
      override fun getName(): String? = super<PyAstKeywordArgument>.getName()
      override fun setName(name: String): PsiElement = throw IncorrectOperationException()
    } }
  override val starArgumentExpressionConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstStarArgument {} }
  override val lambdaExpressionConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstLambdaExpression {} }
  override val listCompExpressionConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstListCompExpression {} }
  override val dictLiteralExpressionConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstDictLiteralExpression {} }
  override val keyValueExpressionConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstKeyValueExpression {} }
  override val reprExpressionConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstReprExpression {} }
  override val generatorExpressionConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstGeneratorExpression {} }
  override val conditionalExpressionConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstConditionalExpression {} }
  override val yieldExpressionConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstYieldExpression {} }
  override val setLiteralExpressionConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstSetLiteralExpression {} }
  override val dictCompExpressionConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstDictCompExpression {} }
  override val ifPartIfConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstIfPartIf {} }
  override val ifPartElifConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstIfPartElif {} }
  override val forPartConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstForPart {} }
  override val whilePartConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstWhilePart {} }
  override val tryPartConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstTryPart {} }
  override val finallyPartConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstFinallyPart {} }
  override val setCompExpressionConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstSetCompExpression {} }
  override val argumentListConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstArgumentList {} }
  override val elsePartConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstElsePart {} }
  override val fStringNodeConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstFormattedStringElement {} }
  override val fStringFragmentConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstFStringFragment {} }
  override val fStringFragmentFormatPartConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstFStringFragmentFormatPart {} }
  override val starExpressionConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstStarExpression {} }
  override val doubleStarExpressionConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstDoubleStarExpression {} }
  override val assignmentExpressionConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstAssignmentExpression {} }
  override val matchStatementConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstMatchStatement {} }
  override val caseClauseConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstCaseClause {} }
  override val literalPatternConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstLiteralPattern {} }
  override val orPatternConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstOrPattern {} }
  override val asPatternConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstAsPattern {} }
  override val valuePatternConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstValuePattern {} }
  override val capturePatternConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstCapturePattern {} }
  override val wildcardPatternConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstWildcardPattern {} }
  override val groupPatternConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstGroupPattern {} }
  override val sequencePatternConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstSequencePattern {} }
  override val singleStarPatternConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstSingleStarPattern {} }
  override val mappingPatternConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstMappingPattern {} }
  override val classPatternConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstClassPattern {} }
  override val patternArgumentListConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstPatternArgumentList {} }
  override val keywordPatternConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstKeywordPattern {} }
  override val keyValuePatternConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstKeyValuePattern {} }
  override val doubleStarPatternConstructor: Function<in ASTNode, out PsiElement> = F { object : PyAstBaseElementImpl(it), PyAstDoubleStarPattern {} }

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
