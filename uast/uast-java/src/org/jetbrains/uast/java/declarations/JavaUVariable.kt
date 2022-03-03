// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.java

import com.intellij.psi.*
import com.intellij.psi.impl.light.LightRecordCanonicalConstructor.LightRecordConstructorParameter
import com.intellij.psi.impl.light.LightRecordField
import com.intellij.psi.impl.source.PsiParameterImpl
import com.intellij.psi.impl.source.tree.java.PsiLocalVariableImpl
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.psi.util.parentOfType
import com.intellij.util.castSafelyTo
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*
import org.jetbrains.uast.internal.accommodate
import org.jetbrains.uast.internal.alternative
import org.jetbrains.uast.java.internal.JavaUElementWithComments

@ApiStatus.Internal
abstract class AbstractJavaUVariable(
  givenParent: UElement?
) : JavaAbstractUElement(givenParent), PsiVariable, UVariableEx, JavaUElementWithComments, UAnchorOwner {

  abstract override val javaPsi: PsiVariable

  @Suppress("OverridingDeprecatedMember")
  override val psi
    get() = javaPsi

  override val uastInitializer: UExpression? by lz {
    val initializer = javaPsi.initializer ?: return@lz null
    UastFacade.findPlugin(initializer)?.convertElement(initializer, this) as? UExpression
  }

  override val uAnnotations: List<UAnnotation> by lz { javaPsi.annotations.map { JavaUAnnotation(it, this) } }
  override val typeReference: UTypeReferenceExpression? by lz {
    javaPsi.typeElement?.let { UastFacade.findPlugin(it)?.convertOpt<UTypeReferenceExpression>(javaPsi.typeElement, this) }
  }

  abstract override val sourcePsi: PsiVariable?

  override val uastAnchor: UIdentifier?
    get() = sourcePsi?.let {  UIdentifier(it.nameIdentifier, this) }

  override fun equals(other: Any?): Boolean = other is AbstractJavaUVariable && javaPsi == other.javaPsi
  override fun hashCode(): Int = javaPsi.hashCode()
}

@ApiStatus.Internal
class JavaUVariable(
  override val javaPsi: PsiVariable,
  givenParent: UElement?
) : AbstractJavaUVariable(givenParent), UVariableEx, PsiVariable by javaPsi {
  @Suppress("OverridingDeprecatedMember")
  override val psi: PsiVariable
    get() = javaPsi

  override val sourcePsi: PsiVariable? get() = javaPsi.takeIf { it.isPhysical || it is PsiLocalVariableImpl}

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
  override fun getOriginalElement(): PsiElement? = javaPsi.originalElement
}

@ApiStatus.Internal
class JavaUParameter(
  override val javaPsi: PsiParameter,
  givenParent: UElement?
) : AbstractJavaUVariable(givenParent), UParameterEx, PsiParameter by javaPsi {

  @Suppress("OverridingDeprecatedMember")
  override val psi: PsiParameter
    get() = javaPsi

  override val sourcePsi: PsiParameter?
    get() = javaPsi.takeIf { it.isPhysical || (it is PsiParameterImpl && it.parentOfType<PsiMethod>()?.let { canBeSourcePsi(it) } == true) }

  override fun getOriginalElement(): PsiElement? = javaPsi.originalElement
}

private class JavaRecordUParameter(
  override val sourcePsi: PsiRecordComponent,
  override val javaPsi: PsiParameter,
  givenParent: UElement?
) : AbstractJavaUVariable(givenParent), UParameterEx, PsiParameter by javaPsi {

  @Suppress("OverridingDeprecatedMember")
  override val psi: PsiParameter
    get() = javaPsi

  override val uastAnchor: UIdentifier
    get() = UIdentifier(sourcePsi.nameIdentifier, this)

  override fun getPsiParentForLazyConversion(): PsiElement? = javaPsi.context
}

internal fun convertRecordConstructorParameterAlternatives(
  element: PsiElement,
  givenParent: UElement?,
  expectedTypes: Array<out Class<out UElement>>
): Sequence<UVariable> {

  val (psiRecordComponent, lightRecordField, lightConstructorParameter) = when (element) {
    is PsiRecordComponent -> Triple(element, null, null)
    is LightRecordConstructorParameter -> {
      val lightRecordField = element.parentOfType<PsiMethod>()?.containingClass?.findFieldByName(element.name, false)
        ?.castSafelyTo<LightRecordField>() ?: return emptySequence()
      Triple(lightRecordField.recordComponent, lightRecordField, element)
    }
    is LightRecordField -> Triple(element.recordComponent, element, null)
    else -> return emptySequence()
  }
  
  val paramAlternative = alternative {
    val psiClass = psiRecordComponent.containingClass ?: return@alternative null
    val jvmParameter = lightConstructorParameter ?: psiClass.constructors.asSequence()
      .filter { !it.isPhysical }
      .flatMap { it.parameterList.parameters.asSequence() }.firstOrNull { it.name == psiRecordComponent.name }
    JavaRecordUParameter(psiRecordComponent, jvmParameter ?: return@alternative null, givenParent)
  }
  val fieldAlternative = alternative {
    val psiField = lightRecordField ?: psiRecordComponent.containingClass?.findFieldByName(psiRecordComponent.name, false)
                   ?: return@alternative null
    JavaRecordUField(psiRecordComponent, psiField, givenParent)
  }
  
  return when (element) {
    is LightRecordField -> expectedTypes.accommodate(fieldAlternative, paramAlternative)
    else -> expectedTypes.accommodate(paramAlternative, fieldAlternative)
  }
}

@ApiStatus.Internal
class JavaUField(
  override val sourcePsi: PsiField,
  givenParent: UElement?
) : AbstractJavaUVariable(givenParent), UFieldEx, PsiField by sourcePsi {
  @Suppress("OverridingDeprecatedMember")
  override val psi: PsiField
    get() = javaPsi

  override val javaPsi: PsiField = unwrap<UField, PsiField>(sourcePsi)
  override fun getOriginalElement(): PsiElement? = sourcePsi.originalElement
}

private class JavaRecordUField(
  private val psiRecord: PsiRecordComponent,
  override val javaPsi: PsiField,
  givenParent: UElement?
) : AbstractJavaUVariable(givenParent), UFieldEx, PsiField by javaPsi {

  @Suppress("OverridingDeprecatedMember")
  override val psi: PsiField
    get() = javaPsi

  override val sourcePsi: PsiVariable?
    get() = null

  override val uastAnchor: UIdentifier
    get() = UIdentifier(psiRecord.nameIdentifier, this)

  override fun getPsiParentForLazyConversion(): PsiElement? = javaPsi.context
}

@ApiStatus.Internal
class JavaULocalVariable(
  override val sourcePsi: PsiLocalVariable,
  givenParent: UElement?
) : AbstractJavaUVariable(givenParent), ULocalVariableEx, PsiLocalVariable by sourcePsi {

  @Suppress("OverridingDeprecatedMember")
  override val psi: PsiLocalVariable
    get() = javaPsi

  override val javaPsi: PsiLocalVariable = unwrap<ULocalVariable, PsiLocalVariable>(sourcePsi)

  override fun getPsiParentForLazyConversion(): PsiElement? = super.getPsiParentForLazyConversion()?.let {
    when (it) {
      is PsiResourceList -> it.parent
      else -> it
    }
  }

  override fun getOriginalElement(): PsiElement? = sourcePsi.originalElement

}

@ApiStatus.Internal
class JavaUEnumConstant(
  override val sourcePsi: PsiEnumConstant,
  givenParent: UElement?
) : AbstractJavaUVariable(givenParent), UEnumConstantEx, UCallExpression, PsiEnumConstant by sourcePsi, UMultiResolvable {
  override val initializingClass: UClass? by lz { UastFacade.findPlugin(sourcePsi)?.convertOpt(sourcePsi.initializingClass, this) }

  @Suppress("OverridingDeprecatedMember")
  override val psi: PsiEnumConstant
    get() = javaPsi

  override val javaPsi: PsiEnumConstant get() = sourcePsi

  override val kind: UastCallKind
    get() = UastCallKind.CONSTRUCTOR_CALL
  override val receiver: UExpression?
    get() = null
  override val receiverType: PsiType?
    get() = null
  override val methodIdentifier: UIdentifier?
    get() = null
  override val classReference: UReferenceExpression?
    get() = JavaEnumConstantClassReference(sourcePsi, this)
  override val typeArgumentCount: Int
    get() = 0
  override val typeArguments: List<PsiType>
    get() = emptyList()
  override val valueArgumentCount: Int
    get() = sourcePsi.argumentList?.expressions?.size ?: 0

  override val valueArguments: List<UExpression> by lz {
    sourcePsi.argumentList?.expressions?.map {
      UastFacade.findPlugin(it)?.convertElement(it, this) as? UExpression ?: UastEmptyExpression(this)
    } ?: emptyList()
  }

  override fun getArgumentForParameter(i: Int): UExpression? = valueArguments.getOrNull(i)

  override val returnType: PsiType?
    get() = sourcePsi.type

  override fun resolve(): PsiMethod? = sourcePsi.resolveMethod()

  override fun multiResolve(): Iterable<ResolveResult> =
    listOfNotNull(sourcePsi.resolveMethodGenerics())

  override val methodName: String?
    get() = null

  private class JavaEnumConstantClassReference(
    override val sourcePsi: PsiEnumConstant,
    givenParent: UElement?
  ) : JavaAbstractUExpression(givenParent), USimpleNameReferenceExpression, UMultiResolvable {
    override fun resolve() = sourcePsi.containingClass
    override fun multiResolve(): Iterable<ResolveResult> =
      listOfNotNull(resolve()?.let { PsiTypesUtil.getClassType(it).resolveGenerics() })

    override val resolvedName: String?
      get() = sourcePsi.containingClass?.name

    override val identifier: String
      get() = sourcePsi.containingClass?.name ?: "<error>"
  }

  override fun getOriginalElement(): PsiElement? = sourcePsi.originalElement
}
