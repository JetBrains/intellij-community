// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.java

import com.intellij.java.syntax.parser.JavaKeywords
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
import org.jetbrains.uast.java.expressions.*
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

    val visitor = object : JavaElementVisitor() {
      var result: UElement? = null

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
        result = requiredType.el<UMethod, PsiMethod>(method, givenParent) { el, gp ->
          JavaUMethod.create(el, this@JavaUastLanguagePlugin, gp)
        }
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

      override fun visitPatternVariable(variable: PsiPatternVariable) {
        result = requiredType.el<UParameter, PsiPatternVariable>(variable, givenParent, ::JavaUParameter)
      }
    }

    element.accept(visitor)

    return visitor.result
  }

  override val analysisPlugin: UastAnalysisPlugin?
    get() = UastAnalysisPlugin.byLanguage(JavaLanguage.INSTANCE)

  override fun getPossiblePsiSourceTypes(vararg uastTypes: Class<out UElement>): ClassSet<PsiElement> =
    when (uastTypes.size) {
      0 -> getPossibleSourceTypes(UElement::class.java)
      1 -> getPossibleSourceTypes(uastTypes.single())
      else -> ClassSetsWrapper(uastTypes.map2Array { getPossibleSourceTypes(it) })
    }

  override fun getContainingAnnotationEntry(uElement: UElement?, annotationsHint: Collection<String>): Pair<UAnnotation, String?>? {
    val sourcePsi = uElement?.sourcePsi ?: return null
    if (sourcePsi is PsiNameValuePair) {
      if (!isOneOfNames(sourcePsi, annotationsHint)) return null

      return super.getContainingAnnotationEntry(uElement, annotationsHint)
    }

    val parent = sourcePsi.parent ?: return null
    if (parent is PsiNameValuePair) {
      if (!isOneOfNames(parent, annotationsHint)) return null

      return super.getContainingAnnotationEntry(uElement, annotationsHint)
    }

    val annotationEntry = PsiTreeUtil.getParentOfType(parent, PsiNameValuePair::class.java, true, PsiMember::class.java)
    if (annotationEntry == null) return null

    if (!isOneOfNames(annotationEntry, annotationsHint)) return null

    return super.getContainingAnnotationEntry(uElement, annotationsHint)
  }

  private fun isOneOfNames(annotationEntry: PsiNameValuePair, annotationsHint: Collection<String>): Boolean {
    if (annotationsHint.isEmpty()) return true
    val qualifiedName = PsiTreeUtil.getParentOfType(annotationEntry, PsiAnnotation::class.java)?.qualifiedName
    return qualifiedName != null && annotationsHint.contains(qualifiedName)
  }
}

internal inline fun <reified ActualT : UElement> Class<*>?.el(f: () -> UElement?): UElement? {
  return if (this == null || isAssignableFrom(ActualT::class.java)) f() else null
}

private inline fun <reified ActualT : UElement, P : PsiElement> Class<out UElement>.el(arg1: P,
                                                                                       arg2: UElement?,
                                                                                       crossinline ctor: (P, UElement?) -> UElement?): UElement? {
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

    val visitor = object : JavaElementVisitor() {
      var result = false

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

      override fun visitResourceList(resourceList: PsiResourceList) {
        result = true
      }

      override fun visitDeconstructionList(deconstructionList: PsiDeconstructionList) {
        result = true
      }
    }

    element.accept(visitor)

    return visitor.result
  }

  internal fun convertPsiElement(el: PsiElement, givenParent: UElement?, requiredType: Class<out UElement>): UElement? {
    val visitor = object: JavaElementVisitor() {
      var result: UElement? = null

      override fun visitAnnotation(annotation: PsiAnnotation) {
        result = annotation.takeIf { PsiTreeUtil.getParentOfType(it, PsiAnnotationMemberValue::class.java, true) != null }?.let { requiredType.el<UExpression,PsiAnnotation>(it, givenParent, ::JavaUAnnotationCallExpression) }
      }

      override fun visitAnnotationArrayInitializer(initializer: PsiArrayInitializerMemberValue) {
        result = requiredType.el<UCallExpression,PsiArrayInitializerMemberValue>(initializer, givenParent, ::JavaAnnotationArrayInitializerUCallExpression)
      }

      override fun visitCatchSection(section: PsiCatchSection) {
        result = requiredType.el<UCatchClause,PsiCatchSection>(section, givenParent, ::JavaUCatchClause)
      }

      override fun visitCodeBlock(block: PsiCodeBlock) {
        result = requiredType.el<UBlockExpression, PsiCodeBlock>(block, givenParent, ::JavaUCodeBlockExpression)
      }

      override fun visitDocToken(token: PsiDocToken) {
        result = if (token.tokenType == JavaDocTokenType.DOC_TAG_VALUE_TOKEN) {
          val reference = when (val elParent = token.parent) {
            is PsiDocMethodOrFieldRef -> elParent.reference
            is PsiDocParamRef -> elParent.reference
            else -> null
          }
          if (reference == null) null else requiredType.el<USimpleNameReferenceExpression, PsiElement>(token, givenParent) { e, gp ->
            JavaUSimpleNameReferenceExpression(e, e.text, gp, reference)
          }
        }
        else null
      }

      override fun visitExpression(expression: PsiExpression) {
        result = convertExpression(expression, givenParent, requiredType)
      }

      override fun visitReferenceExpression(expression: PsiReferenceExpression) {
        visitExpression(expression)
      }

      override fun visitIdentifier(identifier: PsiIdentifier) {
        result = requiredType.el<UIdentifier, PsiElement>(identifier, givenParent, ::JavaLazyParentUIdentifier)
      }

      override fun visitImportStatement(statement: PsiImportStatement) {
        result = requiredType.el<UImportStatement,PsiImportStatementBase>(statement, givenParent, ::JavaUImportStatement)
      }

      override fun visitImportStaticStatement(statement: PsiImportStaticStatement) {
        result = requiredType.el<UImportStatement,PsiImportStatementBase>(statement, givenParent, ::JavaUImportStatement)
      }

      override fun visitKeyword(keyword: PsiKeyword) {
        result = if (keyword.text == JavaKeywords.SUPER || keyword.text == JavaKeywords.THIS) requiredType.el<UIdentifier, PsiElement>(keyword, givenParent, ::JavaLazyParentUIdentifier) else null
      }

      override fun visitModuleReferenceElement(refElement: PsiJavaModuleReferenceElement) {
        result = requiredType.el<UReferenceExpression,PsiJavaModuleReferenceElement>(refElement, givenParent, ::JavaUModuleReferenceExpression)
      }

      override fun visitNameValuePair(pair: PsiNameValuePair) {
        result = requiredType.el<UNamedExpression,PsiNameValuePair>(pair, givenParent, ::JavaUNamedExpression)
      }

      override fun visitReferenceElement(reference: PsiJavaCodeReferenceElement) {
        result = convertReference(reference, givenParent, requiredType)
      }

      override fun visitResourceExpression(expression: PsiResourceExpression) {
        result = convertExpression(expression.expression, givenParent, requiredType)
      }

      override fun visitStatement(statement: PsiStatement) {
        result = convertStatement(statement, givenParent, requiredType)
      }

      override fun visitTypeElement(type: PsiTypeElement) {
        result = requiredType.el<UTypeReferenceExpression,PsiTypeElement>(type, givenParent, ::JavaUTypeReferenceExpression)
      }

      override fun visitComment(comment: PsiComment) {
        result = requiredType.el<UComment,PsiComment>(comment, givenParent, ::UComment)
      }

      override fun visitTypeTestPattern(pattern: PsiTypeTestPattern) {
        result = requiredType.expr<UPatternExpression, PsiTypeTestPattern>(pattern, givenParent, ::JavaUTypePatternExpression)
      }

      override fun visitUnnamedPattern(pattern: PsiUnnamedPattern) {
        result = requiredType.expr<UPatternExpression, PsiUnnamedPattern>(pattern, givenParent, ::JavaUUnamedPatternExpression)
      }

      override fun visitDeconstructionPattern(deconstructionPattern: PsiDeconstructionPattern) {
        result = requiredType.expr<UPatternExpression, PsiDeconstructionPattern>(deconstructionPattern, givenParent,
                                                                                      ::JavaUDeconstructionPatternPattern)
      }
    }

    el.accept(visitor)
    return visitor.result
  }

  internal fun convertBlock(block: PsiCodeBlock, parent: UElement?): UBlockExpression = JavaUCodeBlockExpression(block, parent)

  internal fun convertReference(reference: PsiJavaCodeReferenceElement,
                                givenParent: UElement?,
                                requiredType: Class<out UElement>): UExpression? {
    return if (reference.isQualified) {
      requiredType.expr<UQualifiedReferenceExpression, PsiJavaCodeReferenceElement>(reference, givenParent, ::JavaUQualifiedReferenceExpression)
    }
    else {
      val name = reference.referenceName ?: "<error name>"
      requiredType.expr<USimpleNameReferenceExpression, PsiElement>(reference, givenParent) { ref, gp -> JavaUSimpleNameReferenceExpression(ref, name, gp, ref as PsiReference) }
    }
  }

  internal fun convertExpression(el: PsiExpression,
                                 givenParent: UElement?,
                                 requiredType: Class<out UElement>): UExpression? {
    val visitor = object : JavaElementVisitor() {
      var result: UExpression? = null

      override fun visitArrayAccessExpression(expression: PsiArrayAccessExpression) {
        result = requiredType.expr<UArrayAccessExpression, PsiArrayAccessExpression>(expression, givenParent, ::JavaUArrayAccessExpression)
      }

      override fun visitArrayInitializerExpression(expression: PsiArrayInitializerExpression) {
        result = requiredType.expr<UCallExpression, PsiArrayInitializerExpression>(expression, givenParent,
                                                                                   ::JavaArrayInitializerUCallExpression)
      }

      override fun visitAssignmentExpression(expression: PsiAssignmentExpression) {
        result = requiredType.expr<UBinaryExpression, PsiAssignmentExpression>(expression, givenParent, ::JavaUAssignmentExpression)
      }

      override fun visitBinaryExpression(expression: PsiBinaryExpression) {
        result = requiredType.expr<UBinaryExpression, PsiBinaryExpression>(expression, givenParent, ::JavaUBinaryExpression)
      }

      override fun visitClassObjectAccessExpression(expression: PsiClassObjectAccessExpression) {
        result = requiredType.expr<UClassLiteralExpression, PsiClassObjectAccessExpression>(expression, givenParent,
                                                                                            ::JavaUClassLiteralExpression)
      }

      override fun visitConditionalExpression(expression: PsiConditionalExpression) {
        result = requiredType.expr<UIfExpression, PsiConditionalExpression>(expression, givenParent, ::JavaUTernaryIfExpression)
      }

      override fun visitInstanceOfExpression(expression: PsiInstanceOfExpression) {
        result = if (expression.pattern != null) {
          requiredType.expr<UBinaryExpressionWithType, PsiInstanceOfExpression>(expression, givenParent,
                                                                                ::JavaUInstanceWithPatternExpression)
        } else {
          requiredType.expr<UBinaryExpressionWithPattern, PsiInstanceOfExpression>(expression, givenParent, ::JavaUInstanceCheckExpression)
        }
      }

      override fun visitLambdaExpression(expression: PsiLambdaExpression) {
        result = requiredType.expr<ULambdaExpression, PsiLambdaExpression>(expression, givenParent, ::JavaULambdaExpression)
      }

      override fun visitLiteralExpression(expression: PsiLiteralExpression) {
        if (expression is PsiLiteralExpressionImpl) {
          result = requiredType.expr<JavaULiteralExpression, PsiLiteralExpressionImpl>(expression, givenParent, ::JavaULiteralExpression)
        }
        else {
          result = requiredType.expr<UExpression, PsiElement>(expression, givenParent, ::UnknownJavaExpression)
        }
      }

      override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
        result = psiMethodCallConversionAlternatives(expression, givenParent, requiredType)
      }

      override fun visitMethodReferenceExpression(expression: PsiMethodReferenceExpression) {
        result = requiredType.expr<UCallableReferenceExpression, PsiMethodReferenceExpression>(expression, givenParent,
                                                                                               ::JavaUCallableReferenceExpression)
      }

      override fun visitNewExpression(expression: PsiNewExpression) {
        if (expression.anonymousClass != null)
          result = requiredType.expr<UObjectLiteralExpression, PsiNewExpression>(expression, givenParent, ::JavaUObjectLiteralExpression)
        else
          result = requiredType.expr<UCallExpression, PsiNewExpression>(expression, givenParent, ::JavaConstructorUCallExpression)

      }

      override fun visitParenthesizedExpression(expression: PsiParenthesizedExpression) {
        result = requiredType.expr<UParenthesizedExpression, PsiParenthesizedExpression>(expression, givenParent,
                                                                                         ::JavaUParenthesizedExpression)
      }

      override fun visitPolyadicExpression(expression: PsiPolyadicExpression) {
        result = requiredType.expr<UPolyadicExpression, PsiPolyadicExpression>(expression, givenParent, ::JavaUPolyadicExpression)
      }

      override fun visitPostfixExpression(expression: PsiPostfixExpression) {
        result = requiredType.expr<UPostfixExpression, PsiPostfixExpression>(expression, givenParent, ::JavaUPostfixExpression)
      }

      override fun visitPrefixExpression(expression: PsiPrefixExpression) {
        result = requiredType.expr<UPrefixExpression, PsiPrefixExpression>(expression, givenParent, ::JavaUPrefixExpression)
      }

      override fun visitReferenceExpression(expression: PsiReferenceExpression) {
        result = convertReference(expression, givenParent, requiredType)
      }

      override fun visitSuperExpression(expression: PsiSuperExpression) {
        result = requiredType.expr<USuperExpression, PsiSuperExpression>(expression, givenParent, ::JavaUSuperExpression)
      }

      override fun visitSwitchExpression(expression: PsiSwitchExpression) {
        result = requiredType.expr<USwitchExpression, PsiSwitchExpression>(expression, givenParent, ::JavaUSwitchExpression)
      }

      override fun visitThisExpression(expression: PsiThisExpression) {
        result = requiredType.expr<UThisExpression, PsiThisExpression>(expression, givenParent, ::JavaUThisExpression)
      }

      override fun visitTypeCastExpression(expression: PsiTypeCastExpression) {
        result = requiredType.expr<UBinaryExpressionWithType, PsiTypeCastExpression>(expression, givenParent, ::JavaUTypeCastExpression)
      }

      override fun visitElement(element: PsiElement) {
        result = requiredType.expr<UExpression, PsiElement>(element, givenParent, ::UnknownJavaExpression)
      }
    }

    el.accept(visitor)

    return visitor.result
  }

  internal fun psiMethodCallConversionAlternatives(element: PsiMethodCallExpression,
                                                   givenParent: UElement?,
                                                   requiredTypes: Array<out Class<out UElement>>): Sequence<UExpression> {
    if (element.methodExpression.qualifierExpression == null) {
      return sequenceOf(requiredTypes.expr<UCallExpression,PsiMethodCallExpression>(element, givenParent, ::JavaUCallExpression)).filterNotNull()
    }

    if (!requiredTypes.hasAssignableFrom(UQualifiedReferenceExpression::class.java) &&
        !requiredTypes.hasAssignableFrom(UCallExpression::class.java)) {
      return emptySequence()
    }

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
    val visitor = object : JavaElementVisitor() {
      var result: UExpression? = null

      override fun visitAssertStatement(statement: PsiAssertStatement) {
        result = requiredType.expr<UCallExpression, PsiAssertStatement>(statement, givenParent, ::JavaUAssertExpression)
      }

      override fun visitBlockStatement(statement: PsiBlockStatement) {
        result = requiredType.expr<UBlockExpression, PsiBlockStatement>(statement, givenParent, ::JavaUBlockExpression)
      }

      override fun visitBreakStatement(statement: PsiBreakStatement) {
        result = requiredType.expr<UBreakExpression, PsiBreakStatement>(statement, givenParent, ::JavaUBreakExpression)
      }

      override fun visitContinueStatement(statement: PsiContinueStatement) {
        result = requiredType.expr<UContinueExpression, PsiContinueStatement>(statement, givenParent, ::JavaUContinueExpression)
      }

      override fun visitDeclarationStatement(statement: PsiDeclarationStatement) {
        result = requiredType.expr<UDeclarationsExpression, PsiDeclarationStatement>(statement, givenParent) { e, gp ->
          convertDeclarations(e.declaredElements, gp ?: e.parent?.run { unwrapElements(this).toUElement() })
        }
      }

      override fun visitDoWhileStatement(statement: PsiDoWhileStatement) {
        result = requiredType.expr<UDoWhileExpression, PsiDoWhileStatement>(statement, givenParent, ::JavaUDoWhileExpression)
      }

      override fun visitEmptyStatement(statement: PsiEmptyStatement) {
        result = requiredType.expr<UExpression, PsiEmptyStatement>(statement, givenParent) { e, _ ->
          UastEmptyExpression(e.parent?.toUElement())
        }
      }

      override fun visitExpressionListStatement(statement: PsiExpressionListStatement) {
        result = requiredType.expr<UDeclarationsExpression, PsiExpressionListStatement>(statement, givenParent) { e, gp ->
          convertDeclarations(e.expressionList.expressions, gp ?: e.parent?.run { unwrapElements(this).toUElement() })
        }
      }

      override fun visitExpressionStatement(statement: PsiExpressionStatement) {
        result = convertExpression(statement.expression, givenParent, requiredType)
      }

      override fun visitForeachStatement(statement: PsiForeachStatement) {
        result = requiredType.expr<UForEachExpression, PsiForeachStatement>(statement, givenParent, ::JavaUForEachExpression)
      }

      override fun visitForStatement(statement: PsiForStatement) {
        result = requiredType.expr<UForExpression, PsiForStatement>(statement, givenParent, ::JavaUForExpression)
      }

      override fun visitIfStatement(statement: PsiIfStatement) {
        result = requiredType.expr<UIfExpression, PsiIfStatement>(statement, givenParent, ::JavaUIfExpression)
      }

      override fun visitLabeledStatement(statement: PsiLabeledStatement) {
        result = requiredType.expr<ULabeledExpression, PsiLabeledStatement>(statement, givenParent, ::JavaULabeledExpression)
      }

      override fun visitReturnStatement(statement: PsiReturnStatement) {
        result = requiredType.expr<UReturnExpression, PsiReturnStatement>(statement, givenParent, ::JavaUReturnExpression)
      }

      override fun visitSwitchLabelStatement(statement: PsiSwitchLabelStatement) {
        visitSwitchLabelStatementBase(statement)
      }

      private fun visitSwitchLabelStatementBase(statement: PsiSwitchLabelStatementBase) {
        result = requiredType.expr<UExpression, PsiSwitchLabelStatementBase>(statement, givenParent) { e, gp ->
          when (gp) {
            is JavaUSwitchEntryList -> gp.findUSwitchEntryForLabel(e)
            null -> PsiTreeUtil.getParentOfType(e, PsiSwitchBlock::class.java)?.let {
              JavaUSwitchExpression(it, null).body.findUSwitchEntryForLabel(e)
            }
            else -> null
          }
        }
      }

      override fun visitSwitchLabeledRuleStatement(statement: PsiSwitchLabeledRuleStatement) {
        visitSwitchLabelStatementBase(statement)
      }

      override fun visitSwitchStatement(statement: PsiSwitchStatement) {
        result = requiredType.expr<USwitchExpression, PsiSwitchStatement>(statement, givenParent, ::JavaUSwitchExpression)
      }

      override fun visitSynchronizedStatement(statement: PsiSynchronizedStatement) {
        result = requiredType.expr<UBlockExpression, PsiSynchronizedStatement>(statement, givenParent, ::JavaUSynchronizedExpression)
      }

      override fun visitThrowStatement(statement: PsiThrowStatement) {
        result = requiredType.expr<UThrowExpression, PsiThrowStatement>(statement, givenParent, ::JavaUThrowExpression)
      }

      override fun visitTryStatement(statement: PsiTryStatement) {
        result = requiredType.expr<UTryExpression, PsiTryStatement>(statement, givenParent, ::JavaUTryExpression)
      }

      override fun visitWhileStatement(statement: PsiWhileStatement) {
        result = requiredType.expr<UWhileExpression, PsiWhileStatement>(statement, givenParent, ::JavaUWhileExpression)
      }

      override fun visitYieldStatement(statement: PsiYieldStatement) {
        result = requiredType.expr<UYieldExpression, PsiYieldStatement>(statement, givenParent, ::JavaUYieldExpression)
      }

      override fun visitElement(element: PsiElement) {
        result = requiredType.expr<UExpression, PsiElement>(element, givenParent, ::UnknownJavaExpression)
      }
    }

    el.accept(visitor)

    return visitor.result
  }

  private fun convertDeclarations(elements: Array<out PsiElement>, parent: UElement?): UDeclarationsExpression {
    val expression = JavaUDeclarationsExpression(parent)

    val size = elements.size
    if (size == 1) {
      val uDecl = toDeclaration(elements[0], expression)
      expression.declarations = if (uDecl != null) listOf(uDecl) else emptyList()
      return expression
    }

    // almost never happens, most of the cases need only 1 resulting element
    val declarations = ArrayList<UDeclaration>(elements.size)
    for (element in elements) {
      val uDecl = toDeclaration(element, expression)
      if (uDecl != null) {
        declarations += uDecl
      }
    }
    expression.declarations = declarations

    return expression
  }

  private fun toDeclaration(element: PsiElement, containing: JavaUDeclarationsExpression): UDeclaration? {
    return when (element) {
      is PsiVariable -> JavaUVariable.create(element, containing)
      is PsiClass -> JavaUClass.create(element, containing)
      else -> null
    }
  }

  internal fun convertOrEmpty(statement: PsiStatement?, parent: UElement?): UExpression =
    if (statement == null) {
      UastEmptyExpression(parent)
    }
    else {
      convertStatement(statement, parent, UExpression::class.java) ?: UastEmptyExpression(parent)
    }

  internal fun convertOrEmpty(expression: PsiExpression?, parent: UElement?): UExpression =
    if (expression == null) {
      UastEmptyExpression(parent)
    }
    else {
      convertExpression(expression, parent, UExpression::class.java) ?: UastEmptyExpression(parent)
    }

  internal fun convertOrNull(expression: PsiExpression?, parent: UElement?): UExpression? =
    if (expression == null) null else convertExpression(expression, parent, UExpression::class.java)

  internal fun convertOrEmpty(block: PsiCodeBlock?, parent: UElement?): UExpression =
    if (block == null) UastEmptyExpression(parent) else convertBlock(block, parent)
}

private fun <T : UElement> Array<out Class<out T>>.nonEmptyOr(default: Array<out Class<out T>>): Array<out Class<out T>> = if (isNotEmpty()) this else default