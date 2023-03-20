// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import com.intellij.psi.*
import com.intellij.psi.util.JavaPsiRecordUtil.*
import org.jetbrains.kotlin.nj2k.*
import org.jetbrains.kotlin.nj2k.externalCodeProcessing.JKLightMethodData
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.tree.JKClass.ClassKind.RECORD
import org.jetbrains.kotlin.nj2k.tree.Modality.FINAL
import org.jetbrains.kotlin.nj2k.tree.Mutability.MUTABLE
import org.jetbrains.kotlin.nj2k.tree.Visibility.PUBLIC
import org.jetbrains.kotlin.nj2k.types.determineType

/**
 * See https://openjdk.org/jeps/395 and https://docs.oracle.com/en/java/javase/16/language/records.html
 */
class RecordClassConversion(context: NewJ2kConverterContext) : RecursiveApplicableConversionBase(context) {
    private val componentsToFields = mutableMapOf<JKJavaRecordComponent, JKField>()

    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element is JKClass && element.classKind == RECORD) element.convert()
        return recurse(element)
    }

    private fun JKClass.convert() {
        addJvmRecordAnnotationIfPossible()
        registerAccessorsForExternalProcessing()
        generateFields()
        generateOrModifyConstructor()
    }

    private fun JKClass.addJvmRecordAnnotationIfPossible() {
        // Local @JvmRecord classes are not allowed
        if (!isLocalClass()) {
            annotationList.annotations += JKAnnotation(symbolProvider.provideClassSymbol("kotlin.jvm.JvmRecord"))
        }
    }

    private fun JKClass.registerAccessorsForExternalProcessing() {
        recordComponents.forEach { component ->
            component.psi<PsiRecordComponent>()?.let { psiRecordComponent ->
                val accessor = getAccessorForRecordComponent(psiRecordComponent) ?: return@forEach
                context.externalCodeProcessor.addMember(JKLightMethodData(accessor))
            }
        }
    }

    private fun JKClass.generateFields() {
        classBody.declarations += recordComponents.map { component ->
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
                JKMutabilityModifierElement((MUTABLE))
            ).also { field ->
                componentsToFields[component] = field
                symbolProvider.provideUniverseSymbol(field)
            }
        }
    }

    private fun JKClass.generateOrModifyConstructor() {
        val psiConstructor = canonicalConstructor ?: return
        if (psiConstructor is SyntheticElement) {
            // The original Java class has only a record header, but no explicit constructor.
            generateConstructor()
        } else if (psiConstructor.isCompact) {
            val jkConstructor = symbolProvider.provideUniverseSymbol(psiConstructor).target as? JKConstructor ?: return
            jkConstructor.generateFieldInitializations()
        } else {
            // We have an explicit canonical constructor, which must explicitly initialize the fields in Java.
            // Nothing to do in this case.
        }
    }

    private fun JKClass.generateConstructor() {
        classBody.declarations += JKConstructorImpl(
            JKNameIdentifier(name.value),
            parameters = recordComponents.map { it.copyTreeAndDetach() },
            block = JKBlockImpl(generateFieldInitializations()),
            delegationCall = JKStubExpression(),
            JKAnnotationList(),
            otherModifierElements = emptyList(),
            JKVisibilityModifierElement(PUBLIC),
            JKModalityModifierElement(FINAL)
        )
    }

    private fun generateFieldInitializations(): List<JKStatement> =
        componentsToFields.entries.map { (component, field) -> generateFieldInitialization(component, field) }

    private fun generateFieldInitialization(parameter: JKParameter, field: JKField): JKStatement =
        JKKtAssignmentStatement(
            JKQualifiedExpression(
                JKThisExpression(JKLabelEmpty(), parameter.type.type),
                JKFieldAccessExpression(symbolProvider.provideUniverseSymbol(field))
            ),
            JKFieldAccessExpression(symbolProvider.provideUniverseSymbol(parameter)),
            JKOperatorToken.fromElementType(JavaTokenType.EQ)
        )

    private fun JKConstructor.generateFieldInitializations() {
        for (field in componentsToFields.values) {
            val parameter = parameters.find { it.name.value == field.name.value } ?: continue
            val initialization = generateFieldInitialization(parameter, field)
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

    private val JKClass.canonicalConstructor: PsiMethod?
        get() {
            val psiClass = psi as? PsiClass ?: return null
            return findCanonicalConstructor(psiClass)
        }

    private val PsiMethod.isCompact: Boolean
        get() = isCompactConstructor(this)
}
