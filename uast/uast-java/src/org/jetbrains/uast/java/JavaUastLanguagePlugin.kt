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
import org.jetbrains.uast.java.JavaConverter.convertPsiElement
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
    is UnknownJavaExpression -> {
      val parent = element.uastParent
      parent is UExpression && isExpressionValueUsed(parent)
    }
    else -> {
      val statement = element.sourcePsi as? PsiStatement
      statement != null && statement.parent !is PsiExpressionStatement
    }
  }

  override fun getMethodCallExpression(element: PsiElement, containingClassFqName: String?, methodName: String): UastLanguagePlugin.ResolvedMethod? {
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

  override fun getConstructorCallExpression(element: PsiElement, fqName: String): UastLanguagePlugin.ResolvedConstructor? {
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
    val target = if (requiredType == null) UElement::class.java else requiredType
    return if (canConvert(element.javaClass, target)) {
      convertDeclaration(element, parent, target) ?: convertPsiElement(element, parent, target)
    }
    else null
  }

  override fun convertElementWithParent(element: PsiElement, requiredType: Class<out UElement>?): UElement? {
    if (element is PsiJavaFile) return requiredType.el<UFile> { JavaUFile(element, this) }

    return convertElement(element, null, requiredType)
  }

  override fun <T : UElement> convertElementWithParent(element: PsiElement, requiredTypes: Array<out Class<out T>>): T? {
    val nonEmptyRequiredTypes = requiredTypes.nonEmptyOr(DEFAULT_TYPES_LIST)
    if (canConvert(element.javaClass, requiredTypes)) {
      val declaration = nonEmptyRequiredTypes.asSequence()
        .map { requiredType -> convertDeclaration(element, null, requiredType) }
        .firstNotNullOfOrNull { u -> u }
      @Suppress("UNCHECKED_CAST")
      if (declaration != null) return declaration as T
      @Suppress("UNCHECKED_CAST")
      return nonEmptyRequiredTypes.asSequence()
        .map { requiredType -> convertPsiElement(element, null, requiredType) }
        .firstNotNullOfOrNull { u -> u } as T?
    }
    return null
  }

  @Suppress("UNCHECKED_CAST")
  override fun <T : UElement> convertToAlternatives(element: PsiElement, requiredTypes: Array<out Class<out T>>): Sequence<T> = when (element) {
    is PsiMethodCallExpression ->
      JavaConverter.psiMethodCallConversionAlternatives(element,
        null,
        requiredTypes.nonEmptyOr(DEFAULT_EXPRESSION_TYPES_LIST)) as Sequence<T>
    is PsiRecordComponent -> convertRecordConstructorParameterAlternatives(element, null, requiredTypes) as Sequence<T>
    else -> sequenceOf(convertElementWithParent(element, requiredTypes.nonEmptyOr(DEFAULT_TYPES_LIST)) as? T).filterNotNull()
  }

  private fun convertDeclaration(element: PsiElement, givenParent: UElement?, requiredType: Class<out UElement>): UElement? {
    if (element is UDeclaration) {
      return requiredType.el<UDeclaration,PsiElement>(element, null) { _, _ -> element }
    }
    var result: UElement? = null
    element.accept(object : JavaElementVisitor() {
      override fun visitAnnotation(annotation: PsiAnnotation) {
        result = requiredType.el<UAnnotation, PsiAnnotation>(annotation, givenParent, ::JavaUAnnotation)
      }

      override fun visitClass(aClass: PsiClass) {
        result = requiredType.el<UClass, PsiClass>(aClass, givenParent, JavaUClass::create)
      }

      override fun visitClassInitializer(initializer: PsiClassInitializer) {
        result = requiredType.el<UClassInitializer, PsiClassInitializer>(initializer, givenParent, ::JavaUClassInitializer)
      }

      override fun visitEnumConstant(enumConstant: PsiEnumConstant) {
        result = requiredType.el<UEnumConstant, PsiEnumConstant>(enumConstant, givenParent, ::JavaUEnumConstant)
      }

      override fun visitField(field: PsiField) {
        if (field is LightRecordField) {
          result = convertRecordConstructorParameterAlternatives(field, givenParent, requiredType)
        }
        else {
          result = requiredType.el<UField, PsiField>(field, givenParent, ::JavaUField)
        }
      }

      override fun visitJavaFile(file: PsiJavaFile) {
        result = requiredType.el<UFile, PsiJavaFile>(file, null) { el, _ -> JavaUFile(el, this@JavaUastLanguagePlugin) }
      }

      override fun visitLocalVariable(variable: PsiLocalVariable) {
        result = requiredType.el<ULocalVariable, PsiLocalVariable>(variable, givenParent, ::JavaULocalVariable)
      }

      override fun visitMethod(method: PsiMethod) {
        result = requiredType.el<UMethod, PsiMethod>(method, givenParent) { el, gp -> JavaUMethod.create(el, this@JavaUastLanguagePlugin, gp) }
      }

      override fun visitParameter(parameter: PsiParameter) {
        if (parameter is LightRecordConstructorParameter) {
          result = convertRecordConstructorParameterAlternatives(element, givenParent, requiredType)
        }
        else {
          result = requiredType.el<UParameter, PsiParameter>(parameter, givenParent, ::JavaUParameter)
        }
      }

      override fun visitRecordComponent(recordComponent: PsiRecordComponent) {
        result = convertRecordConstructorParameterAlternatives(recordComponent, givenParent, requiredType)
      }

      override fun visitRecordHeader(recordHeader: PsiRecordHeader) {
        result = requiredType.el<UMethod, PsiRecordHeader>(recordHeader, givenParent, JavaUMethod::create)
      }

      override fun visitVariable(variable: PsiVariable) {
        result = requiredType.el<UVariable, PsiVariable>(variable, givenParent, ::JavaUVariable)
      }
    })
    return result
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

internal inline fun <reified ActualT : UElement, P : PsiElement> Class<out UElement>.el(arg1: P,
                                                                                        arg2: UElement?,
                                                                                        ctor: (P, UElement?) -> UElement?): UElement? {
  return if (this.isAssignableFrom(ActualT::class.java)) ctor(arg1, arg2) else null
}

internal inline fun <reified ActualT : UElement, P : PsiElement> Array<out Class<out UElement>>.expr(arg1: P, arg2: UElement?, ctor: (P, UElement?) -> UExpression?): UExpression? =
  if (hasAssignableFrom(ActualT::class.java)) ctor(arg1, arg2) else null
internal inline fun <reified ActualT : UElement, P : PsiElement> Class<out UElement>.expr(arg1: P, arg2: UElement?, ctor: (P, UElement?) -> UExpression?): UExpression? =
  if (this.isAssignableFrom(ActualT::class.java)) ctor(arg1, arg2) else null

internal fun Array<out Class<out UElement>>.hasAssignableFrom(cls: Class<*>): Boolean = any { it.isAssignableFrom(cls) }

internal object JavaConverter {
  internal tailrec fun unwrapElements(element: PsiElement): PsiElement {
    val parent = element.parent
    return if (parent != null && shouldUnwrapParent(element)) unwrapElements(parent) else element
  }

  private fun shouldUnwrapParent(element: PsiElement): Boolean {
    if (element is LazyParseablePsiElement) {
      return element.elementType == JavaDocElementType.DOC_REFERENCE_HOLDER
    }
    var result:Boolean = false
    element.accept(object:JavaElementVisitor() {
      override fun visitAnnotationParameterList(list: PsiAnnotationParameterList) {
        result = true
      }

      override fun visitBlockStatement(statement: PsiBlockStatement) {
        result = true
      }

      override fun visitCaseLabelElementList(list: PsiCaseLabelElementList) {
        result = true
      }

      override fun visitDocTag(tag: PsiDocTag) {
        result = true
      }

      override fun visitDocTagValue(value: PsiDocTagValue) {
        result = true
      }

      override fun visitExpressionList(list: PsiExpressionList) {
        result = true
      }

      override fun visitExpressionStatement(statement: PsiExpressionStatement) {
        result = true
      }

      override fun visitImportList(list: PsiImportList) {
        result = true
      }

      override fun visitModifierList(list: PsiModifierList) {
        result = true
      }

      override fun visitPackageStatement(statement: PsiPackageStatement) {
        result = true
      }

      override fun visitParameterList(list: PsiParameterList) {
        result = true
      }

      override fun visitReferenceList(list: PsiReferenceList) {
        result = true
      }

      override fun visitReferenceParameterList(list: PsiReferenceParameterList) {
        result = true
      }
    })
    return result
  }

  internal fun convertPsiElement(el: PsiElement,
                                 givenParent: UElement?,
                                 requiredType: Class<out UElement>): UElement? {
    return when (el) {
      is PsiCodeBlock -> requiredType.el<UBlockExpression, PsiCodeBlock>(el, givenParent, ::JavaUCodeBlockExpression)
      is PsiResourceExpression -> convertExpression(el.expression, givenParent, requiredType)
      is PsiExpression -> convertExpression(el, givenParent, requiredType)
      is PsiStatement -> convertStatement(el, givenParent, requiredType)
      is PsiImportStatementBase -> requiredType.el<UImportStatement,PsiImportStatementBase>(el, givenParent, ::JavaUImportStatement)
      is PsiIdentifier -> requiredType.el<UIdentifier, PsiElement>(el, givenParent, ::JavaLazyParentUIdentifier)
      is PsiKeyword -> if (el.text == PsiKeyword.SUPER || el.text == PsiKeyword.THIS)
                         requiredType.el<UIdentifier, PsiElement>(el, givenParent, ::JavaLazyParentUIdentifier)
                       else null
      is PsiNameValuePair -> requiredType.el<UNamedExpression,PsiNameValuePair>(el, givenParent, ::JavaUNamedExpression)
      is PsiArrayInitializerMemberValue -> requiredType.el<UCallExpression,PsiArrayInitializerMemberValue>(el, givenParent,
                                                                                              ::JavaAnnotationArrayInitializerUCallExpression)
      is PsiTypeElement -> requiredType.el<UTypeReferenceExpression,PsiTypeElement>(el, givenParent, ::JavaUTypeReferenceExpression)
      is PsiJavaCodeReferenceElement -> convertReference(el, givenParent, requiredType)
      is PsiJavaModuleReferenceElement -> requiredType.el<UReferenceExpression,PsiJavaModuleReferenceElement>(el, givenParent,
                                                                                                 ::JavaUModuleReferenceExpression)
      is PsiAnnotation -> el.takeIf { PsiTreeUtil.getParentOfType(it, PsiAnnotationMemberValue::class.java, true) != null }?.let {
        requiredType.el<UExpression,PsiAnnotation>(it, givenParent, ::JavaUAnnotationCallExpression)
      }
      is PsiComment -> requiredType.el<UComment,PsiComment>(el, givenParent, ::UComment)
      is PsiDocToken ->
        if (el.tokenType == JavaDocTokenType.DOC_TAG_VALUE_TOKEN) {
          val reference = when (val elParent = el.parent) {
            is PsiDocMethodOrFieldRef -> elParent.reference
            is PsiDocParamRef -> elParent.reference
            else -> null
          }
          if (reference == null) null else requiredType.el<USimpleNameReferenceExpression, PsiElement>(el, givenParent) { e, gp ->
            JavaUSimpleNameReferenceExpression(e, e.text, gp, reference)
          }
        }
        else null

      is PsiCatchSection -> requiredType.el<UCatchClause,PsiCatchSection>(el, givenParent, ::JavaUCatchClause)
      else -> null
    }
  }

  internal fun convertBlock(block: PsiCodeBlock, parent: UElement?): UBlockExpression = JavaUCodeBlockExpression(block, parent)

  internal fun convertReference(reference: PsiJavaCodeReferenceElement,
                                givenParent: UElement?,
                                requiredType: Class<out UElement>): UExpression? {
    return if (reference.isQualified) {
      requiredType.expr<UQualifiedReferenceExpression,PsiJavaCodeReferenceElement>(reference, givenParent, ::JavaUQualifiedReferenceExpression)
    }
    else {
      val name = reference.referenceName ?: "<error name>"
      requiredType.expr<USimpleNameReferenceExpression, PsiElement>(reference, givenParent) { ref, gp -> JavaUSimpleNameReferenceExpression(ref, name, gp, ref as PsiReference) }
    }
  }

  internal fun convertExpression(el: PsiExpression,
                                 givenParent: UElement?,
                                 requiredType: Class<out UElement>): UExpression? {
    return when (el) {
      is PsiArrayAccessExpression -> requiredType.expr<UArrayAccessExpression,PsiArrayAccessExpression>(el, givenParent, ::JavaUArrayAccessExpression)
      is PsiArrayInitializerExpression -> requiredType.expr<UCallExpression,PsiArrayInitializerExpression>(el, givenParent, ::JavaArrayInitializerUCallExpression)
      is PsiAssignmentExpression -> requiredType.expr<UBinaryExpression, PsiAssignmentExpression>(el, givenParent, ::JavaUAssignmentExpression)
      is PsiBinaryExpression -> requiredType.expr<UBinaryExpression,PsiBinaryExpression>(el, givenParent, ::JavaUBinaryExpression)
      is PsiClassObjectAccessExpression -> requiredType.expr<UClassLiteralExpression,PsiClassObjectAccessExpression>(el, givenParent, ::JavaUClassLiteralExpression)
      is PsiConditionalExpression -> requiredType.expr<UIfExpression, PsiConditionalExpression>(el, givenParent, ::JavaUTernaryIfExpression)
      is PsiInstanceOfExpression -> requiredType.expr<UBinaryExpressionWithType,PsiInstanceOfExpression>(el, givenParent, ::JavaUInstanceCheckExpression)
      is PsiLambdaExpression -> requiredType.expr<ULambdaExpression,PsiLambdaExpression>(el, givenParent, ::JavaULambdaExpression)
      is PsiLiteralExpressionImpl -> requiredType.expr<JavaULiteralExpression,PsiLiteralExpressionImpl>(el, givenParent, ::JavaULiteralExpression)
      is PsiMethodCallExpression -> psiMethodCallConversionAlternatives(el, givenParent, requiredType)
      is PsiMethodReferenceExpression -> requiredType.expr<UCallableReferenceExpression,PsiMethodReferenceExpression>(el, givenParent, ::JavaUCallableReferenceExpression)
      is PsiNewExpression -> {
        if (el.anonymousClass != null)
          requiredType.expr<UObjectLiteralExpression,PsiNewExpression>(el, givenParent, ::JavaUObjectLiteralExpression)
        else
          requiredType.expr<UCallExpression,PsiNewExpression>(el, givenParent, ::JavaConstructorUCallExpression)
      }
      is PsiParenthesizedExpression -> requiredType.expr<UParenthesizedExpression,PsiParenthesizedExpression>(el, givenParent, ::JavaUParenthesizedExpression)
      // Should go after PsiBinaryExpression since it implements PsiPolyadicExpression
      is PsiPolyadicExpression -> requiredType.expr<UPolyadicExpression,PsiPolyadicExpression>(el, givenParent, ::JavaUPolyadicExpression)
      is PsiPostfixExpression -> requiredType.expr<UPostfixExpression,PsiPostfixExpression>(el, givenParent, ::JavaUPostfixExpression)
      is PsiPrefixExpression -> requiredType.expr<UPrefixExpression,PsiPrefixExpression>(el, givenParent, ::JavaUPrefixExpression)
      is PsiReferenceExpression -> convertReference(el, givenParent, requiredType)
      is PsiSuperExpression -> requiredType.expr<USuperExpression,PsiSuperExpression>(el, givenParent, ::JavaUSuperExpression)
      is PsiSwitchExpression -> requiredType.expr<USwitchExpression,PsiSwitchExpression>(el, givenParent, ::JavaUSwitchExpression)
      is PsiThisExpression -> requiredType.expr<UThisExpression,PsiThisExpression>(el, givenParent, ::JavaUThisExpression)
      is PsiTypeCastExpression -> requiredType.expr<UBinaryExpressionWithType,PsiTypeCastExpression>(el, givenParent, ::JavaUTypeCastExpression)
      else -> requiredType.expr<UExpression,PsiElement>(el, givenParent, ::UnknownJavaExpression)
    }
  }

  internal fun psiMethodCallConversionAlternatives(element: PsiMethodCallExpression,
                                                   givenParent: UElement?,
                                                   requiredTypes: Array<out Class<out UElement>>): Sequence<UExpression> {
    if (element.methodExpression.qualifierExpression == null) {
      return sequenceOf(requiredTypes.expr<UCallExpression,PsiMethodCallExpression>(element, givenParent, ::JavaUCallExpression)).filterNotNull()
    }

    if (!requiredTypes.hasAssignableFrom(UQualifiedReferenceExpression::class.java) &&
        !requiredTypes.hasAssignableFrom(UCallExpression::class.java)) return emptySequence()


    val expr = JavaUCompositeQualifiedExpression(element, givenParent).apply {
      receiverInitializer = {
        convertOrEmpty(element.methodExpression.qualifierExpression!!, this@apply)
      }
      selector = JavaUCallExpression(element, this@apply)
    }

    val results = sequenceOf(expr, expr.selector)
    return requiredTypes.asSequence().flatMap { requiredType -> results.filter { requiredType.isInstance(it) } }.distinct()
  }
  private fun psiMethodCallConversionAlternatives(element: PsiMethodCallExpression,
                                                  givenParent: UElement?,
                                                  requiredType: Class<out UElement>): UExpression? {
    if (element.methodExpression.qualifierExpression == null) {
      return requiredType.expr<UCallExpression,PsiMethodCallExpression>(element, givenParent, ::JavaUCallExpression)
    }

    if (!requiredType.isAssignableFrom(UQualifiedReferenceExpression::class.java) &&
        !requiredType.isAssignableFrom(UCallExpression::class.java)) return null


    val expr = JavaUCompositeQualifiedExpression(element, givenParent).apply {
      receiverInitializer = {
        convertOrEmpty(element.methodExpression.qualifierExpression!!, this@apply)
      }
      selector = JavaUCallExpression(element, this@apply)
    }

    return if (requiredType.isInstance(expr)) expr
    else if (requiredType.isInstance(expr.selector)) expr.selector
    else null
  }

  internal fun convertStatement(el: PsiStatement,
                                givenParent: UElement?,
                                requiredType: Class<out UElement>): UExpression? {
    return when (el) {
      is PsiDeclarationStatement -> requiredType.expr<UDeclarationsExpression,PsiDeclarationStatement>(el, givenParent) { e, gp ->
        convertDeclarations(e.declaredElements, gp ?: unwrapElements(e.parent).toUElement())
      }
      is PsiExpressionListStatement -> requiredType.expr<UDeclarationsExpression,PsiExpressionListStatement>(el, givenParent) { e, gp ->
        convertDeclarations(e.expressionList.expressions, gp ?: unwrapElements(e.parent).toUElement())
      }
      is PsiBlockStatement -> requiredType.expr<UBlockExpression,PsiBlockStatement>(el, givenParent, ::JavaUBlockExpression)
      is PsiLabeledStatement -> requiredType.expr<ULabeledExpression,PsiLabeledStatement>(el, givenParent, ::JavaULabeledExpression)
      is PsiExpressionStatement -> convertExpression(el.expression, givenParent, requiredType)
      is PsiIfStatement -> requiredType.expr<UIfExpression,PsiIfStatement>(el, givenParent, ::JavaUIfExpression)
      is PsiSwitchStatement -> requiredType.expr<USwitchExpression,PsiSwitchStatement>(el, givenParent, ::JavaUSwitchExpression)
      is PsiWhileStatement -> requiredType.expr<UWhileExpression,PsiWhileStatement>(el, givenParent, ::JavaUWhileExpression)
      is PsiDoWhileStatement -> requiredType.expr<UDoWhileExpression,PsiDoWhileStatement>(el, givenParent, ::JavaUDoWhileExpression)
      is PsiForStatement -> requiredType.expr<UForExpression,PsiForStatement>(el, givenParent, ::JavaUForExpression)
      is PsiForeachStatement -> requiredType.expr<UForEachExpression,PsiForeachStatement>(el, givenParent, ::JavaUForEachExpression)
      is PsiBreakStatement -> requiredType.expr<UBreakExpression,PsiBreakStatement>(el, givenParent, ::JavaUBreakExpression)
      is PsiYieldStatement -> requiredType.expr<UYieldExpression,PsiYieldStatement>(el, givenParent, ::JavaUYieldExpression)
      is PsiContinueStatement -> requiredType.expr<UContinueExpression,PsiContinueStatement>(el, givenParent, ::JavaUContinueExpression)
      is PsiReturnStatement -> requiredType.expr<UReturnExpression,PsiReturnStatement>(el, givenParent, ::JavaUReturnExpression)
      is PsiAssertStatement -> requiredType.expr<UCallExpression,PsiAssertStatement>(el, givenParent, ::JavaUAssertExpression)
      is PsiThrowStatement -> requiredType.expr<UThrowExpression,PsiThrowStatement>(el, givenParent, ::JavaUThrowExpression)
      is PsiSynchronizedStatement -> requiredType.expr<UBlockExpression,PsiSynchronizedStatement>(el, givenParent, ::JavaUSynchronizedExpression)
      is PsiTryStatement -> requiredType.expr<UTryExpression,PsiTryStatement>(el, givenParent, ::JavaUTryExpression)
      is PsiEmptyStatement -> requiredType.expr<UExpression,PsiEmptyStatement>(el,givenParent) { e,_->UastEmptyExpression(e.parent?.toUElement()) }
      is PsiSwitchLabelStatementBase -> requiredType.expr<UExpression,PsiSwitchLabelStatementBase>(el, givenParent) { e, gp ->
        when (gp) {
          is JavaUSwitchEntryList -> gp.findUSwitchEntryForLabel(e)
          null -> PsiTreeUtil.getParentOfType(e, PsiSwitchBlock::class.java)?.let {
            JavaUSwitchExpression(it, null).body.findUSwitchEntryForLabel(e)
          }
          else -> null
        }
      }
      else -> requiredType.expr<UExpression, PsiElement>(el, givenParent, ::UnknownJavaExpression)
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
    if (statement == null) UastEmptyExpression(parent) else {
      val converted = convertStatement(statement, parent, UExpression::class.java)
      if (converted == null) UastEmptyExpression(parent) else converted
    }

  internal fun convertOrEmpty(expression: PsiExpression?, parent: UElement?): UExpression =
    if (expression == null) UastEmptyExpression(parent) else {
      val converted = convertExpression(expression, parent, UExpression::class.java)
      if (converted == null) UastEmptyExpression(parent) else converted
    }

  internal fun convertOrNull(expression: PsiExpression?, parent: UElement?): UExpression? =
    if (expression == null) null else convertExpression(expression, parent, UExpression::class.java)

  internal fun convertOrEmpty(block: PsiCodeBlock?, parent: UElement?): UExpression =
    if (block == null) UastEmptyExpression(parent) else convertBlock(block, parent)
}

private fun <T : UElement> Array<out Class<out T>>.nonEmptyOr(default: Array<out Class<out T>>): Array<out Class<out T>> = if (isNotEmpty()) this else default