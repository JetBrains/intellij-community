/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.uast.java

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
  override val kind: UastCallKind
    get() {
      val element = nameReferenceElement
      if (element is PsiKeyword && (element.text == PsiKeyword.THIS || element.text == PsiKeyword.SUPER))
        return UastCallKind.CONSTRUCTOR_CALL

      return UastCallKind.METHOD_CALL
    }

  override val methodIdentifier: UIdentifier? by lz {
    nameReferenceElement?.let { UIdentifier(it, this) }
  }

  private val nameReferenceElement: PsiElement?
    get() = sourcePsi.methodExpression.referenceNameElement

  override val classReference: UReferenceExpression?
    get() = null

  override val valueArgumentCount: Int
    get() = sourcePsi.argumentList.expressionCount

  override val valueArguments: List<UExpression> by lz {
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

  override val typeArgumentCount: Int by lz { sourcePsi.typeArguments.size }

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
  override val kind: UastCallKind by lz {
    when {
      sourcePsi.arrayInitializer != null -> UastCallKind.NEW_ARRAY_WITH_INITIALIZER
      sourcePsi.arrayDimensions.isNotEmpty() -> UastCallKind.NEW_ARRAY_WITH_DIMENSIONS
      else -> UastCallKind.CONSTRUCTOR_CALL
    }
  }

  override val receiver: UExpression?
    get() = null

  override val receiverType: PsiType?
    get() = null

  override val methodIdentifier: UIdentifier?
    get() = null

  override val classReference: UReferenceExpression? by lz {
    sourcePsi.classReference?.let { ref ->
      JavaConverter.convertReference(ref, this) as? UReferenceExpression
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

  override val valueArguments: List<UExpression> by lz {
    val initializer = sourcePsi.arrayInitializer
    when {
      initializer != null -> initializer.initializers.map { JavaConverter.convertOrEmpty(it, this) }
      sourcePsi.arrayDimensions.isNotEmpty() -> sourcePsi.arrayDimensions.map { JavaConverter.convertOrEmpty(it, this) }
      else -> sourcePsi.argumentList?.expressions?.map { JavaConverter.convertOrEmpty(it, this) } ?: emptyList()
    }
  }

  override fun getArgumentForParameter(i: Int): UExpression? = valueArguments.getOrNull(i)

  override val typeArgumentCount: Int by lz { sourcePsi.classReference?.typeParameters?.size ?: 0 }

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
  override val methodIdentifier: UIdentifier?
    get() = null

  override val classReference: UReferenceExpression?
    get() = null

  override val methodName: String?
    get() = null

  override val valueArgumentCount: Int by lz { sourcePsi.initializers.size }
  override val valueArguments: List<UExpression> by lz { sourcePsi.initializers.map { JavaConverter.convertOrEmpty(it, this) } }

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

  override fun getArgumentForParameter(i: Int): UExpression? = valueArguments.getOrNull(i)

  override val kind: UastCallKind
    get() = UastCallKind.NESTED_ARRAY_INITIALIZER

  override val methodIdentifier: UIdentifier?
    get() = null

  override val classReference: UReferenceExpression?
    get() = null

  override val methodName: String?
    get() = null

  override val valueArgumentCount: Int by lz { sourcePsi.initializers.size }

  override val valueArguments: List<UExpression> by lz {
    sourcePsi.initializers.map {
      JavaConverter.convertPsiElement(it, this) as? UExpression ?: UnknownJavaExpression(it, this)
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
