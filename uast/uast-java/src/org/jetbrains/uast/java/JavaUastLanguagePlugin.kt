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

  @Suppress("UNCHECKED_CAST")
  fun <T : UElement> convertElement(element: PsiElement, parent: UElement?, requiredTypes: Array<out Class<out T>>): T? {
    val nonEmptyRequiredTypes = requiredTypes.nonEmptyOr(DEFAULT_TYPES_LIST)
    if (!canConvert(element.javaClass, requiredTypes)) return null
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
    fun <P : PsiElement> build(ctor: (P, UElement?) -> UElement): () -> UElement? {
      @Suppress("UNCHECKED_CAST")
      return fun(): UElement? {
        return ctor(element as P, givenParent)
      }
    }

    return with(requiredType) {
      when (element) {
        is PsiJavaFile -> el<UFile> { JavaUFile(element, this@JavaUastLanguagePlugin) }
        is UDeclaration -> el<UDeclaration> { element }
        is PsiClass -> el<UClass> {
          JavaUClass.create(element, givenParent)
        }
        is PsiRecordHeader -> el<UMethod> { JavaUMethod.create(element, givenParent) }
        is PsiMethod -> el<UMethod> {
          JavaUMethod.create(element, this@JavaUastLanguagePlugin, givenParent)
        }
        is PsiClassInitializer -> el<UClassInitializer>(build(::JavaUClassInitializer))
        is PsiEnumConstant -> el<UEnumConstant>(build(::JavaUEnumConstant))
        is PsiLocalVariable -> el<ULocalVariable>(build(::JavaULocalVariable))
        is PsiRecordComponent, is LightRecordConstructorParameter, is LightRecordField ->
          convertRecordConstructorParameterAlternatives(element, givenParent, requiredType).firstOrNull()
        is PsiParameter -> el<UParameter>(build(::JavaUParameter))
        is PsiField -> el<UField>(build(::JavaUField))
        is PsiVariable -> el<UVariable>(build(::JavaUVariable))
        is PsiAnnotation -> el<UAnnotation>(build(::JavaUAnnotation))
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
      else -> ClassSetsWrapper<PsiElement>(uastTypes.map2Array { getPossibleSourceTypes(it) })
    }
}

internal inline fun <reified ActualT : UElement> Class<*>?.el(f: () -> UElement?): UElement? {
  return if (this == null || isAssignableFrom(ActualT::class.java)) f() else null
}

internal inline fun <reified ActualT : UElement> Array<out Class<out UElement>>.el(f: () -> UElement?): UElement? {
  return if (isAssignableFrom(ActualT::class.java)) f() else null
}

internal inline fun <reified ActualT : UElement> Array<out Class<out UElement>>.expr(f: () -> UExpression?): UExpression? {
  return if (isAssignableFrom(ActualT::class.java)) f() else null
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

    fun <P : PsiElement> build(ctor: (P, UElement?) -> UElement): () -> UElement? {
      @Suppress("UNCHECKED_CAST")
      return fun(): UElement? {
        return ctor(el as P, givenParent)
      }
    }

    return with(requiredType) {
      when (el) {
        is PsiCodeBlock -> el<UBlockExpression>(build(::JavaUCodeBlockExpression))
        is PsiResourceExpression -> convertExpression(el.expression, givenParent, requiredType)
        is PsiExpression -> convertExpression(el, givenParent, requiredType)
        is PsiStatement -> convertStatement(el, givenParent, requiredType)
        is PsiImportStatementBase -> el<UImportStatement>(build(::JavaUImportStatement))
        is PsiIdentifier -> el<UIdentifier> { JavaLazyParentUIdentifier(el, givenParent) }
        is PsiKeyword -> if (el.text == PsiKeyword.SUPER || el.text == PsiKeyword.THIS)
          el<UIdentifier> { JavaLazyParentUIdentifier(el, givenParent) }
        else null
        is PsiNameValuePair -> el<UNamedExpression>(build(::JavaUNamedExpression))
        is PsiArrayInitializerMemberValue -> el<UCallExpression>(build(::JavaAnnotationArrayInitializerUCallExpression))
        is PsiTypeElement -> el<UTypeReferenceExpression>(build(::JavaUTypeReferenceExpression))
        is PsiJavaCodeReferenceElement -> convertReference(el, givenParent, requiredType)
        is PsiJavaModuleReferenceElement -> el<UReferenceExpression>(build(::JavaUModuleReferenceExpression))
        is PsiAnnotation -> el.takeIf { PsiTreeUtil.getParentOfType(it, PsiAnnotationMemberValue::class.java, true) != null }?.let {
          el<UExpression> { JavaUAnnotationCallExpression(it, givenParent) }
        }
        is PsiComment -> el<UComment>(build(::UComment))
        is PsiDocToken -> el<USimpleNameReferenceExpression> {
          el.takeIf { it.tokenType == JavaDocTokenType.DOC_TAG_VALUE_TOKEN }?.let {
            val reference = when (val elParent = el.parent) {
              is PsiDocMethodOrFieldRef -> elParent.reference
              is PsiDocParamRef -> elParent.reference
              else -> null
            }
            reference?.let { JavaUSimpleNameReferenceExpression(el, el.text, givenParent, it) }
          }
        }
        is PsiCatchSection -> el<UCatchClause>(build(::JavaUCatchClause))
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
        expr<UQualifiedReferenceExpression> { JavaUQualifiedReferenceExpression(reference, givenParent) }
      }
      else {
        val name = reference.referenceName ?: "<error name>"
        expr<USimpleNameReferenceExpression> { JavaUSimpleNameReferenceExpression(reference, name, givenParent, reference) }
      }
    }
  }

  internal fun convertExpression(el: PsiExpression,
                                 givenParent: UElement?,
                                 requiredType: Array<out Class<out UElement>> = DEFAULT_EXPRESSION_TYPES_LIST): UExpression? {
    fun <P : PsiElement> build(ctor: (P, UElement?) -> UExpression): () -> UExpression? {
      @Suppress("UNCHECKED_CAST")
      return fun(): UExpression? {
        return ctor(el as P, givenParent)
      }
    }

    return with(requiredType) {
      when (el) {
        is PsiAssignmentExpression -> expr<UBinaryExpression>(build(::JavaUAssignmentExpression))
        is PsiConditionalExpression -> expr<UIfExpression>(build(::JavaUTernaryIfExpression))
        is PsiNewExpression -> {
          if (el.anonymousClass != null)
            expr<UObjectLiteralExpression>(build(::JavaUObjectLiteralExpression))
          else
            expr<UCallExpression>(build(::JavaConstructorUCallExpression))
        }
        is PsiMethodCallExpression -> psiMethodCallConversionAlternatives(el, givenParent, requiredType).firstOrNull()
        is PsiArrayInitializerExpression -> expr<UCallExpression>(build(::JavaArrayInitializerUCallExpression))
        is PsiBinaryExpression -> expr<UBinaryExpression>(build(::JavaUBinaryExpression))
        // Should go after PsiBinaryExpression since it implements PsiPolyadicExpression
        is PsiPolyadicExpression -> expr<UPolyadicExpression>(build(::JavaUPolyadicExpression))
        is PsiParenthesizedExpression -> expr<UParenthesizedExpression>(build(::JavaUParenthesizedExpression))
        is PsiPrefixExpression -> expr<UPrefixExpression>(build(::JavaUPrefixExpression))
        is PsiPostfixExpression -> expr<UPostfixExpression>(build(::JavaUPostfixExpression))
        is PsiLiteralExpressionImpl -> expr<JavaULiteralExpression>(build(::JavaULiteralExpression))
        is PsiMethodReferenceExpression -> expr<UCallableReferenceExpression>(build(::JavaUCallableReferenceExpression))
        is PsiReferenceExpression -> convertReference(el, givenParent, requiredType)
        is PsiThisExpression -> expr<UThisExpression>(build(::JavaUThisExpression))
        is PsiSuperExpression -> expr<USuperExpression>(build(::JavaUSuperExpression))
        is PsiInstanceOfExpression -> expr<UBinaryExpressionWithType>(build(::JavaUInstanceCheckExpression))
        is PsiTypeCastExpression -> expr<UBinaryExpressionWithType>(build(::JavaUTypeCastExpression))
        is PsiClassObjectAccessExpression -> expr<UClassLiteralExpression>(build(::JavaUClassLiteralExpression))
        is PsiArrayAccessExpression -> expr<UArrayAccessExpression>(build(::JavaUArrayAccessExpression))
        is PsiLambdaExpression -> expr<ULambdaExpression>(build(::JavaULambdaExpression))
        is PsiSwitchExpression -> expr<USwitchExpression>(build(::JavaUSwitchExpression))
        else -> expr<UExpression>(build(::UnknownJavaExpression))
      }
    }
  }

  internal fun psiMethodCallConversionAlternatives(element: PsiMethodCallExpression,
                                                   givenParent: UElement?,
                                                   requiredTypes: Array<out Class<out UElement>>): Sequence<UExpression> {
    if (element.methodExpression.qualifierExpression == null) {
      return sequenceOf(requiredTypes.expr<UCallExpression> { JavaUCallExpression(element, givenParent) }).filterNotNull()
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
    fun <P : PsiElement> build(ctor: (P, UElement?) -> UExpression): () -> UExpression? {
      @Suppress("UNCHECKED_CAST")
      return fun(): UExpression? {
        return ctor(el as P, givenParent)
      }
    }

    return with(requiredType) {
      when (el) {
        is PsiDeclarationStatement -> expr<UDeclarationsExpression> {
          convertDeclarations(el.declaredElements, givenParent ?: unwrapElements(el.parent).toUElement())
        }
        is PsiExpressionListStatement -> expr<UDeclarationsExpression> {
          convertDeclarations(el.expressionList.expressions, givenParent ?: unwrapElements(el.parent).toUElement())
        }
        is PsiBlockStatement -> expr<UBlockExpression>(build(::JavaUBlockExpression))
        is PsiLabeledStatement -> expr<ULabeledExpression>(build(::JavaULabeledExpression))
        is PsiExpressionStatement -> convertExpression(el.expression, givenParent, requiredType)
        is PsiIfStatement -> expr<UIfExpression>(build(::JavaUIfExpression))
        is PsiSwitchStatement -> expr<USwitchExpression>(build(::JavaUSwitchExpression))
        is PsiWhileStatement -> expr<UWhileExpression>(build(::JavaUWhileExpression))
        is PsiDoWhileStatement -> expr<UDoWhileExpression>(build(::JavaUDoWhileExpression))
        is PsiForStatement -> expr<UForExpression>(build(::JavaUForExpression))
        is PsiForeachStatement -> expr<UForEachExpression>(build(::JavaUForEachExpression))
        is PsiBreakStatement -> expr<UBreakExpression>(build(::JavaUBreakExpression))
        is PsiYieldStatement -> expr<UYieldExpression> { JavaUYieldExpression(el, el.expression, givenParent) }
        is PsiContinueStatement -> expr<UContinueExpression>(build(::JavaUContinueExpression))
        is PsiReturnStatement -> expr<UReturnExpression>(build(::JavaUReturnExpression))
        is PsiAssertStatement -> expr<UCallExpression>(build(::JavaUAssertExpression))
        is PsiThrowStatement -> expr<UThrowExpression>(build(::JavaUThrowExpression))
        is PsiSynchronizedStatement -> expr<UBlockExpression>(build(::JavaUSynchronizedExpression))
        is PsiTryStatement -> expr<UTryExpression>(build(::JavaUTryExpression))
        is PsiEmptyStatement -> expr<UExpression> { UastEmptyExpression(el.parent?.toUElement()) }
        is PsiSwitchLabelStatementBase -> expr<UExpression> {
          when (givenParent) {
            is JavaUSwitchEntryList -> givenParent.findUSwitchEntryForLabel(el)
            null -> PsiTreeUtil.getParentOfType(el, PsiSwitchBlock::class.java)?.let {
              JavaUSwitchExpression(it, null).body.findUSwitchEntryForLabel(el)
            }
            else -> null
          }
        }
        else -> expr<UExpression>(build(::UnknownJavaExpression))
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

private fun elementTypes(requiredType: Class<out UElement>?) = requiredType?.let { arrayOf(it) } ?: DEFAULT_TYPES_LIST

private fun <T : UElement> Array<out Class<out T>>.nonEmptyOr(default: Array<out Class<out UElement>>) = takeIf { it.isNotEmpty() }
                                                                                                         ?: default