// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.ProjectScope
import org.jetbrains.kotlin.config.ApiVersion.Companion.KOTLIN_1_6
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelTypeAliasFqNameIndex
import org.jetbrains.kotlin.load.java.JvmAnnotationNames.*
import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.jetbrains.kotlin.utils.ifEmpty

class JavaAnnotationsConversion(context: NewJ2kConverterContext) : RecursiveApplicableConversionBase(context) {
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element is JKAnnotationList) {
            for (annotation in element.annotations) {
                if (annotation.classSymbol.fqName == "java.lang.SuppressWarnings") {
                    element.annotations -= annotation
                } else {
                    annotation.tryToConvert()
                }
            }
        }

        return recurse(element)
    }

    private fun JKAnnotation.tryToConvert() {
        when(classSymbol.fqName) {
            DEPRECATED_ANNOTATION.asString() -> convertDeprecatedAnnotation()
            TARGET_ANNOTATION.asString() -> convertTargetAnnotation()
            RETENTION_ANNOTATION.asString() -> convertRetentionAnnotation()
            REPEATABLE_ANNOTATION.asString() -> convertRepeatableAnnotation()
        }
    }

    private fun JKAnnotation.convertDeprecatedAnnotation() {
        classSymbol = symbolProvider.provideClassSymbol("kotlin.Deprecated")
        arguments = listOf(JKAnnotationParameterImpl(JKLiteralExpression("\"\"", JKLiteralExpression.LiteralType.STRING)))
    }

    private fun JKAnnotation.convertTargetAnnotation() {
        classSymbol = symbolProvider.provideClassSymbol("kotlin.annotation.Target")
        val argument = arguments.singleOrNull() ?: return
        val javaTargets: List<JKAnnotationMemberValue> = when (val value = argument.value) {
            is JKKtAnnotationArrayInitializerExpression -> value.initializers
            else -> listOf(value)
        }
        val kotlinTargets: List<JKFieldAccessExpression> = javaTargets.flatMap { target ->
            val javaFqName = target.fieldAccessFqName() ?: return
            val kotlinFqNames = targetMappings[javaFqName] ?: return
            kotlinFqNames.map { JKFieldAccessExpression(symbolProvider.provideFieldSymbol(it)) }
        }
        arguments = kotlinTargets.distinctBy { it.identifier.fqName }.map { JKAnnotationParameterImpl(it) }
    }

    private fun JKAnnotation.convertRetentionAnnotation() {
        classSymbol = symbolProvider.provideClassSymbol("kotlin.annotation.Retention")
        val javaRetention = arguments.singleOrNull()?.value ?: return
        val javaFqName = javaRetention.fieldAccessFqName() ?: return
        val kotlinFqName = retentionMappings[javaFqName] ?: return
        val kotlinRetention = JKAnnotationParameterImpl(JKFieldAccessExpression(symbolProvider.provideFieldSymbol(kotlinFqName)))
        arguments = listOf(kotlinRetention)
    }

    private fun JKAnnotation.convertRepeatableAnnotation() {
        if (moduleApiVersion < KOTLIN_1_6) return
        val jvmRepeatable = "kotlin.jvm.JvmRepeatable"
        KotlinTopLevelTypeAliasFqNameIndex
            .get(
                jvmRepeatable,
                context.project,
                context.converter.targetModule?.let { GlobalSearchScope.moduleWithLibrariesScope(it) }
                    ?: ProjectScope.getLibrariesScope(context.project)
            ).ifEmpty {
                return
            }
        classSymbol = symbolProvider.provideClassSymbol(jvmRepeatable)
    }

    private fun JKAnnotationMemberValue.fieldAccessFqName(): String? =
        (safeAs<JKQualifiedExpression>()?.selector ?: this)
            .safeAs<JKFieldAccessExpression>()
            ?.identifier
            ?.fqName

    companion object {
        private val targetMappings: Map<String, List<String>> =
            listOf(
                "ANNOTATION_TYPE" to listOf("ANNOTATION_CLASS"),
                "CONSTRUCTOR" to listOf("CONSTRUCTOR"),
                "FIELD" to listOf("FIELD"),
                "LOCAL_VARIABLE" to listOf("LOCAL_VARIABLE"),
                "METHOD" to listOf("FUNCTION", "PROPERTY_GETTER", "PROPERTY_SETTER"),
                "PACKAGE" to listOf("FILE"),
                "PARAMETER" to listOf("VALUE_PARAMETER"),
                "TYPE_PARAMETER" to listOf("TYPE_PARAMETER"),
                "TYPE" to listOf("CLASS"),
                "TYPE_USE" to listOf("CLASS", "TYPE", "TYPE_PARAMETER")
            ).associate { (java, kotlin) ->
                "java.lang.annotation.ElementType.$java" to kotlin.map { "kotlin.annotation.AnnotationTarget.$it" }
            }

        private val retentionMappings: Map<String, String> =
            listOf(
                "SOURCE" to "SOURCE",
                "CLASS" to "BINARY",
                "RUNTIME" to "RUNTIME",
            ).associate { (java, kotlin) ->
                "java.lang.annotation.RetentionPolicy.$java" to "kotlin.annotation.AnnotationRetention.$kotlin"
            }
    }
}
