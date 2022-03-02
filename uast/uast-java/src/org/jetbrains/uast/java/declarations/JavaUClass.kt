// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.java

import com.intellij.psi.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*
import org.jetbrains.uast.java.internal.JavaUElementWithComments

@ApiStatus.Internal
abstract class AbstractJavaUClass(
  givenParent: UElement?
) : JavaAbstractUElement(givenParent), UClass, JavaUElementWithComments, UAnchorOwner, UDeclarationEx {

  abstract override val javaPsi: PsiClass

  @Suppress("OverridingDeprecatedMember")
  override val psi get() = javaPsi

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
    javaPsi.extendsList?.referenceElements?.map { createJavaUTypeReferenceExpression(it) }.orEmpty() +
    javaPsi.implementsList?.referenceElements?.map { createJavaUTypeReferenceExpression(it) }.orEmpty()
  }

  override val uastAnchor: UIdentifier?
    get() = UIdentifier(javaPsi.nameIdentifier, this)

  override val uAnnotations: List<UAnnotation>
    get() = javaPsi.annotations.map { JavaUAnnotation(it, this) }

  override fun equals(other: Any?): Boolean = other is AbstractJavaUClass && javaPsi == other.javaPsi
  override fun hashCode(): Int = javaPsi.hashCode()
}

@ApiStatus.Internal
class JavaUClass(
  override val sourcePsi: PsiClass,
  val givenParent: UElement?
) : AbstractJavaUClass(givenParent), UAnchorOwner, PsiClass by sourcePsi {

  override val javaPsi: PsiClass = unwrap<UClass, PsiClass>(sourcePsi)

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

  override fun getOriginalElement(): PsiElement? = sourcePsi.originalElement
}

@ApiStatus.Internal
class JavaUAnonymousClass(
  override val sourcePsi: PsiAnonymousClass,
  uastParent: UElement?
) : AbstractJavaUClass(uastParent), UAnonymousClass, UAnchorOwner, PsiAnonymousClass by sourcePsi {

  @Suppress("OverridingDeprecatedMember")
  override val psi: PsiAnonymousClass get() = sourcePsi

  override val javaPsi: PsiAnonymousClass = sourcePsi

  override val uastSuperTypes: List<UTypeReferenceExpression> by lazy {
    listOf(createJavaUTypeReferenceExpression(sourcePsi.baseClassReference)) + super.uastSuperTypes
  }

  override val uastAnchor: UIdentifier? by lazy {
    when (javaPsi) {
      is PsiEnumConstantInitializer ->
        (javaPsi.parent as? PsiEnumConstant)?.let { UIdentifier(it.nameIdentifier, this) }
      else -> UIdentifier(sourcePsi.baseClassReference.referenceNameElement, this)
    }
  }

  override fun getSuperClass(): UClass? = super<AbstractJavaUClass>.getSuperClass()
  override fun getFields(): Array<UField> = super<AbstractJavaUClass>.getFields()
  override fun getInitializers(): Array<UClassInitializer> = super<AbstractJavaUClass>.getInitializers()
  override fun getMethods(): Array<UMethod> = super<AbstractJavaUClass>.getMethods()
  override fun getInnerClasses(): Array<UClass> = super<AbstractJavaUClass>.getInnerClasses()
  override fun getOriginalElement(): PsiElement? = sourcePsi.originalElement
}
