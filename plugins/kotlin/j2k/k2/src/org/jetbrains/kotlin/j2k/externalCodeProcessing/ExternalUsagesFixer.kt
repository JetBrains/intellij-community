// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k.externalCodeProcessing

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolModality
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.psi.isConstructorDeclaredProperty
import org.jetbrains.kotlin.j2k.AccessorKind.GETTER
import org.jetbrains.kotlin.j2k.AccessorKind.SETTER
import org.jetbrains.kotlin.lexer.KtTokens.CONST_KEYWORD
import org.jetbrains.kotlin.lexer.KtTokens.OPEN_KEYWORD
import org.jetbrains.kotlin.lexer.KtTokens.OVERRIDE_KEYWORD
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

class ExternalUsagesFixer(private val usages: List<JKMemberInfoWithUsages>) {
    private val conversions: MutableList<JKExternalConversion> = mutableListOf()
    private val jvmFieldAnnotatedDeclarations: MutableSet<KtNamedDeclaration> = mutableSetOf()
    private val jvmStaticAnnotatedDeclarations: MutableSet<KtNamedDeclaration> = mutableSetOf()

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
            ktProperty.canBeAnnotatedWithJvmField(isEffectivelyFinal) ->
                ktProperty.addJvmFieldAnnotationIfThereAreNoJvmAnnotations()

            isStatic && !ktProperty.hasModifier(CONST_KEYWORD) ->
                ktProperty.addJvmStaticAnnotationIfThereAreNoJvmAnnotations()
        }
    }

    private fun JKMethodData.fix(javaUsages: List<PsiElement>, kotlinUsages: List<KtElement>) {
        val member = usedAsAccessorOfProperty ?: this
        val element = member.kotlinElement ?: return

        if (javaUsages.isNotEmpty()) {
            when {
                element.canBeAnnotatedWithJvmField(member.isEffectivelyFinal) ->
                    element.addJvmFieldAnnotationIfThereAreNoJvmAnnotations()

                isStatic && !element.isConstProperty() ->
                    element.addJvmStaticAnnotationIfThereAreNoJvmAnnotations()
            }
        }

        if (!element.isSimpleProperty()) return

        val accessorKind = if (javaElement.name.startsWith("set")) SETTER else GETTER

        for (usage in kotlinUsages) {
            conversions += AccessorToPropertyKotlinExternalConversion(member.name, accessorKind, usage)
        }

        if (javaUsages.isNotEmpty() && element.hasJvmFieldAnnotation()) {
            for (usage in javaUsages) {
                conversions += AccessorToPropertyJavaExternalConversion(member.name, accessorKind, usage)
            }
        }
    }

    private fun KtNamedDeclaration.hasJvmFieldAnnotation(): Boolean =
        jvmFieldAnnotatedDeclarations.contains(this)

    private fun KtNamedDeclaration.hasJvmAnnotations(): Boolean =
        jvmFieldAnnotatedDeclarations.contains(this) || jvmStaticAnnotatedDeclarations.contains(this)

    private fun KtNamedDeclaration.isConstProperty(): Boolean =
        this is KtProperty && hasModifier(CONST_KEYWORD)

    private fun KtNamedDeclaration.canBeAnnotatedWithJvmField(isEffectivelyFinal: Boolean): Boolean {
        if (hasModifier(OVERRIDE_KEYWORD) || hasModifier(OPEN_KEYWORD) || hasModifier(CONST_KEYWORD)) return false
        if (!isSimpleProperty()) return false
        // A Kotlin compiler plugin (e.g. all-open or kotlin-jpa) may make the property effectively `open` even
        // without an explicit `open` modifier. `@JvmField` cannot be applied to open or abstract properties, so
        // adding it in that case would produce uncompilable code (KTIJ-38510). The effective modality is precomputed
        // in a background read action (see [populateEffectiveModality]), so here it is only a cheap flag read.
        return isEffectivelyFinal
    }

    private fun KtNamedDeclaration.isSimpleProperty(): Boolean =
        isConstructorDeclaredProperty() ||
                (this is KtProperty && getter == null && setter == null)

    private fun KtNamedDeclaration.addJvmFieldAnnotationIfThereAreNoJvmAnnotations() {
        if (hasJvmAnnotations()) return
        jvmFieldAnnotatedDeclarations.add(this)
        addAnnotation(JVM_FIELD)
    }

    private fun KtNamedDeclaration.addJvmStaticAnnotationIfThereAreNoJvmAnnotations() {
        if (hasJvmAnnotations()) return
        jvmStaticAnnotatedDeclarations.add(this)
        addAnnotation(JVM_STATIC)
    }

    private fun KtNamedDeclaration.addAnnotation(fqName: String) {
        val annotation = addAnnotationEntry(KtPsiFactory(project).createAnnotationEntry("@$fqName"))
        ShortenReferencesFacility.getInstance().shorten(annotation)
    }

    data class JKMemberInfoWithUsages(
        val member: JKMemberData,
        val javaUsages: List<PsiElement>,
        val kotlinUsages: List<KtElement>
    )

    companion object {
        /**
         * Resolves the effective modality of every converted declaration backing [usages] and stores it in
         * [JKMemberData.isEffectivelyFinal], so that [ExternalUsagesFixer] can later decide about `@JvmField`
         * without touching the Analysis API.
         *
         * Must be called inside a (background) read action: it resolves symbol modality, which can trigger heavy,
         * compiler-plugin-driven lazy resolution and therefore must not run inside the EDT write action (KTIJ-38510).
         */
        fun populateEffectiveModality(usages: List<JKMemberInfoWithUsages>) {
            for (usage in usages) {
                val member = usage.member
                val declaration = member.kotlinElement ?: continue
                // Only members of a class or object can have their modality altered by compiler plugins; everything
                // else keeps the default `isEffectivelyFinal = true`.
                if (declaration.containingClassOrObject == null) continue
                member.isEffectivelyFinal = analyze(declaration) {
                    val symbol = declaration.symbol
                    val propertySymbol = (symbol as? KaValueParameterSymbol)?.generatedPrimaryConstructorProperty ?: symbol
                    propertySymbol.modality == KaSymbolModality.FINAL
                }
            }
        }
    }
}

private const val JVM_FIELD: String = "kotlin.jvm.JvmField"
private const val JVM_STATIC: String = "kotlin.jvm.JvmStatic"
