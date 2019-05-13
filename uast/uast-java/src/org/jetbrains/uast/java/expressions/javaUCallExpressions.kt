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
import org.jetbrains.uast.*
import org.jetbrains.uast.java.expressions.JavaUExpressionList
import org.jetbrains.uast.java.internal.PsiArrayToUElementListMappingView
import org.jetbrains.uast.psi.UElementWithLocation

class JavaUCallExpression(
  override val psi: PsiMethodCallExpression,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UCallExpressionEx, UElementWithLocation, UMultiResolvable {
  override val kind: UastCallKind
    get() = UastCallKind.METHOD_CALL

  override val methodIdentifier: UIdentifier? by lz {
    val methodExpression = psi.methodExpression
    val nameElement = methodExpression.referenceNameElement ?: return@lz null
    UIdentifier(nameElement, this)
  }

  override val classReference: UReferenceExpression?
    get() = null

  override val valueArgumentCount: Int
    get() = psi.argumentList.expressionCount

  override val valueArguments: List<UExpression> by lz {
    PsiArrayToUElementListMappingView(psi.argumentList.expressions) { JavaConverter.convertOrEmpty(it, this@JavaUCallExpression) }
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

  override val typeArgumentCount: Int by lz { psi.typeArguments.size }

  override val typeArguments: List<PsiType>
    get() = psi.typeArguments.toList()

  override val returnType: PsiType?
    get() = psi.type

  override val methodName: String?
    get() = psi.methodExpression.referenceName

  override fun resolve(): PsiMethod? = psi.resolveMethod()
  override fun multiResolve(): Iterable<ResolveResult> =
    psi.methodExpression.multiResolve(false).asIterable()

  override fun getStartOffset(): Int =
    psi.methodExpression.referenceNameElement?.textOffset ?: psi.methodExpression.textOffset

  override fun getEndOffset(): Int = psi.textRange.endOffset

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
      val qualifierType = psi.methodExpression.qualifierExpression?.type
      if (qualifierType != null) {
        return qualifierType
      }

      val method = resolve() ?: return null
      if (method.hasModifierProperty(PsiModifier.STATIC)) return null

      val psiManager = psi.manager
      val containingClassForMethod = method.containingClass ?: return null

      val containingClass = PsiTreeUtil.getParentOfType(psi, PsiClass::class.java)
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

class JavaConstructorUCallExpression(
  override val psi: PsiNewExpression,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UCallExpressionEx, UMultiResolvable {
  override val kind: UastCallKind by lz {
    when {
      psi.arrayInitializer != null -> UastCallKind.NEW_ARRAY_WITH_INITIALIZER
      psi.arrayDimensions.isNotEmpty() -> UastCallKind.NEW_ARRAY_WITH_DIMENSIONS
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
    psi.classReference?.let { ref ->
      JavaConverter.convertReference(ref, this) as? UReferenceExpression
    }
  }

  override val valueArgumentCount: Int
    get() {
      val initializer = psi.arrayInitializer
      return when {
        initializer != null -> initializer.initializers.size
        psi.arrayDimensions.isNotEmpty() -> psi.arrayDimensions.size
        else -> psi.argumentList?.expressions?.size ?: 0
      }
    }

  override val valueArguments: List<UExpression> by lz {
    val initializer = psi.arrayInitializer
    when {
      initializer != null -> initializer.initializers.map { JavaConverter.convertOrEmpty(it, this) }
      psi.arrayDimensions.isNotEmpty() -> psi.arrayDimensions.map { JavaConverter.convertOrEmpty(it, this) }
      else -> psi.argumentList?.expressions?.map { JavaConverter.convertOrEmpty(it, this) } ?: emptyList()
    }
  }

  override fun getArgumentForParameter(i: Int): UExpression? = valueArguments.getOrNull(i)

  override val typeArgumentCount: Int by lz { psi.classReference?.typeParameters?.size ?: 0 }

  override val typeArguments: List<PsiType>
    get() = psi.classReference?.typeParameters?.toList() ?: emptyList()

  override val returnType: PsiType?
    get() = (psi.classReference?.resolve() as? PsiClass)?.let { PsiTypesUtil.getClassType(it) } ?: psi.type

  override val methodName: String?
    get() = null

  override fun resolve(): PsiMethod? = psi.resolveMethod()
  override fun multiResolve(): Iterable<ResolveResult> =
    psi.classReference?.multiResolve(false)?.asIterable() ?: emptyList()
}

class JavaArrayInitializerUCallExpression(
  override val psi: PsiArrayInitializerExpression,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UCallExpressionEx, UMultiResolvable {
  override val methodIdentifier: UIdentifier?
    get() = null

  override val classReference: UReferenceExpression?
    get() = null

  override val methodName: String?
    get() = null

  override val valueArgumentCount: Int by lz { psi.initializers.size }
  override val valueArguments: List<UExpression> by lz { psi.initializers.map { JavaConverter.convertOrEmpty(it, this) } }

  override fun getArgumentForParameter(i: Int): UExpression? = valueArguments.getOrNull(i)

  override val typeArgumentCount: Int
    get() = 0

  override val typeArguments: List<PsiType>
    get() = emptyList()

  override val returnType: PsiType?
    get() = psi.type

  override val kind: UastCallKind
    get() = UastCallKind.NESTED_ARRAY_INITIALIZER

  override fun resolve(): Nothing? = null
  override fun multiResolve(): Iterable<ResolveResult> = emptyList()

  override val receiver: UExpression?
    get() = null

  override val receiverType: PsiType?
    get() = null
}

class JavaAnnotationArrayInitializerUCallExpression(
  override val psi: PsiArrayInitializerMemberValue,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UCallExpressionEx, UMultiResolvable {

  override fun getArgumentForParameter(i: Int): UExpression? = valueArguments.getOrNull(i)

  override val kind: UastCallKind
    get() = UastCallKind.NESTED_ARRAY_INITIALIZER

  override val methodIdentifier: UIdentifier?
    get() = null

  override val classReference: UReferenceExpression?
    get() = null

  override val methodName: String?
    get() = null

  override val valueArgumentCount: Int by lz { psi.initializers.size }

  override val valueArguments: List<UExpression> by lz {
    psi.initializers.map {
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