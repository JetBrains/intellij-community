// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.uast.java

import com.intellij.psi.*
import com.intellij.psi.impl.light.LightMethodBuilder
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.util.SmartList
import com.intellij.util.asSafely
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

  override val uastDeclarations: List<UDeclaration> by lz {
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

  internal var cachedSuperTypes: List<UTypeReferenceExpression>? = null
  override val uastSuperTypes: List<UTypeReferenceExpression>
    get() {
      var types = cachedSuperTypes
      if (types == null) {
        types = javaPsi.extendsList?.referenceElements?.map { createJavaUTypeReferenceExpression(it) }.orEmpty() +
                javaPsi.implementsList?.referenceElements?.map { createJavaUTypeReferenceExpression(it) }.orEmpty()
        cachedSuperTypes = types
      }
      return types
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
  givenParent: UElement?
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

  override val uastSuperTypes: List<UTypeReferenceExpression>
    get() {
      var types = cachedSuperTypes
      if (types == null) {
        types = listOf(createJavaUTypeReferenceExpression(sourcePsi.baseClassReference)) +
                javaPsi.extendsList?.referenceElements?.map { createJavaUTypeReferenceExpression(it) }.orEmpty() +
                javaPsi.implementsList?.referenceElements?.map { createJavaUTypeReferenceExpression(it) }.orEmpty()
        cachedSuperTypes = types
      }
      return types
    }

  override fun convertParent(): UElement? = sourcePsi.parent.toUElementOfType<UObjectLiteralExpression>() ?: super.convertParent()

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

  private val fakeConstructor: JavaUMethod? by lz {
    val psiClass = this.javaPsi
    val physicalNewExpression = psiClass.parent.asSafely<PsiNewExpression>() ?: return@lz null
    val superConstructor = physicalNewExpression.resolveMethod()
    val lightMethodBuilder = object : LightMethodBuilder(psiClass.manager, psiClass.language, "<anon-init>") {
      init {
        containingClass = psiClass
        isConstructor = true
      }

      override fun getNavigationElement(): PsiElement =
        superConstructor?.navigationElement ?: psiClass.superClass?.navigationElement ?: super.getNavigationElement()
      override fun getParent(): PsiElement = psiClass
      override fun getModifierList(): PsiModifierList = superConstructor?.modifierList ?: super.getModifierList()
      override fun getParameterList(): PsiParameterList = superConstructor?.parameterList ?: super.getParameterList()
      override fun getDocComment(): PsiDocComment? = superConstructor?.docComment ?: super.getDocComment()
    }

    JavaUMethod(lightMethodBuilder, this@JavaUAnonymousClass)
  }

  override fun getMethods(): Array<UMethod> {
    val constructor = fakeConstructor ?: return super<AbstractJavaUClass>.getMethods()
    val uMethods = SmartList<UMethod>()
    uMethods.add(constructor)
    uMethods.addAll(super<AbstractJavaUClass>.getMethods())
    return uMethods.toTypedArray()
  }

  override fun getInnerClasses(): Array<UClass> = super<AbstractJavaUClass>.getInnerClasses()
  override fun getOriginalElement(): PsiElement? = sourcePsi.originalElement
}
