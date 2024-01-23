// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.externalCodeProcessing

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.psi.isConstructorDeclaredProperty
import org.jetbrains.kotlin.idea.util.hasJvmFieldAnnotation
import org.jetbrains.kotlin.j2k.AccessorKind.GETTER
import org.jetbrains.kotlin.j2k.AccessorKind.SETTER
import org.jetbrains.kotlin.lexer.KtTokens.*
import org.jetbrains.kotlin.load.java.JvmAbi.JVM_FIELD_ANNOTATION_FQ_NAME
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory

internal class ExternalUsagesFixer(private val usages: List<JKMemberInfoWithUsages>) {
    private val conversions = mutableListOf<JKExternalConversion>()

    fun fix() {
        for (usage in usages) {
            usage.fix()
        }
        conversions.sort()
        conversions.forEach(JKExternalConversion::apply)
    }

    private fun JKMemberInfoWithUsages.fix() {
        when (member) {
            is JKFieldDataFromJava -> member.fix(javaUsages, kotlinUsages)
            is JKMethodData -> member.fix(javaUsages, kotlinUsages)
        }
    }

    private fun JKFieldDataFromJava.fix(javaUsages: List<PsiElement>, kotlinUsages: List<KtElement>) {
        if (wasRenamed) {
            for (usage in javaUsages) {
                conversions += PropertyRenamedJavaExternalUsageConversion(name, usage)
            }

            for (usage in kotlinUsages) {
                conversions += PropertyRenamedKotlinExternalUsageConversion(name, usage)
            }
        }

        if (javaUsages.isEmpty()) return
        val ktProperty = kotlinElement ?: return

        when {
            ktProperty.canBeAnnotatedWithJvmField() ->
                ktProperty.addAnnotationIfThereAreNoJvmOnes(JVM_FIELD_ANNOTATION_FQ_NAME)

            isStatic && !ktProperty.hasModifier(CONST_KEYWORD) ->
                ktProperty.addAnnotationIfThereAreNoJvmOnes(JVM_STATIC_FQ_NAME)
        }
    }

    private fun JKMethodData.fix(javaUsages: List<PsiElement>, kotlinUsages: List<KtElement>) {
        val member = usedAsAccessorOfProperty ?: this
        val element = member.kotlinElement ?: return

        if (javaUsages.isNotEmpty()) {
            when {
                element.canBeAnnotatedWithJvmField() ->
                    element.addAnnotationIfThereAreNoJvmOnes(JVM_FIELD_ANNOTATION_FQ_NAME)

                isStatic && !element.isConstProperty() ->
                    element.addAnnotationIfThereAreNoJvmOnes(JVM_STATIC_FQ_NAME)
            }
        }

        if (!element.isSimpleProperty()) return

        val accessorKind = if (javaElement.name.startsWith("set")) SETTER else GETTER

        for (usage in kotlinUsages) {
            conversions += AccessorToPropertyKotlinExternalConversion(member.name, accessorKind, usage)
        }

        if (element.hasJvmFieldAnnotation()) {
            for (usage in javaUsages) {
                conversions += AccessorToPropertyJavaExternalConversion(member.name, accessorKind, usage)
            }
        }
    }

    private fun KtNamedDeclaration.isConstProperty(): Boolean =
        this is KtProperty && hasModifier(CONST_KEYWORD)

    private fun KtNamedDeclaration.canBeAnnotatedWithJvmField(): Boolean {
        if (hasModifier(OVERRIDE_KEYWORD) || hasModifier(OPEN_KEYWORD) || hasModifier(CONST_KEYWORD)) return false
        return isSimpleProperty()
    }

    private fun KtNamedDeclaration.isSimpleProperty(): Boolean =
        isConstructorDeclaredProperty() ||
                (this is KtProperty && getter == null && setter == null)

    private fun KtNamedDeclaration.addAnnotationIfThereAreNoJvmOnes(fqName: FqName) {
        // we don't want to resolve here and as we are working with fqNames, just by-text comparing is OK
        val hasJvmAnnotations = annotationEntries.any { entry ->
            USED_JVM_ANNOTATIONS.any { jvmAnnotation ->
                entry.typeReference?.textMatches(jvmAnnotation.asString()) == true
            }
        }
        if (!hasJvmAnnotations) {
            addAnnotationEntry(KtPsiFactory(project).createAnnotationEntry("@${fqName.asString()}"))
        }
    }

    internal data class JKMemberInfoWithUsages(
        val member: JKMemberData,
        val javaUsages: List<PsiElement>,
        val kotlinUsages: List<KtElement>
    )

    companion object {
        private val JVM_STATIC_FQ_NAME: FqName = FqName("kotlin.jvm.JvmStatic")
        val USED_JVM_ANNOTATIONS: List<FqName> = listOf(JVM_STATIC_FQ_NAME, JVM_FIELD_ANNOTATION_FQ_NAME)
    }
}
