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
import org.jetbrains.uast.*
import org.jetbrains.uast.java.internal.JavaUElementWithComments

abstract class AbstractJavaUVariable(givenParent: UElement?) : JavaAbstractUElement(
  givenParent), PsiVariable, UVariableEx, JavaUElementWithComments, UAnchorOwner {

  abstract override val javaPsi: PsiVariable

  @Suppress("unused") // Used in Kotlin 1.1.4, to be removed in 2018.1
  @Deprecated("use AbstractJavaUVariable(givenParent) instead", ReplaceWith("AbstractJavaUVariable(givenParent)"))
  constructor() : this(null)

  override val uastInitializer: UExpression? by lz {
    val initializer = psi.initializer ?: return@lz null
    getLanguagePlugin().convertElement(initializer, this) as? UExpression
  }

  override val annotations: List<JavaUAnnotation> by lz { psi.annotations.map { JavaUAnnotation(it, this) } }
  override val typeReference: UTypeReferenceExpression? by lz { getLanguagePlugin().convertOpt<UTypeReferenceExpression>(psi.typeElement, this) }

  override val uastAnchor: UIdentifier
    get() = UIdentifier(psi.nameIdentifier, this)

  override fun equals(other: Any?): Boolean = other is AbstractJavaUVariable && psi == other.psi
  override fun hashCode(): Int = psi.hashCode()
}

open class JavaUVariable(
  psi: PsiVariable,
  givenParent: UElement?
) : AbstractJavaUVariable(givenParent), UVariableEx, PsiVariable by psi {
  override val psi: PsiVariable
    get() = javaPsi

  override val javaPsi: PsiVariable = unwrap<UVariable, PsiVariable>(psi)

  companion object {
    fun create(psi: PsiVariable, containingElement: UElement?): UVariable {
      return when (psi) {
        is PsiEnumConstant -> JavaUEnumConstant(psi, containingElement)
        is PsiLocalVariable -> JavaULocalVariable(psi, containingElement)
        is PsiParameter -> JavaUParameter(psi, containingElement)
        is PsiField -> JavaUField(psi, containingElement)
        else -> JavaUVariable(psi, containingElement)
      }
    }
  }
}

open class JavaUParameter(
  psi: PsiParameter,
  givenParent: UElement?
) : AbstractJavaUVariable(givenParent), UParameterEx, PsiParameter by psi {
  override val psi: PsiParameter
    get() = javaPsi

  override val javaPsi: PsiParameter = unwrap<UParameter, PsiParameter>(psi)
}

open class JavaUField(
  psi: PsiField,
  givenParent: UElement?
) : AbstractJavaUVariable(givenParent), UFieldEx, PsiField by psi {
  override val psi: PsiField
    get() = javaPsi

  override val javaPsi: PsiField = unwrap<UField, PsiField>(psi)
}

open class JavaULocalVariable(
  psi: PsiLocalVariable,
  givenParent: UElement?
) : AbstractJavaUVariable(givenParent), ULocalVariableEx, PsiLocalVariable by psi {
  override val psi: PsiLocalVariable
    get() = javaPsi

  override val javaPsi: PsiLocalVariable = unwrap<ULocalVariable, PsiLocalVariable>(psi)

  override fun getPsiParentForLazyConversion(): PsiElement? = super.getPsiParentForLazyConversion()?.let {
    when (it) {
      is PsiResourceList -> it.parent
      else -> it
    }
  }

}

open class JavaUEnumConstant(
  psi: PsiEnumConstant,
  givenParent: UElement?
) : AbstractJavaUVariable(givenParent), UEnumConstantEx, UCallExpressionEx, PsiEnumConstant by psi {
  override val initializingClass: UClass? by lz { getLanguagePlugin().convertOpt<UClass>(psi.initializingClass, this) }

  override val psi: PsiEnumConstant
    get() = javaPsi

  override val javaPsi: PsiEnumConstant = unwrap<UEnumConstant, PsiEnumConstant>(psi)

  override val kind: UastCallKind
    get() = UastCallKind.CONSTRUCTOR_CALL
  override val receiver: UExpression?
    get() = null
  override val receiverType: PsiType?
    get() = null
  override val methodIdentifier: UIdentifier?
    get() = null
  override val classReference: UReferenceExpression?
    get() = JavaEnumConstantClassReference(psi, this)
  override val typeArgumentCount: Int
    get() = 0
  override val typeArguments: List<PsiType>
    get() = emptyList()
  override val valueArgumentCount: Int
    get() = psi.argumentList?.expressions?.size ?: 0

  override val valueArguments: List<UExpression> by lz {
    psi.argumentList?.expressions?.map {
      getLanguagePlugin().convertElement(it, this) as? UExpression ?: UastEmptyExpression(this)
    } ?: emptyList()
  }

  override fun getArgumentForParameter(i: Int): UExpression? = valueArguments.getOrNull(i)

  override val returnType: PsiType?
    get() = psi.type

  override fun resolve(): PsiMethod? = psi.resolveMethod()

  override val methodName: String?
    get() = null

  private class JavaEnumConstantClassReference(
    override val psi: PsiEnumConstant,
    givenParent: UElement?
  ) : JavaAbstractUExpression(givenParent), USimpleNameReferenceExpression {
    override fun resolve() = psi.containingClass
    override val resolvedName: String?
      get() = psi.containingClass?.name
    override val identifier: String
      get() = psi.containingClass?.name ?: "<error>"
  }
}