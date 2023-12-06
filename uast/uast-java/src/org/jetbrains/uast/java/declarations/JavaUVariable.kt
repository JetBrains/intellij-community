// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.uast.java

import com.intellij.psi.*
import com.intellij.psi.impl.light.LightElement
import com.intellij.psi.impl.light.LightRecordCanonicalConstructor.LightRecordConstructorParameter
import com.intellij.psi.impl.light.LightRecordField
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiTypesUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*
import org.jetbrains.uast.internal.CONVERSION_LOGGER
import org.jetbrains.uast.internal.UElementAlternative
import org.jetbrains.uast.internal.accommodate
import org.jetbrains.uast.internal.alternative
import org.jetbrains.uast.java.internal.JavaUElementWithComments

@ApiStatus.Internal
abstract class AbstractJavaUVariable(
  givenParent: UElement?
) : JavaAbstractUElement(givenParent), PsiVariable, UVariableEx, JavaUElementWithComments, UAnchorOwner {

  private var uastInitializerPart: Any? = UNINITIALIZED_UAST_PART
  private var uAnnotationsPart: Any? = UNINITIALIZED_UAST_PART
  private var typeReferencePart: Any? = UNINITIALIZED_UAST_PART

  abstract override val javaPsi: PsiVariable

  @Suppress("OverridingDeprecatedMember")
  override val psi: PsiVariable
    get() = javaPsi

  override val uastInitializer: UExpression?
    get() {
      if (uastInitializerPart == UNINITIALIZED_UAST_PART) {
        uastInitializerPart = javaPsi.initializer
          ?.let { initializer -> UastFacade.findPlugin(initializer)?.convertElement(initializer, this) as? UExpression }
      }

      return uastInitializerPart as UExpression?
    }

  @Suppress("UNCHECKED_CAST")
  override val uAnnotations: List<UAnnotation>
    get() {
      if (uAnnotationsPart == UNINITIALIZED_UAST_PART) {
        uAnnotationsPart = javaPsi.annotations.map { JavaUAnnotation(it, this) }
      }

      return uAnnotationsPart as List<UAnnotation>
    }

  override val typeReference: UTypeReferenceExpression?
    get() {
      if (typeReferencePart == UNINITIALIZED_UAST_PART) {
        typeReferencePart = javaPsi.typeElement?.let {
          UastFacade.findPlugin(it)?.convertOpt<UTypeReferenceExpression>(javaPsi.typeElement, this)
        }
      }
      return typeReferencePart as UTypeReferenceExpression?
    }

  abstract override val sourcePsi: PsiVariable?

  override val uastAnchor: UIdentifier?
    get() = sourcePsi?.let { UIdentifier(it.nameIdentifier, this) }

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

  override val sourcePsi: PsiVariable? get() = javaPsi.takeIf { it !is LightElement }

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
    get() = javaPsi.takeIf { it !is LightElement }

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

internal fun convertRecordConstructorParameterAlternatives(element: PsiElement,
                                                           givenParent: UElement?,
                                                           expectedTypes: Array<out Class<out UElement>>): Sequence<UVariable> {
  val (paramAlternative, fieldAlternative) = createAlternatives(element, givenParent) ?: return emptySequence()

  return when (element) {
    is LightRecordField -> expectedTypes.accommodate(fieldAlternative, paramAlternative)
    else -> expectedTypes.accommodate(paramAlternative, fieldAlternative)
  }
}

internal fun convertRecordConstructorParameterAlternatives(element: PsiElement,
                                                           givenParent: UElement?,
                                                           expectedType: Class<out UElement>): UVariable? {
  val (paramAlternative, fieldAlternative) = createAlternatives(element, givenParent) ?: return null

  return when (element) {
    is LightRecordField -> expectedType.accommodate(fieldAlternative, paramAlternative)
    else -> expectedType.accommodate(paramAlternative, fieldAlternative)
  }
}

private fun createAlternatives(element: PsiElement,
                               givenParent: UElement?): Pair<UElementAlternative<JavaRecordUParameter>, UElementAlternative<JavaRecordUField>>? {

  fun <T> logAndNull(message: () -> String): T? =
    CONVERSION_LOGGER.logAndNull { "createAlternatives($element) ${message()}" }

  val (psiRecordComponent, lightRecordField, lightConstructorParameter) = when (element) {
    is PsiRecordComponent -> Triple(element, null, null)
    is LightRecordConstructorParameter -> {
      val containingClass = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)?.containingClass
                            ?: return logAndNull { "failed to get containingClass" }
      val findFieldByName = containingClass.findFieldByName(element.name, false)
                            ?: return logAndNull { "failed to find ${element.name} in $containingClass" }
      val lightRecordField = findFieldByName.asSafely<LightRecordField>()
                             ?: return logAndNull { "failed to cast ${findFieldByName} to LightRecordField" }
      Triple(lightRecordField.recordComponent, lightRecordField, element)
    }
    is LightRecordField -> Triple(element.recordComponent, element, null)
    else -> return logAndNull { "no matches in when" }
  }

  val paramAlternative = alternative {
    val psiClass = psiRecordComponent.containingClass ?: return@alternative logAndNull { "no psiRecordComponent containingClass" }
    val jvmParameter = lightConstructorParameter ?: psiClass.constructors.asSequence()
      .filter { it is LightElement }
      .flatMap { it.parameterList.parameters.asSequence() }.firstOrNull { it.name == psiRecordComponent.name }
    val psiParameter = jvmParameter ?: return@alternative logAndNull { "no psiRecordComponent psiParameter ${psiRecordComponent.name}" }
    JavaRecordUParameter(psiRecordComponent, psiParameter, givenParent)
  }
  val fieldAlternative = alternative {
    val psiField = lightRecordField ?: psiRecordComponent.containingClass?.findFieldByName(psiRecordComponent.name, false)
                   ?: return@alternative logAndNull { "no lightRecordField by name ${psiRecordComponent.name}" }
    JavaRecordUField(psiRecordComponent, psiField, givenParent)
  }
  return Pair(paramAlternative, fieldAlternative)
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

  private val initializingClassPart = UastLazyPart<UClass?>()
  private val valueArgumentsPart = UastLazyPart<List<UExpression>>()

  override val initializingClass: UClass?
    get() = initializingClassPart.getOrBuild { UastFacade.findPlugin(sourcePsi)?.convertOpt(sourcePsi.initializingClass, this) }

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
  override val classReference: UReferenceExpression
    get() = JavaEnumConstantClassReference(sourcePsi, this)
  override val typeArgumentCount: Int
    get() = 0
  override val typeArguments: List<PsiType>
    get() = emptyList()
  override val valueArgumentCount: Int
    get() = sourcePsi.argumentList?.expressions?.size ?: 0

  override val valueArguments: List<UExpression>
    get() = valueArgumentsPart.getOrBuild {
      sourcePsi.argumentList?.expressions?.map {
        UastFacade.findPlugin(it)?.convertElement(it, this) as? UExpression ?: UastEmptyExpression(this)
      } ?: emptyList()
    }

  override fun getArgumentForParameter(i: Int): UExpression? = valueArguments.getOrNull(i)

  override val returnType: PsiType
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
