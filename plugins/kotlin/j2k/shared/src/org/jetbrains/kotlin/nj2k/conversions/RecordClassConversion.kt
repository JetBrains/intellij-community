// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import com.intellij.psi.*
import com.intellij.psi.util.JavaPsiRecordUtil.*
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.name.JvmStandardClassIds.JVM_RECORD_ANNOTATION_FQ_NAME
import org.jetbrains.kotlin.nj2k.*
import org.jetbrains.kotlin.nj2k.externalCodeProcessing.JKLightMethodData
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.tree.Modality.FINAL
import org.jetbrains.kotlin.nj2k.tree.Mutability.IMMUTABLE
import org.jetbrains.kotlin.nj2k.tree.Visibility.PRIVATE
import org.jetbrains.kotlin.nj2k.tree.Visibility.PUBLIC
import org.jetbrains.kotlin.nj2k.types.determineType

/**
 * Converts Java records into Kotlin data classes.
 *
 * See [JEP 395](https://openjdk.org/jeps/395) and [Records documentation](https://docs.oracle.com/en/java/javase/16/language/records.html)
 */
class RecordClassConversion(context: ConverterContext) : RecursiveConversion(context) {
    context(_: KaSession)
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element is JKRecordClass) element.convert()
        return recurse(element)
    }

    context(_: KaSession)
    private fun JKRecordClass.convert() {
        addJvmRecordAnnotationIfPossible()
        registerAccessorsForExternalProcessing()

        val fields = generateFieldsForRecordComponents()
        classBody.declarations += fields

        generateOrModifyConstructor(fields)
    }

    private fun JKRecordClass.addJvmRecordAnnotationIfPossible() {
        // Local @JvmRecord classes are not allowed
        if (!isLocalClass()) {
            annotationList.annotations += JKAnnotation(symbolProvider.provideClassSymbol(JVM_RECORD_ANNOTATION_FQ_NAME))
        }
    }

    context(_: KaSession)
    private fun JKRecordClass.registerAccessorsForExternalProcessing() {
        if (visibility == PRIVATE || isLocalClass()) return
        for (component in recordComponents) {
            val psiRecordComponent = component.psi<PsiRecordComponent>() ?: continue
            val accessor = getAccessorForRecordComponent(psiRecordComponent) ?: continue
            context.externalCodeProcessor.addMember(JKLightMethodData(accessor))
        }
    }

    private fun JKRecordClass.generateFieldsForRecordComponents(): List<JKField> =
        recordComponents.map { component ->
            JKField(
                JKTypeElement(
                    component.determineType(symbolProvider),
                    component.type.annotationList.copyTreeAndDetach()
                ),
                component.name.copyTreeAndDetach(),
                initializer = JKStubExpression(),
                component.annotationList.copyTreeAndDetach(),
                otherModifierElements = emptyList(),
                JKVisibilityModifierElement(PUBLIC),
                JKModalityModifierElement(FINAL),
                JKMutabilityModifierElement(IMMUTABLE)
            ).also { field ->
                field.lineBreaksAfter = 1
                symbolProvider.provideUniverseSymbol(field)
            }
        }

    context(_: KaSession)
    private fun JKRecordClass.generateOrModifyConstructor(fields: List<JKField>) {
        val psiConstructor = canonicalConstructor ?: return
        if (psiConstructor is SyntheticElement) {
            // The original Java class has only a record header, but no explicit constructor.
            generateConstructor(fields)
        } else if (psiConstructor.isCompact) {
            val jkConstructor = symbolProvider.provideUniverseSymbol(psiConstructor).target as? JKConstructor ?: return
            jkConstructor.generateFieldInitializations(fields)
        } else {
            // We have an explicit canonical constructor, which must explicitly initialize the fields in Java.
            // Nothing to do in this case.
        }
    }

    private fun JKRecordClass.generateConstructor(fields: List<JKField>) {
        classBody.declarations += JKConstructorImpl(
            JKNameIdentifier(name.value),
            parameters = recordComponents.map { it.copyTreeAndDetach() },
            block = JKBlockImpl(generateFieldInitializations(fields)),
            delegationCall = JKStubExpression(),
            JKAnnotationList(),
            otherModifierElements = emptyList(),
            JKVisibilityModifierElement(PUBLIC),
            JKModalityModifierElement(FINAL)
        ).also {
           it.lineBreaksAfter = 1
        }
    }

    private fun JKRecordClass.generateFieldInitializations(fields: List<JKField>): List<JKStatement> =
        fields.mapIndexed { i, field -> generateAssignmentStatement(lhs = field, rhs = recordComponents[i]) }

    private fun generateAssignmentStatement(lhs: JKField, rhs: JKParameter): JKKtAssignmentStatement =
        JKKtAssignmentStatement(
            JKQualifiedExpression(
                JKThisExpression(JKLabelEmpty(), rhs.type.type),
                JKFieldAccessExpression(symbolProvider.provideUniverseSymbol(lhs))
            ),
            JKFieldAccessExpression(symbolProvider.provideUniverseSymbol(rhs)),
            JKOperatorToken.fromElementType(JavaTokenType.EQ)
        ).also {
            it.lineBreaksAfter = 1
        }

    context(_: KaSession)
    private fun JKConstructor.generateFieldInitializations(fields: List<JKField>) {
        for (field in fields) {
            val parameter = parameters.find { it.name.value == field.name.value } ?: continue
            val initialization = generateAssignmentStatement(lhs = field, rhs = parameter)
            if (parameter.hasWritableUsages(scope = this, context)) {
                // Insert field initialization at the end, after the parameter has been reassigned
                block.statements += listOf(initialization)
            } else {
                // Insert field initialization at the beginning, so that `MergePropertyWithConstructorParameterProcessing`
                // can merge the field into the primary constructor later.
                block.statements = listOf(initialization) + block.statements
            }
        }
    }

    private val JKRecordClass.canonicalConstructor: PsiMethod?
        get() {
            val psiClass = psi as? PsiClass ?: return null
            return findCanonicalConstructor(psiClass)
        }

    private val PsiMethod.isCompact: Boolean
        get() = isCompactConstructor(this)
}
