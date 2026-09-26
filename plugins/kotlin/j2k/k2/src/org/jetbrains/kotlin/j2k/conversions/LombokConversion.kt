// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k.conversions

import com.intellij.psi.JavaTokenType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierListOwner
import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.j2k.RecursiveConversion
import org.jetbrains.kotlin.j2k.annotationByFqName
import org.jetbrains.kotlin.j2k.psi
import org.jetbrains.kotlin.j2k.tree.JKAnnotation
import org.jetbrains.kotlin.j2k.tree.JKAnnotationList
import org.jetbrains.kotlin.j2k.tree.JKBlockImpl
import org.jetbrains.kotlin.j2k.tree.JKClass
import org.jetbrains.kotlin.j2k.tree.JKClass.ClassKind.CLASS
import org.jetbrains.kotlin.j2k.tree.JKConstructor
import org.jetbrains.kotlin.j2k.tree.JKConstructorImpl
import org.jetbrains.kotlin.j2k.tree.JKField
import org.jetbrains.kotlin.j2k.tree.JKFieldAccessExpression
import org.jetbrains.kotlin.j2k.tree.JKKtAssignmentStatement
import org.jetbrains.kotlin.j2k.tree.JKLabelEmpty
import org.jetbrains.kotlin.j2k.tree.JKModalityModifierElement
import org.jetbrains.kotlin.j2k.tree.JKNameIdentifier
import org.jetbrains.kotlin.j2k.tree.JKOtherModifierElement
import org.jetbrains.kotlin.j2k.tree.JKParameter
import org.jetbrains.kotlin.j2k.tree.JKQualifiedExpression
import org.jetbrains.kotlin.j2k.tree.JKStatement
import org.jetbrains.kotlin.j2k.tree.JKStubExpression
import org.jetbrains.kotlin.j2k.tree.JKThisExpression
import org.jetbrains.kotlin.j2k.tree.JKTreeElement
import org.jetbrains.kotlin.j2k.tree.JKTypeElement
import org.jetbrains.kotlin.j2k.tree.JKVisibilityModifierElement
import org.jetbrains.kotlin.j2k.tree.Modality.FINAL
import org.jetbrains.kotlin.j2k.tree.Mutability.IMMUTABLE
import org.jetbrains.kotlin.j2k.tree.Mutability.MUTABLE
import org.jetbrains.kotlin.j2k.tree.OtherModifier
import org.jetbrains.kotlin.j2k.tree.Visibility
import org.jetbrains.kotlin.j2k.tree.Visibility.INTERNAL
import org.jetbrains.kotlin.j2k.tree.Visibility.PRIVATE
import org.jetbrains.kotlin.j2k.tree.Visibility.PROTECTED
import org.jetbrains.kotlin.j2k.tree.Visibility.PUBLIC
import org.jetbrains.kotlin.j2k.tree.JKOperatorToken
import org.jetbrains.kotlin.j2k.tree.copyTreeAndDetach
import org.jetbrains.kotlin.j2k.tree.modality
import org.jetbrains.kotlin.j2k.tree.mutability
import org.jetbrains.kotlin.j2k.tree.visibility

private const val LOMBOK_DATA: String = "lombok.Data"
private const val LOMBOK_VALUE: String = "lombok.Value"
private const val LOMBOK_ALL_ARGS_CONSTRUCTOR: String = "lombok.AllArgsConstructor"
private const val LOMBOK_REQUIRED_ARGS_CONSTRUCTOR: String = "lombok.RequiredArgsConstructor"
private const val LOMBOK_NON_NULL: String = "lombok.NonNull"
private const val LOMBOK_GETTER: String = "lombok.Getter"
private const val LOMBOK_EQUALS_AND_HASH_CODE: String = "lombok.EqualsAndHashCode"
private const val LOMBOK_TO_STRING: String = "lombok.ToString"
private const val LOMBOK_SETTER: String = "lombok.Setter"
private const val PUBLIC_LEVEL: String = "PUBLIC"
private const val NONE_LEVEL: String = "NONE"

class LombokConversion(context: ConverterContext) : RecursiveConversion(context) {
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element is JKClass) element.convert()
        return recurse(element)
    }

    private fun JKClass.convert() {
        if (classKind != CLASS) return
        val value = annotationList.annotationByFqName(LOMBOK_VALUE)
        val data = annotationList.annotationByFqName(LOMBOK_DATA)
        val allArgs = annotationList.annotationByFqName(LOMBOK_ALL_ARGS_CONSTRUCTOR)
        val requiredArgs = annotationList.annotationByFqName(LOMBOK_REQUIRED_ARGS_CONSTRUCTOR)

        val presented = listOfNotNull(value, data, allArgs, requiredArgs)
        if (presented.isEmpty()) return
        if (presented.any { it.arguments.isNotEmpty() }) return
        // `@Data` and `@Value` add their constructor only when the class declares none
        if ((value != null || data != null) && classBody.declarations.any { it is JKConstructor }) return

        val allFieldsToConstructor = value != null || allArgs != null
        val generateAccessors = value != null || data != null

        val fields = classBody.declarations.filterIsInstance<JKField>()
        val constructorFields = fields.filter { if (allFieldsToConstructor) it.isAllArgument() else it.isRequiredArgument() }
        val shouldBeDataClass = generateAccessors && constructorFields.isNotEmpty() && canBeDataClass()

        for (annotation in presented) annotationList.annotations -= annotation

        if (generateAccessors) {
            for (field in fields) applyGeneratedAccessors(field, immutable = value != null)
            removeAccessorAnnotations(annotationList)
            if (!shouldBeDataClass) addGeneratedMemberAnnotations()
        }
        if (constructorFields.isEmpty()) return

        classBody.declarations += generateConstructor(constructorFields)
        if (shouldBeDataClass) otherModifierElements += JKOtherModifierElement(OtherModifier.DATA)
    }

    private fun JKClass.addGeneratedMemberAnnotations() {
        for (fqName in listOf(LOMBOK_EQUALS_AND_HASH_CODE, LOMBOK_TO_STRING)) {
            annotationList.annotations += JKAnnotation(symbolProvider.provideClassSymbol(fqName))
        }
    }

    private fun JKClass.applyGeneratedAccessors(field: JKField, immutable: Boolean) {
        val psiField = field.psi<PsiField>() ?: return
        if (psiField.hasModifierProperty(PsiModifier.STATIC)) return
        val psiClass = psi<PsiClass>()

        val getterLevel = psiField.accessLevel(LOMBOK_GETTER) ?: psiClass?.accessLevel(LOMBOK_GETTER) ?: PUBLIC_LEVEL
        field.visibility = getterLevel.toVisibility() ?: field.visibility

        if (immutable) {
            field.mutability = IMMUTABLE
        } else if (!psiField.hasModifierProperty(PsiModifier.FINAL)) {
            val setterLevel = psiField.accessLevel(LOMBOK_SETTER) ?: psiClass?.accessLevel(LOMBOK_SETTER) ?: PUBLIC_LEVEL
            if (setterLevel != NONE_LEVEL) field.mutability = MUTABLE
        }
        removeAccessorAnnotations(field.annotationList)
    }

    private fun removeAccessorAnnotations(annotationList: JKAnnotationList) {
        for (fqName in listOf(LOMBOK_GETTER, LOMBOK_SETTER)) {
            val annotation = annotationList.annotationByFqName(fqName) ?: continue
            annotationList.annotations -= annotation
        }
    }

    private fun PsiModifierListOwner.accessLevel(annotationFqName: String): String? {
        val annotation = modifierList?.findAnnotation(annotationFqName) ?: return null
        val value = annotation.findDeclaredAttributeValue("value") ?: return PUBLIC_LEVEL
        return value.text.substringAfterLast('.')
    }

    private fun String.toVisibility(): Visibility? = when (this) {
        PUBLIC_LEVEL -> PUBLIC
        "PROTECTED" -> PROTECTED
        "PACKAGE", "MODULE" -> INTERNAL
        "PRIVATE" -> PRIVATE
        else -> null
    }

    private fun JKClass.canBeDataClass(): Boolean {
        if (modality != FINAL) return false
        val psiClass = psi<PsiClass>() ?: return false
        return psiClass.containingClass == null || psiClass.hasModifierProperty(PsiModifier.STATIC)
    }

    private fun JKField.isAllArgument(): Boolean {
        val psiField = psi<PsiField>() ?: return false
        if (psiField.hasModifierProperty(PsiModifier.STATIC)) return false
        return !(psiField.hasModifierProperty(PsiModifier.FINAL) && psiField.hasInitializer())
    }

    private fun JKField.isRequiredArgument(): Boolean {
        val psiField = psi<PsiField>() ?: return false
        if (psiField.hasInitializer()) return false
        if (psiField.hasModifierProperty(PsiModifier.STATIC)) return false
        return psiField.hasModifierProperty(PsiModifier.FINAL) || psiField.hasAnnotation(LOMBOK_NON_NULL)
    }

    private fun JKClass.generateConstructor(fields: List<JKField>): JKConstructorImpl {
        val parameters = fields.map { field ->
            JKParameter(
                JKTypeElement(field.type.type, field.type.annotationList.copyTreeAndDetach()),
                JKNameIdentifier(field.name.value)
            ).also { symbolProvider.provideUniverseSymbol(it) }
        }

        return JKConstructorImpl(
            JKNameIdentifier(name.value),
            parameters,
            JKBlockImpl(fields.zip(parameters, ::generateAssignmentStatement)),
            delegationCall = JKStubExpression(),
            JKAnnotationList(),
            otherModifierElements = emptyList(),
            JKVisibilityModifierElement(PUBLIC),
            JKModalityModifierElement(FINAL)
        ).also { it.lineBreaksAfter = 1 }
    }

    private fun generateAssignmentStatement(field: JKField, parameter: JKParameter): JKStatement =
        JKKtAssignmentStatement(
            JKQualifiedExpression(
                JKThisExpression(JKLabelEmpty(), parameter.type.type),
                JKFieldAccessExpression(symbolProvider.provideUniverseSymbol(field))
            ),
            JKFieldAccessExpression(symbolProvider.provideUniverseSymbol(parameter)),
            JKOperatorToken.fromElementType(JavaTokenType.EQ)
        ).also { it.lineBreaksAfter = 1 }
}
