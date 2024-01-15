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
        usages.forEach { it.fix() }
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
        run {
            val ktProperty = kotlinElement ?: return@run
            when {
                javaUsages.isNotEmpty() && ktProperty.canBeAnnotatedWithJvmField() ->
                    ktProperty.addAnnotationIfThereAreNoJvmOnes(JVM_FIELD_ANNOTATION_FQ_NAME)

                javaUsages.isNotEmpty() && isStatic && !ktProperty.hasModifier(CONST_KEYWORD) ->
                    ktProperty.addAnnotationIfThereAreNoJvmOnes(JVM_STATIC_FQ_NAME)
            }
        }

        if (wasRenamed) {
            javaUsages.forEach { usage ->
                conversions += PropertyRenamedJavaExternalUsageConversion(name, usage)
            }

            kotlinUsages.forEach { usage ->
                conversions += PropertyRenamedKotlinExternalUsageConversion(name, usage)
            }
        }
    }

    private fun JKMethodData.fix(javaUsages: List<PsiElement>, kotlinUsages: List<KtElement>) {
        val member = usedAsAccessorOfProperty ?: this
        val element = member.kotlinElement

        when {
            javaUsages.isNotEmpty() && element.canBeAnnotatedWithJvmField() ->
                element?.addAnnotationIfThereAreNoJvmOnes(JVM_FIELD_ANNOTATION_FQ_NAME)

            javaUsages.isNotEmpty() && isStatic && !element.isConstProperty() ->
                element?.addAnnotationIfThereAreNoJvmOnes(JVM_STATIC_FQ_NAME)
        }

        if (element.isSimpleProperty()) {
            val accessorKind = if (javaElement.name.startsWith("set")) SETTER else GETTER
            if (element?.hasJvmFieldAnnotation() == true) {
                javaUsages.forEach { usage ->
                    conversions += AccessorToPropertyJavaExternalConversion(member.name, accessorKind, usage)
                }
            }

            kotlinUsages.forEach { usage ->
                conversions += AccessorToPropertyKotlinExternalConversion(member.name, accessorKind, usage)
            }
        }
    }

    private fun KtNamedDeclaration?.isConstProperty(): Boolean =
        this is KtProperty && hasModifier(CONST_KEYWORD)

    private fun KtNamedDeclaration?.canBeAnnotatedWithJvmField(): Boolean {
        if (this == null) return false
        if (hasModifier(OVERRIDE_KEYWORD) || hasModifier(OPEN_KEYWORD) || hasModifier(CONST_KEYWORD)) return false
        return isSimpleProperty()
    }

    private fun KtNamedDeclaration?.isSimpleProperty(): Boolean =
        this?.isConstructorDeclaredProperty() == true ||
                (this is KtProperty && getter == null && setter == null)

    private fun KtNamedDeclaration.addAnnotationIfThereAreNoJvmOnes(fqName: FqName) {
        // we don't want to resolve here and as we are working with fqNames, just by-text comparing is OK
        if (annotationEntries.any { entry ->
                USED_JVM_ANNOTATIONS.any { jvmAnnotation ->
                    entry.typeReference?.textMatches(jvmAnnotation.asString()) == true
                }
            }
        ) return
        addAnnotationEntry(KtPsiFactory(project).createAnnotationEntry("@${fqName.asString()}"))
    }

    internal data class JKMemberInfoWithUsages(
        val member: JKMemberData,
        val javaUsages: List<PsiElement>,
        val kotlinUsages: List<KtElement>
    )

    companion object {
        private val JVM_STATIC_FQ_NAME = FqName("kotlin.jvm.JvmStatic")
        val USED_JVM_ANNOTATIONS = listOf(JVM_STATIC_FQ_NAME, JVM_FIELD_ANNOTATION_FQ_NAME)
    }
}
