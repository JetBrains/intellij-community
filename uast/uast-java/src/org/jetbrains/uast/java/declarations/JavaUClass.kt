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

abstract class AbstractJavaUClass(givenParent: UElement?) : JavaAbstractUElement(
  givenParent), UClassTypeSpecific, JavaUElementWithComments, UAnchorOwner {

  abstract override val javaPsi: PsiClass

  @Suppress("unused") // Used in Kotlin, to be removed in 2018.1
  @Deprecated("use AbstractJavaUClass(givenParent)", ReplaceWith("AbstractJavaUClass(givenParent)"))
  constructor() : this(null)

  override val uastDeclarations: MutableList<UDeclaration> by lz {
    mutableListOf<UDeclaration>().apply {
      addAll(fields)
      addAll(initializers)
      addAll(methods)
      addAll(innerClasses)
    }
  }

  protected fun createJavaUTypeReferenceExpression(referenceElement: PsiJavaCodeReferenceElement): LazyJavaUTypeReferenceExpression =
    LazyJavaUTypeReferenceExpression(referenceElement, this) {
      JavaPsiFacade.getElementFactory(referenceElement.project).createType(referenceElement)
    }

  override val uastSuperTypes: List<UTypeReferenceExpression> by lazy {
    psi.extendsList?.referenceElements?.map { createJavaUTypeReferenceExpression(it) }.orEmpty() +
    psi.implementsList?.referenceElements?.map { createJavaUTypeReferenceExpression(it) }.orEmpty()
  }

  override val uastAnchor: UIdentifier?
    get() = UIdentifier(psi.nameIdentifier, this)

  override val annotations: List<UAnnotation>
    get() = psi.annotations.map { JavaUAnnotation(it, this) }

  override fun equals(other: Any?): Boolean = other is AbstractJavaUClass && psi == other.psi
  override fun hashCode(): Int = psi.hashCode()
}

class JavaUClass private constructor(psi: PsiClass, val givenParent: UElement?) :
  AbstractJavaUClass(givenParent), UAnchorOwner, PsiClass by psi {

  override val psi: PsiClass
    get() = javaPsi

  override val javaPsi: PsiClass = unwrap<UClass, PsiClass>(psi)

  override fun getSuperClass(): UClass? = super.getSuperClass()
  override fun getFields(): Array<UField> = super.getFields()
  override fun getInitializers(): Array<UClassInitializer> = super.getInitializers()
  override fun getMethods(): Array<UMethod> = super.getMethods()
  override fun getInnerClasses(): Array<UClass> = super.getInnerClasses()

  companion object {
    fun create(psi: PsiClass, containingElement: UElement?): UClass {
      return if (psi is PsiAnonymousClass)
        JavaUAnonymousClass(psi, containingElement)
      else
        JavaUClass(psi, containingElement)
    }
  }
}

class JavaUAnonymousClass(
  psi: PsiAnonymousClass,
  uastParent: UElement?
) : AbstractJavaUClass(uastParent), UAnonymousClass, UAnchorOwner, PsiAnonymousClass by psi {
  override val psi: PsiAnonymousClass
    get() = javaPsi

  override val javaPsi: PsiAnonymousClass = unwrap<UAnonymousClass, PsiAnonymousClass>(psi)

  override val uastSuperTypes: List<UTypeReferenceExpression> by lazy {
    listOf(createJavaUTypeReferenceExpression(psi.baseClassReference)) + super.uastSuperTypes
  }

  override val uastAnchor: UIdentifier? by lazy {
    when (javaPsi) {
      is PsiEnumConstantInitializer ->
        (javaPsi.parent as? PsiEnumConstant)?.let { UIdentifier(it.nameIdentifier, this) }
      else -> UIdentifier(psi.baseClassReference.referenceNameElement, this)
    }
  }

  override fun getSuperClass(): UClass? = super<AbstractJavaUClass>.getSuperClass()
  override fun getFields(): Array<UField> = super<AbstractJavaUClass>.getFields()
  override fun getInitializers(): Array<UClassInitializer> = super<AbstractJavaUClass>.getInitializers()
  override fun getMethods(): Array<UMethod> = super<AbstractJavaUClass>.getMethods()
  override fun getInnerClasses(): Array<UClass> = super<AbstractJavaUClass>.getInnerClasses()
}
