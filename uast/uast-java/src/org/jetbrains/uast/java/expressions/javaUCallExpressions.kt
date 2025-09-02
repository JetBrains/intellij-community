// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.java

import com.intellij.java.syntax.parser.JavaKeywords
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiTypesUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*
import org.jetbrains.uast.java.expressions.JavaUExpressionList
import org.jetbrains.uast.java.internal.PsiArrayToUElementListMappingView
import org.jetbrains.uast.psi.UElementWithLocation

@ApiStatus.Internal
class JavaUCallExpression(
  override val sourcePsi: PsiMethodCallExpression,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UCallExpression, UElementWithLocation, UMultiResolvable {

  private val valueArgumentsPart = UastLazyPart<List<UExpression>>()
  private val methodIdentifierPart = UastLazyPart<UIdentifier?>()
  private var typeArgumentCountLazy = Int.MIN_VALUE

  override val kind: UastCallKind
    get() {
      val element = nameReferenceElement
      if (element is PsiKeyword && (element.text == JavaKeywords.THIS || element.text == JavaKeywords.SUPER))
        return UastCallKind.CONSTRUCTOR_CALL

      return UastCallKind.METHOD_CALL
    }

  override val methodIdentifier: UIdentifier?
    get() = methodIdentifierPart.getOrBuild {
      nameReferenceElement?.let { UIdentifier(it, this) }
    }

  private val nameReferenceElement: PsiElement?
    get() = sourcePsi.methodExpression.referenceNameElement

  override val classReference: UReferenceExpression?
    get() = null

  override val valueArgumentCount: Int
    get() = sourcePsi.argumentList.expressionCount

  override val valueArguments: List<UExpression>
    get() = valueArgumentsPart.getOrBuild {
      PsiArrayToUElementListMappingView(sourcePsi.argumentList.expressions) { JavaConverter.convertOrEmpty(it, this@JavaUCallExpression) }
    }

  override fun getArgumentForParameter(i: Int): UExpression? {
    val resolved = multiResolve().mapNotNull { it.element as? PsiMethod }
    for (psiMethod in resolved) {
      val isVarArgs = psiMethod.parameterList.parameters.getOrNull(i)?.isVarArgs ?: continue
      if (isVarArgs) {
        return JavaUExpressionList(null, UastSpecialExpressionKind.VARARGS, this) {
          valueArguments.subList(i, valueArguments.size)
        }
      }
      return valueArguments.getOrNull(i)
    }
    return null
  }

  override val typeArgumentCount: Int
    get() {
      if (typeArgumentCountLazy == Int.MIN_VALUE) {
        typeArgumentCountLazy = sourcePsi.typeArguments.size
      }

      return typeArgumentCountLazy
    }

  override val typeArguments: List<PsiType>
    get() = sourcePsi.typeArguments.toList()

  override val returnType: PsiType?
    get() = sourcePsi.type

  override val methodName: String?
    get() = sourcePsi.methodExpression.referenceName

  override fun resolve(): PsiMethod? = sourcePsi.resolveMethod()
  override fun multiResolve(): Iterable<ResolveResult> =
    sourcePsi.methodExpression.multiResolve(false).asIterable()

  override fun getStartOffset(): Int =
    sourcePsi.methodExpression.referenceNameElement?.textOffset ?: sourcePsi.methodExpression.textOffset

  override fun getEndOffset(): Int = sourcePsi.textRange.endOffset

  override val receiver: UExpression?
    get() {
      uastParent.let { uastParent ->
        return if (uastParent is UQualifiedReferenceExpression && uastParent.selector == this)
          uastParent.receiver
        else
          null
      }
    }

  override val receiverType: PsiType?
    get() {
      val qualifierType = sourcePsi.methodExpression.qualifierExpression?.type
      if (qualifierType != null) {
        return qualifierType
      }

      val method = resolve() ?: return null
      if (method.hasModifierProperty(PsiModifier.STATIC)) return null

      val psiManager = sourcePsi.manager
      val containingClassForMethod = method.containingClass ?: return null

      val containingClass = PsiTreeUtil.getParentOfType(sourcePsi, PsiClass::class.java)
      val containingClassSequence = generateSequence(containingClass) {
        if (it.hasModifierProperty(PsiModifier.STATIC))
          null
        else
          PsiTreeUtil.getParentOfType(it, PsiClass::class.java)
      }

      val receiverClass = containingClassSequence.find { containingClassForExpression ->
        psiManager.areElementsEquivalent(containingClassForMethod, containingClassForExpression) ||
        containingClassForExpression.isInheritor(containingClassForMethod, true)
      }

      return receiverClass?.let { PsiTypesUtil.getClassType(it) }
    }
}

@ApiStatus.Internal
class JavaConstructorUCallExpression(
  override val sourcePsi: PsiNewExpression,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UCallExpression, UMultiResolvable {

  private val classReferencePart = UastLazyPart<UReferenceExpression?>()
  private val valueArgumentsPart = UastLazyPart<List<UExpression>>()

  private var kindLazy: UastCallKind? = null
  private var typeArgumentCountLazy = Int.MIN_VALUE

  override val kind: UastCallKind
    get() {
      if (kindLazy == null) {
        kindLazy = when {
          sourcePsi.arrayInitializer != null -> UastCallKind.NEW_ARRAY_WITH_INITIALIZER
          sourcePsi.arrayDimensions.isNotEmpty() -> UastCallKind.NEW_ARRAY_WITH_DIMENSIONS
          else -> UastCallKind.CONSTRUCTOR_CALL
        }
      }

      return kindLazy!!
    }

  override val receiver: UExpression?
    get() = null

  override val receiverType: PsiType?
    get() = null

  override val methodIdentifier: UIdentifier?
    get() = null

  override val classReference: UReferenceExpression?
    get() = classReferencePart.getOrBuild {
      sourcePsi.classReference?.let { ref ->
        JavaConverter.convertReference(ref, this, UElement::class.java) as? UReferenceExpression
      }
    }

  override val valueArgumentCount: Int
    get() {
      val initializer = sourcePsi.arrayInitializer
      return when {
        initializer != null -> initializer.initializers.size
        sourcePsi.arrayDimensions.isNotEmpty() -> sourcePsi.arrayDimensions.size
        else -> sourcePsi.argumentList?.expressions?.size ?: 0
      }
    }

  override val valueArguments: List<UExpression>
    get() = valueArgumentsPart.getOrBuild {
      val initializer = sourcePsi.arrayInitializer
      when {
        initializer != null -> initializer.initializers.map { JavaConverter.convertOrEmpty(it, this) }
        sourcePsi.arrayDimensions.isNotEmpty() -> sourcePsi.arrayDimensions.map { JavaConverter.convertOrEmpty(it, this) }
        else -> sourcePsi.argumentList?.expressions?.map { JavaConverter.convertOrEmpty(it, this) } ?: emptyList()
      }
    }

  override fun getArgumentForParameter(i: Int): UExpression? = valueArguments.getOrNull(i)

  override val typeArgumentCount: Int
    get() {
      if (typeArgumentCountLazy == Int.MIN_VALUE) {
        typeArgumentCountLazy = sourcePsi.classReference?.typeParameters?.size ?: 0
      }

      return typeArgumentCountLazy
    }

  override val typeArguments: List<PsiType>
    get() = sourcePsi.classReference?.typeParameters?.toList() ?: emptyList()

  override val returnType: PsiType?
    get() = (sourcePsi.classReference?.resolve() as? PsiClass)?.let { PsiTypesUtil.getClassType(it) } ?: sourcePsi.type

  override val methodName: String?
    get() = null

  override fun resolve(): PsiMethod? = sourcePsi.resolveMethod()
  override fun multiResolve(): Iterable<ResolveResult> {
    val methodResolve = sourcePsi.resolveMethodGenerics()
    if (methodResolve != JavaResolveResult.EMPTY) {
      // if there is a non-default constructor
      return listOf<ResolveResult>(methodResolve)
    }
    // unable to resolve constructor method - resolve to class
    val classResolve = sourcePsi.classReference?.multiResolve(false) ?: emptyArray()
    return classResolve.asIterable()
  }
}

@ApiStatus.Internal
class JavaArrayInitializerUCallExpression(
  override val sourcePsi: PsiArrayInitializerExpression,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UCallExpression, UMultiResolvable {
  private var valueArgumentCountLazy = Int.MIN_VALUE
  private val valueArgumentsPart = UastLazyPart<List<UExpression>>()

  override val methodIdentifier: UIdentifier?
    get() = null

  override val classReference: UReferenceExpression?
    get() = null

  override val methodName: String?
    get() = null

  override val valueArgumentCount: Int
    get() {
      if (valueArgumentCountLazy == Int.MIN_VALUE) {
        valueArgumentCountLazy = sourcePsi.initializers.size
      }

      return valueArgumentCountLazy
    }

  override val valueArguments: List<UExpression>
    get() = valueArgumentsPart.getOrBuild { sourcePsi.initializers.map { JavaConverter.convertOrEmpty(it, this) } }

  override fun getArgumentForParameter(i: Int): UExpression? = valueArguments.getOrNull(i)

  override val typeArgumentCount: Int
    get() = 0

  override val typeArguments: List<PsiType>
    get() = emptyList()

  override val returnType: PsiType?
    get() = sourcePsi.type

  override val kind: UastCallKind
    get() = UastCallKind.NESTED_ARRAY_INITIALIZER

  override fun resolve(): Nothing? = null
  override fun multiResolve(): Iterable<ResolveResult> = emptyList()

  override val receiver: UExpression?
    get() = null

  override val receiverType: PsiType?
    get() = null
}

@ApiStatus.Internal
class JavaAnnotationArrayInitializerUCallExpression(
  override val sourcePsi: PsiArrayInitializerMemberValue,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UCallExpression, UMultiResolvable {
  private val valueArgumentsPart = UastLazyPart<List<UExpression>>()

  override fun getArgumentForParameter(i: Int): UExpression? = valueArguments.getOrNull(i)

  override val kind: UastCallKind
    get() = UastCallKind.NESTED_ARRAY_INITIALIZER

  override val methodIdentifier: UIdentifier?
    get() = null

  override val classReference: UReferenceExpression?
    get() = null

  override val methodName: String?
    get() = null

  private var valueArgumentCountLazy = Int.MIN_VALUE

  override val valueArgumentCount: Int
    get() {
      if (valueArgumentCountLazy == Int.MIN_VALUE) {
        valueArgumentCountLazy = sourcePsi.initializers.size
      }
      return valueArgumentCountLazy
    }

  override val valueArguments: List<UExpression>
    get() = valueArgumentsPart.getOrBuild {
      sourcePsi.initializers.map {
        JavaConverter.convertPsiElement(it, this, UElement::class.java) as? UExpression ?: UnknownJavaExpression(it, this)
      }
    }

  override val typeArgumentCount: Int
    get() = 0

  override val typeArguments: List<PsiType>
    get() = emptyList()

  override val returnType: PsiType?
    get() = null

  override fun resolve(): Nothing? = null
  override fun multiResolve(): Iterable<ResolveResult> = emptyList()

  override val receiver: UExpression?
    get() = null

  override val receiverType: PsiType?
    get() = null
}
