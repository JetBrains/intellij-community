// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.java

import com.intellij.lang.Language
import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.*
import com.intellij.psi.impl.light.LightRecordCanonicalConstructor.LightRecordConstructorParameter
import com.intellij.psi.impl.light.LightRecordField
import com.intellij.psi.impl.source.javadoc.PsiDocMethodOrFieldRef
import com.intellij.psi.impl.source.javadoc.PsiDocParamRef
import com.intellij.psi.impl.source.tree.JavaDocElementType
import com.intellij.psi.impl.source.tree.LazyParseablePsiElement
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl
import com.intellij.psi.javadoc.PsiDocTag
import com.intellij.psi.javadoc.PsiDocTagValue
import com.intellij.psi.javadoc.PsiDocToken
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.containers.map2Array
import org.jetbrains.uast.*
import org.jetbrains.uast.analysis.UastAnalysisPlugin
import org.jetbrains.uast.java.declarations.JavaLazyParentUIdentifier
import org.jetbrains.uast.java.expressions.JavaUAnnotationCallExpression
import org.jetbrains.uast.java.expressions.JavaUModuleReferenceExpression
import org.jetbrains.uast.java.expressions.JavaUNamedExpression
import org.jetbrains.uast.java.expressions.JavaUSynchronizedExpression
import org.jetbrains.uast.util.ClassSet
import org.jetbrains.uast.util.ClassSetsWrapper

class JavaUastLanguagePlugin : UastLanguagePlugin {

  override val priority: Int = 0

  override fun isFileSupported(fileName: String): Boolean = fileName.endsWith(".java", ignoreCase = true)

  override val language: Language
    get() = JavaLanguage.INSTANCE

  override fun isExpressionValueUsed(element: UExpression): Boolean = when (element) {
    is JavaUDeclarationsExpression -> false
    is UnknownJavaExpression -> (element.uastParent as? UExpression)?.let { isExpressionValueUsed(it) } ?: false
    else -> {
      val statement = element.sourcePsi as? PsiStatement
      statement != null && statement.parent !is PsiExpressionStatement
    }
  }

  override fun getMethodCallExpression(
    element: PsiElement,
    containingClassFqName: String?,
    methodName: String
  ): UastLanguagePlugin.ResolvedMethod? {
    if (element !is PsiMethodCallExpression) return null
    if (element.methodExpression.referenceName != methodName) return null

    val callExpression = when (val uElement = convertElementWithParent(element, null)) {
      is UCallExpression -> uElement
      is UQualifiedReferenceExpression -> uElement.selector as UCallExpression
      else -> error("Invalid element type: $uElement")
    }

    val method = callExpression.resolve() ?: return null
    if (containingClassFqName != null) {
      val containingClass = method.containingClass ?: return null
      if (containingClass.qualifiedName != containingClassFqName) return null
    }

    return UastLanguagePlugin.ResolvedMethod(callExpression, method)
  }

  override fun getConstructorCallExpression(
    element: PsiElement,
    fqName: String
  ): UastLanguagePlugin.ResolvedConstructor? {
    if (element !is PsiNewExpression) return null
    val simpleName = fqName.substringAfterLast('.')
    if (element.classReference?.referenceName != simpleName) return null

    val callExpression = convertElementWithParent(element, null) as? UCallExpression ?: return null

    val constructorMethod = element.resolveConstructor() ?: return null
    val containingClass = constructorMethod.containingClass ?: return null
    if (containingClass.qualifiedName != fqName) return null

    return UastLanguagePlugin.ResolvedConstructor(callExpression, constructorMethod, containingClass)
  }

  override fun convertElement(element: PsiElement, parent: UElement?, requiredType: Class<out UElement>?): UElement? {
    return convertElement(element, parent, elementTypes(requiredType))
  }

  override fun convertElementWithParent(element: PsiElement, requiredType: Class<out UElement>?): UElement? {
    if (element is PsiJavaFile) return requiredType.el<UFile> { JavaUFile(element, this) }

    return convertElement(element, null, requiredType)
  }

  fun <T : UElement> convertElement(element: PsiElement, parent: UElement?, requiredTypes: Array<out Class<out T>>): T? {
    val nonEmptyRequiredTypes = requiredTypes.nonEmptyOr(DEFAULT_TYPES_LIST)
    if (!canConvert(element.javaClass, requiredTypes)) return null
    @Suppress("UNCHECKED_CAST")
    return (convertDeclaration(element, parent, nonEmptyRequiredTypes)
            ?: JavaConverter.convertPsiElement(element, parent, nonEmptyRequiredTypes)) as? T
  }

  override fun <T : UElement> convertElementWithParent(element: PsiElement, requiredTypes: Array<out Class<out T>>): T? {
    return convertElement(element, null, requiredTypes)
  }

  @Suppress("UNCHECKED_CAST")
  override fun <T : UElement> convertToAlternatives(element: PsiElement, requiredTypes: Array<out Class<out T>>) = when (element) {
    is PsiMethodCallExpression ->
      JavaConverter.psiMethodCallConversionAlternatives(element,
        null,
        requiredTypes.nonEmptyOr(DEFAULT_EXPRESSION_TYPES_LIST)) as Sequence<T>
    is PsiRecordComponent -> convertRecordConstructorParameterAlternatives(element, null, requiredTypes) as Sequence<T>
    else -> sequenceOf(convertElementWithParent(element, requiredTypes.nonEmptyOr(DEFAULT_TYPES_LIST)) as? T).filterNotNull()
  }

  private fun convertDeclaration(element: PsiElement,
                                 givenParent: UElement?,
                                 requiredType: Array<out Class<out UElement>>): UElement? {
    return with(requiredType) {
      when (element) {
        is PsiJavaFile -> el<UFile,PsiJavaFile>(element, null) { el, _ -> JavaUFile(el, this@JavaUastLanguagePlugin) }
        is UDeclaration -> el<UDeclaration,PsiElement>(element, null) { _, _ -> element }
        is PsiClass -> el<UClass, PsiClass>(element, givenParent, JavaUClass::create)

        is PsiRecordHeader -> el<UMethod, PsiRecordHeader>(element, givenParent, JavaUMethod::create)
        is PsiMethod -> el<UMethod, PsiMethod>(element, givenParent) { el, givenParent ->
          JavaUMethod.create(el, this@JavaUastLanguagePlugin, givenParent)
        }

        is PsiClassInitializer -> el<UClassInitializer,PsiClassInitializer>(element, givenParent, ::JavaUClassInitializer)
        is PsiEnumConstant -> el<UEnumConstant,PsiEnumConstant>(element, givenParent, ::JavaUEnumConstant)
        is PsiLocalVariable -> el<ULocalVariable,PsiLocalVariable>(element, givenParent, ::JavaULocalVariable)
        is PsiRecordComponent, is LightRecordConstructorParameter, is LightRecordField ->
          convertRecordConstructorParameterAlternatives(element, givenParent, requiredType).firstOrNull()
        is PsiParameter -> el<UParameter, PsiParameter>(element, givenParent, ::JavaUParameter)
        is PsiField -> el<UField, PsiField>(element, givenParent, ::JavaUField)
        is PsiVariable -> el<UVariable, PsiVariable>(element, givenParent, ::JavaUVariable)
        is PsiAnnotation -> el<UAnnotation,PsiAnnotation>(element, givenParent, ::JavaUAnnotation)
        else -> null
      }
    }
  }

  override val analysisPlugin: UastAnalysisPlugin?
    get() = UastAnalysisPlugin.byLanguage(JavaLanguage.INSTANCE)

  override fun getPossiblePsiSourceTypes(vararg uastTypes: Class<out UElement>): ClassSet<PsiElement> =
    when (uastTypes.size) {
      0 -> getPossibleSourceTypes(UElement::class.java)
      1 -> getPossibleSourceTypes(uastTypes.single())
      else -> ClassSetsWrapper(uastTypes.map2Array { getPossibleSourceTypes(it) })
    }
}

internal inline fun <reified ActualT : UElement> Class<*>?.el(f: () -> UElement?): UElement? {
  return if (this == null || isAssignableFrom(ActualT::class.java)) f() else null
}


internal inline fun <reified ActualT : UElement, P : PsiElement> Array<out Class<out UElement>>.el(arg1: P,
                                                                                                   arg2: UElement?,
                                                                                                   ctor: (P, UElement?) -> UElement?): UElement? {
  return if (isAssignableFrom(ActualT::class.java)) ctor(arg1, arg2) else null
}

internal inline fun <reified ActualT : UElement, P : PsiElement> Array<out Class<out UElement>>.expr(arg1: P, arg2: UElement?, ctor: (P, UElement?) -> UExpression?): UExpression? {
  return if (isAssignableFrom(ActualT::class.java)) ctor(arg1, arg2) else null
}

internal fun Array<out Class<out UElement>>.isAssignableFrom(cls: Class<*>) = any { it.isAssignableFrom(cls) }

internal object JavaConverter {

  internal tailrec fun unwrapElements(element: PsiElement?): PsiElement? = when (element) {
    is PsiExpressionStatement -> unwrapElements(element.parent)
    is PsiParameterList -> unwrapElements(element.parent)
    is PsiAnnotationParameterList -> unwrapElements(element.parent)
    is PsiModifierList -> unwrapElements(element.parent)
    is PsiExpressionList -> unwrapElements(element.parent)
    is PsiCaseLabelElementList -> unwrapElements(element.parent)
    is PsiPackageStatement -> unwrapElements(element.parent)
    is PsiImportList -> unwrapElements(element.parent)
    is PsiReferenceList -> unwrapElements(element.parent)
    is PsiReferenceParameterList -> unwrapElements(element.parent)
    is PsiBlockStatement -> unwrapElements(element.parent)
    is PsiDocTag -> unwrapElements(element.parent)
    is PsiDocTagValue -> unwrapElements(element.parent)
    is LazyParseablePsiElement ->
      if (element.elementType == JavaDocElementType.DOC_REFERENCE_HOLDER)
        unwrapElements(element.parent)
      else element
    else -> element
  }

  internal fun convertPsiElement(el: PsiElement,
                                 givenParent: UElement?,
                                 requiredType: Array<out Class<out UElement>> = DEFAULT_TYPES_LIST): UElement? {
    return with(requiredType) {
      when (el) {
        is PsiCodeBlock -> el<UBlockExpression, PsiCodeBlock>(el, givenParent, ::JavaUCodeBlockExpression)
        is PsiResourceExpression -> convertExpression(el.expression, givenParent, requiredType)
        is PsiExpression -> convertExpression(el, givenParent, requiredType)
        is PsiStatement -> convertStatement(el, givenParent, requiredType)
        is PsiImportStatementBase -> el<UImportStatement,PsiImportStatementBase>(el, givenParent, ::JavaUImportStatement)
        is PsiIdentifier -> el<UIdentifier, PsiElement>(el, givenParent, ::JavaLazyParentUIdentifier)
        is PsiKeyword -> if (el.text == PsiKeyword.SUPER || el.text == PsiKeyword.THIS)
          el<UIdentifier, PsiElement>(el, givenParent, ::JavaLazyParentUIdentifier)
        else null
        is PsiNameValuePair -> el<UNamedExpression,PsiNameValuePair>(el, givenParent, ::JavaUNamedExpression)
        is PsiArrayInitializerMemberValue -> el<UCallExpression,PsiArrayInitializerMemberValue>(el, givenParent,
                                                                                                ::JavaAnnotationArrayInitializerUCallExpression)
        is PsiTypeElement -> el<UTypeReferenceExpression,PsiTypeElement>(el, givenParent, ::JavaUTypeReferenceExpression)
        is PsiJavaCodeReferenceElement -> convertReference(el, givenParent, requiredType)
        is PsiJavaModuleReferenceElement -> el<UReferenceExpression,PsiJavaModuleReferenceElement>(el, givenParent,
                                                                                                   ::JavaUModuleReferenceExpression)
        is PsiAnnotation -> el.takeIf { PsiTreeUtil.getParentOfType(it, PsiAnnotationMemberValue::class.java, true) != null }?.let {
          el<UExpression,PsiAnnotation>(it, givenParent, ::JavaUAnnotationCallExpression)
        }
        is PsiComment -> el<UComment,PsiComment>(el, givenParent, ::UComment)
        is PsiDocToken ->
          if (el.tokenType == JavaDocTokenType.DOC_TAG_VALUE_TOKEN) {
            val reference = when (val elParent = el.parent) {
              is PsiDocMethodOrFieldRef -> elParent.reference
              is PsiDocParamRef -> elParent.reference
              else -> null
            }
            reference?.let { el<USimpleNameReferenceExpression, PsiElement>(el, givenParent) { el, givenParent ->
              JavaUSimpleNameReferenceExpression(el, el.text, givenParent, it)
            }
            }
          }
          else null

        is PsiCatchSection -> el<UCatchClause,PsiCatchSection>(el, givenParent, ::JavaUCatchClause)
        else -> null
      }
    }
  }

  internal fun convertBlock(block: PsiCodeBlock, parent: UElement?): UBlockExpression = JavaUCodeBlockExpression(block, parent)

  internal fun convertReference(reference: PsiJavaCodeReferenceElement,
                                givenParent: UElement?,
                                requiredType: Array<out Class<out UElement>> = DEFAULT_TYPES_LIST): UExpression? {
    return with(requiredType) {
      if (reference.isQualified) {
        expr<UQualifiedReferenceExpression,PsiJavaCodeReferenceElement>(reference, givenParent, ::JavaUQualifiedReferenceExpression)
      }
      else {
        val name = reference.referenceName ?: "<error name>"
        expr<USimpleNameReferenceExpression, PsiElement>(reference, givenParent) { reference, givenParent -> JavaUSimpleNameReferenceExpression(reference, name, givenParent, reference as PsiReference) }
      }
    }
  }

  internal fun convertExpression(el: PsiExpression,
                                 givenParent: UElement?,
                                 requiredType: Array<out Class<out UElement>> = DEFAULT_EXPRESSION_TYPES_LIST): UExpression? {
    return with(requiredType) {
      when (el) {
        is PsiAssignmentExpression -> expr<UBinaryExpression, PsiAssignmentExpression>(el, givenParent, ::JavaUAssignmentExpression)
        is PsiConditionalExpression -> expr<UIfExpression, PsiConditionalExpression>(el, givenParent, ::JavaUTernaryIfExpression)
        is PsiNewExpression -> {
          if (el.anonymousClass != null)
            expr<UObjectLiteralExpression,PsiNewExpression>(el, givenParent, ::JavaUObjectLiteralExpression)
          else
            expr<UCallExpression,PsiNewExpression>(el, givenParent, ::JavaConstructorUCallExpression)
        }
        is PsiMethodCallExpression -> psiMethodCallConversionAlternatives(el, givenParent, requiredType).firstOrNull()
        is PsiArrayInitializerExpression -> expr<UCallExpression,PsiArrayInitializerExpression>(el, givenParent, ::JavaArrayInitializerUCallExpression)
        is PsiBinaryExpression -> expr<UBinaryExpression,PsiBinaryExpression>(el, givenParent, ::JavaUBinaryExpression)
        // Should go after PsiBinaryExpression since it implements PsiPolyadicExpression
        is PsiPolyadicExpression -> expr<UPolyadicExpression,PsiPolyadicExpression>(el, givenParent, ::JavaUPolyadicExpression)
        is PsiParenthesizedExpression -> expr<UParenthesizedExpression,PsiParenthesizedExpression>(el, givenParent, ::JavaUParenthesizedExpression)
        is PsiPrefixExpression -> expr<UPrefixExpression,PsiPrefixExpression>(el, givenParent, ::JavaUPrefixExpression)
        is PsiPostfixExpression -> expr<UPostfixExpression,PsiPostfixExpression>(el, givenParent, ::JavaUPostfixExpression)
        is PsiLiteralExpressionImpl -> expr<JavaULiteralExpression,PsiLiteralExpressionImpl>(el, givenParent, ::JavaULiteralExpression)
        is PsiMethodReferenceExpression -> expr<UCallableReferenceExpression,PsiMethodReferenceExpression>(el, givenParent, ::JavaUCallableReferenceExpression)
        is PsiReferenceExpression -> convertReference(el, givenParent, requiredType)
        is PsiThisExpression -> expr<UThisExpression,PsiThisExpression>(el, givenParent, ::JavaUThisExpression)
        is PsiSuperExpression -> expr<USuperExpression,PsiSuperExpression>(el, givenParent, ::JavaUSuperExpression)
        is PsiInstanceOfExpression -> expr<UBinaryExpressionWithType,PsiInstanceOfExpression>(el, givenParent, ::JavaUInstanceCheckExpression)
        is PsiTypeCastExpression -> expr<UBinaryExpressionWithType,PsiTypeCastExpression>(el, givenParent, ::JavaUTypeCastExpression)
        is PsiClassObjectAccessExpression -> expr<UClassLiteralExpression,PsiClassObjectAccessExpression>(el, givenParent, ::JavaUClassLiteralExpression)
        is PsiArrayAccessExpression -> expr<UArrayAccessExpression,PsiArrayAccessExpression>(el, givenParent, ::JavaUArrayAccessExpression)
        is PsiLambdaExpression -> expr<ULambdaExpression,PsiLambdaExpression>(el, givenParent, ::JavaULambdaExpression)
        is PsiSwitchExpression -> expr<USwitchExpression,PsiSwitchExpression>(el, givenParent, ::JavaUSwitchExpression)
        else -> expr<UExpression,PsiElement>(el, givenParent, ::UnknownJavaExpression)
      }
    }
  }

  internal fun psiMethodCallConversionAlternatives(element: PsiMethodCallExpression,
                                                   givenParent: UElement?,
                                                   requiredTypes: Array<out Class<out UElement>>): Sequence<UExpression> {
    if (element.methodExpression.qualifierExpression == null) {
      return sequenceOf(requiredTypes.expr<UCallExpression,PsiMethodCallExpression>(element, givenParent, ::JavaUCallExpression)).filterNotNull()
    }

    if (!requiredTypes.isAssignableFrom(UQualifiedReferenceExpression::class.java) &&
        !requiredTypes.isAssignableFrom(UCallExpression::class.java)) return emptySequence()


    val expr = JavaUCompositeQualifiedExpression(element, givenParent).apply {
      receiverInitializer = {
        convertOrEmpty(element.methodExpression.qualifierExpression!!, this@apply)
      }
      selector = JavaUCallExpression(element, this@apply)
    }

    val results = sequenceOf(expr, expr.selector)
    return requiredTypes.asSequence().flatMap { required -> results.filter { required.isInstance(it) } }.distinct()
  }

  internal fun convertStatement(el: PsiStatement,
                                givenParent: UElement?,
                                requiredType: Array<out Class<out UElement>> = DEFAULT_EXPRESSION_TYPES_LIST): UExpression? {
    return with(requiredType) {
      when (el) {
        is PsiDeclarationStatement -> expr<UDeclarationsExpression,PsiDeclarationStatement>(el, givenParent) { el, givenParent ->
          convertDeclarations(el.declaredElements, givenParent ?: unwrapElements(el.parent).toUElement())
        }
        is PsiExpressionListStatement -> expr<UDeclarationsExpression,PsiExpressionListStatement>(el, givenParent) { el, givenParent ->
          convertDeclarations(el.expressionList.expressions, givenParent ?: unwrapElements(el.parent).toUElement())
        }
        is PsiBlockStatement -> expr<UBlockExpression,PsiBlockStatement>(el, givenParent, ::JavaUBlockExpression)
        is PsiLabeledStatement -> expr<ULabeledExpression,PsiLabeledStatement>(el, givenParent, ::JavaULabeledExpression)
        is PsiExpressionStatement -> convertExpression(el.expression, givenParent, requiredType)
        is PsiIfStatement -> expr<UIfExpression,PsiIfStatement>(el, givenParent, ::JavaUIfExpression)
        is PsiSwitchStatement -> expr<USwitchExpression,PsiSwitchStatement>(el, givenParent, ::JavaUSwitchExpression)
        is PsiWhileStatement -> expr<UWhileExpression,PsiWhileStatement>(el, givenParent, ::JavaUWhileExpression)
        is PsiDoWhileStatement -> expr<UDoWhileExpression,PsiDoWhileStatement>(el, givenParent, ::JavaUDoWhileExpression)
        is PsiForStatement -> expr<UForExpression,PsiForStatement>(el, givenParent, ::JavaUForExpression)
        is PsiForeachStatement -> expr<UForEachExpression,PsiForeachStatement>(el, givenParent, ::JavaUForEachExpression)
        is PsiBreakStatement -> expr<UBreakExpression,PsiBreakStatement>(el, givenParent, ::JavaUBreakExpression)
        is PsiYieldStatement -> expr<UYieldExpression,PsiYieldStatement>(el, givenParent, ::JavaUYieldExpression)
        is PsiContinueStatement -> expr<UContinueExpression,PsiContinueStatement>(el, givenParent, ::JavaUContinueExpression)
        is PsiReturnStatement -> expr<UReturnExpression,PsiReturnStatement>(el, givenParent, ::JavaUReturnExpression)
        is PsiAssertStatement -> expr<UCallExpression,PsiAssertStatement>(el, givenParent, ::JavaUAssertExpression)
        is PsiThrowStatement -> expr<UThrowExpression,PsiThrowStatement>(el, givenParent, ::JavaUThrowExpression)
        is PsiSynchronizedStatement -> expr<UBlockExpression,PsiSynchronizedStatement>(el, givenParent, ::JavaUSynchronizedExpression)
        is PsiTryStatement -> expr<UTryExpression,PsiTryStatement>(el, givenParent, ::JavaUTryExpression)
        is PsiEmptyStatement -> expr<UExpression,PsiEmptyStatement>(el,givenParent) { el,_->UastEmptyExpression(el.parent?.toUElement()) }
        is PsiSwitchLabelStatementBase -> expr<UExpression,PsiSwitchLabelStatementBase>(el, givenParent) { el, givenParent ->
          when (givenParent) {
            is JavaUSwitchEntryList -> givenParent.findUSwitchEntryForLabel(el)
            null -> PsiTreeUtil.getParentOfType(el, PsiSwitchBlock::class.java)?.let {
              JavaUSwitchExpression(it, null).body.findUSwitchEntryForLabel(el)
            }
            else -> null
          }
        }
        else -> expr<UExpression, PsiElement>(el, givenParent, ::UnknownJavaExpression)
      }
    }
  }

  private fun convertDeclarations(elements: Array<out PsiElement>, parent: UElement?): UDeclarationsExpression {
    return JavaUDeclarationsExpression(parent).apply {
      val declarations = mutableListOf<UDeclaration>()
      for (element in elements) {
        if (element is PsiVariable) {
          declarations += JavaUVariable.create(element, this)
        }
        else if (element is PsiClass) {
          declarations += JavaUClass.create(element, this)
        }
      }
      this.declarations = declarations
    }
  }

  internal fun convertOrEmpty(statement: PsiStatement?, parent: UElement?): UExpression =
    statement?.let { convertStatement(it, parent) } ?: UastEmptyExpression(parent)

  internal fun convertOrEmpty(expression: PsiExpression?, parent: UElement?): UExpression =
    expression?.let { convertExpression(it, parent) } ?: UastEmptyExpression(parent)

  internal fun convertOrNull(expression: PsiExpression?, parent: UElement?): UExpression? =
    if (expression != null) convertExpression(expression, parent) else null

  internal fun convertOrEmpty(block: PsiCodeBlock?, parent: UElement?): UExpression =
    if (block != null) convertBlock(block, parent) else UastEmptyExpression(parent)
}

private fun elementTypes(requiredType: Class<out UElement>?): Array<Class<out UElement>> = requiredType?.let { arrayOf(it) } ?: DEFAULT_TYPES_LIST

private fun <T : UElement> Array<out Class<out T>>.nonEmptyOr(default: Array<out Class<out T>>): Array<out Class<out T>> = takeIf { it.isNotEmpty() } ?: default