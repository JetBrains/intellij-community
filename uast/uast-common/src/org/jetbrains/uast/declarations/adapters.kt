// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("DELEGATED_MEMBER_HIDES_SUPERTYPE_OVERRIDE", "unused")

// reason: problem methods are deprecated so should not been mentioned explicitly

package org.jetbrains.uast

import com.intellij.psi.*

/*
 * Mocks of the UAST declarations interfaces.
 *
 * Can be useful for UAST plugins written in Kotlin and may be the only way to implement
 * needed interfaces in other JVM-languages such as Scala, where JVM clashes happen
 * when trying to inherit from some UAST interfaces.
 *
 * Provides:
 *  - Elimination of some possible JVM clashes
 *  - Inherited default implementations from UAST interfaces
 *  - Kotlin delegation mechanism which helps implement PSI interfaces by some delegate
 */

abstract class UAnnotationAdapter : UAnnotation

abstract class UClassAdapter(psiClass: PsiClass) : UClass, PsiClass by psiClass {

  @Suppress("DEPRECATION") // unavoidable because of delegation, will be removed together with an interface method
  @Deprecated("will return null if existing superclass is not convertable to Uast, use `javaPsi.superClass` instead",
              ReplaceWith("javaPsi.superClass"))
  override fun getSuperClass(): UClass? = super.getSuperClass()

  /**
   * To eliminate JVM clashes subclasses should override getU*** methods.
   * @see getUFields
   * @see getUInitializers
   * @see getUInnerClasses
   * @see getUMethods
   */
  final override fun getFields(): Array<UField> = getUFields()

  final override fun getInitializers(): Array<UClassInitializer> = getUInitializers()

  final override fun getInnerClasses(): Array<UClass> = getUInnerClasses()

  final override fun getMethods(): Array<UMethod> = getUMethods()

  open fun getUFields(): Array<UField> = super.getFields()

  open fun getUInitializers(): Array<UClassInitializer> = super.getInitializers()

  open fun getUInnerClasses(): Array<UClass> = super.getInnerClasses()

  open fun getUMethods(): Array<UMethod> = super.getMethods()

  override fun getOriginalElement(): PsiElement? = javaPsi

  override fun getSourceElement(): PsiElement? = sourcePsi
}


abstract class UAnonymousClassAdapter(psiAnonymousClass: PsiAnonymousClass)
  : UAnonymousClass, PsiAnonymousClass by psiAnonymousClass {

  @Suppress("DEPRECATION") // unavoidable because of delegation, will be removed together with an interface method
  @Deprecated("will return null if existing superclass is not convertable to Uast, use `javaPsi.superClass` instead",
              ReplaceWith("javaPsi.superClass"))
  override fun getSuperClass(): UClass? = super.getSuperClass()

  /**
   * To eliminate JVM clashes subclasses should override getU*** methods.
   * @see getUFields
   * @see getUInitializers
   * @see getUInnerClasses
   * @see getUMethods
   */
  final override fun getFields(): Array<UField> = getUFields()

  final override fun getInitializers(): Array<UClassInitializer> = getUInitializers()

  final override fun getInnerClasses(): Array<UClass> = getUInnerClasses()

  final override fun getMethods(): Array<UMethod> = getUMethods()

  open fun getUFields(): Array<UField> = super.getFields()

  open fun getUInitializers(): Array<UClassInitializer> = super.getInitializers()

  open fun getUInnerClasses(): Array<UClass> = super.getInnerClasses()

  open fun getUMethods(): Array<UMethod> = super.getMethods()

  override fun getOriginalElement(): PsiElement? = javaPsi

  override fun getSourceElement(): PsiElement? = sourcePsi
}

abstract class UClassInitializerAdapter(psiClassInitializer: PsiClassInitializer)
  : UClassInitializer, PsiClassInitializer by psiClassInitializer {

  override fun getOriginalElement(): PsiElement? = javaPsi

  override fun getSourceElement(): PsiElement? = sourcePsi
}

abstract class UDeclarationAdapter(psiJvmModifiersOwner: PsiJvmModifiersOwner)
  : UDeclaration, PsiJvmModifiersOwner by psiJvmModifiersOwner {

  override fun getOriginalElement(): PsiElement? = javaPsi

  override fun getSourceElement(): PsiElement? = sourcePsi
}

abstract class UAnchorOwnerAdapter : UAnchorOwner

abstract class UFileAdapter : UFile

abstract class UImportStatementAdapter : UImportStatement

abstract class UMethodAdapter(private val psiMethod: PsiMethod) : UMethod, PsiMethod by psiMethod {

  override fun getOriginalElement(): PsiElement? = sourcePsi?.originalElement

  override fun getSourceElement(): PsiElement? = psiMethod.sourceElement
}

abstract class UAnnotationMethodAdapter(private val psiAnnotationMethod: PsiAnnotationMethod)
  : UMethod, PsiAnnotationMethod by psiAnnotationMethod {

  override fun getOriginalElement(): PsiElement? = sourcePsi?.originalElement

  override fun getSourceElement(): PsiElement? = psiAnnotationMethod.sourceElement
}

abstract class UVariableAdapter(private val psiVariable: PsiVariable)
  : UVariable, PsiVariable by psiVariable {

  override fun getOriginalElement(): PsiElement? = sourcePsi?.originalElement

  override fun getSourceElement(): PsiElement? = psiVariable
}

abstract class UParameterAdapter(private val psiParameter: PsiParameter)
  : UParameter, PsiParameter by psiParameter {

  override fun getOriginalElement(): PsiElement? = sourcePsi?.originalElement

  override fun getSourceElement(): PsiElement? = psiParameter.sourceElement
}

abstract class UFieldAdapter(private val psiField: PsiField)
  : UField, PsiField by psiField {

  override fun getOriginalElement(): PsiElement? = sourcePsi?.originalElement

  override fun getSourceElement(): PsiElement? = psiField.sourceElement
}

abstract class ULocalVariableAdapter(psiLocalVariable: PsiLocalVariable)
  : ULocalVariable, PsiLocalVariable by psiLocalVariable {

  override fun getOriginalElement(): PsiElement? = sourcePsi?.originalElement
}


abstract class UEnumConstantAdapter(private val psiEnumConstant: PsiEnumConstant)
  : UEnumConstant, PsiEnumConstant by psiEnumConstant {

  override fun getOriginalElement(): PsiElement? = sourcePsi?.originalElement

  override fun getSourceElement(): PsiElement? = psiEnumConstant.sourceElement
}