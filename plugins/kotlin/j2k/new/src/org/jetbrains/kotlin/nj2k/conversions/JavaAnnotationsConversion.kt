// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.nj2k.conversions

import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.ProjectScope
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelTypeAliasFqNameIndex
import org.jetbrains.kotlin.load.java.JvmAnnotationNames
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
                    processAnnotation(annotation)
                }
            }
        }

        return recurse(element)
    }

    private fun processAnnotation(annotation: JKAnnotation) {
        with(annotation) {
            tryConvertDeprecatedAnnotation() || tryConvertTargetAnnotation() || tryConvertRepeatableAnnotation()
        }
    }

    private fun JKAnnotation.tryConvertDeprecatedAnnotation(): Boolean {
        if (classSymbol.fqName == "java.lang.Deprecated") {
            classSymbol = symbolProvider.provideClassSymbol("kotlin.Deprecated")
            arguments = listOf(JKAnnotationParameterImpl(JKLiteralExpression("\"\"", JKLiteralExpression.LiteralType.STRING)))
            return true
        }

        return false
    }

    private fun JKAnnotation.tryConvertTargetAnnotation(): Boolean {
        if (classSymbol.fqName == JvmAnnotationNames.TARGET_ANNOTATION.asString()) {
            classSymbol = symbolProvider.provideClassSymbol("kotlin.annotation.Target")

            arguments.singleOrNull()
                ?.let { parameter ->
                    when (val value = parameter.value) {
                        is JKKtAnnotationArrayInitializerExpression -> value.initializers
                        else -> listOf(value)
                    }
                }
                ?.flatMap { value ->
                    value.fieldAccessFqName()
                        ?.let { targetMappings[it] }
                        ?.map { fqName -> JKFieldAccessExpression(symbolProvider.provideFieldSymbol(fqName)) }
                        ?: listOf(value.copyTreeAndDetach())
                }
                ?.map { JKAnnotationParameterImpl(it) }
                ?.let {
                    arguments = it
                }

            return true
        }

        return false
    }

    private fun JKAnnotation.tryConvertRepeatableAnnotation(): Boolean {
        if (classSymbol.fqName == JvmAnnotationNames.REPEATABLE_ANNOTATION.asString() && moduleApiVersion >= ApiVersion.KOTLIN_1_6) {
            val jvmRepeatable = "kotlin.jvm.JvmRepeatable"
            KotlinTopLevelTypeAliasFqNameIndex.getInstance()[
                    jvmRepeatable,
                    context.project,
                    context.converter.targetModule?.let { GlobalSearchScope.moduleWithLibrariesScope(it) }
                        ?: ProjectScope.getLibrariesScope(context.project)
            ].ifEmpty {
                return false
            }

            classSymbol = symbolProvider.provideClassSymbol(jvmRepeatable)
            return true
        }

        return false
    }

    private fun JKAnnotationMemberValue.fieldAccessFqName(): String? =
        (safeAs<JKQualifiedExpression>()?.selector ?: this)
            .safeAs<JKFieldAccessExpression>()
            ?.identifier
            ?.fqName


    companion object {
        private val targetMappings =
            listOf(
                "ANNOTATION_TYPE" to listOf("ANNOTATION_CLASS"),
                "CONSTRUCTOR" to listOf("CONSTRUCTOR"),
                "FIELD" to listOf("FIELD"),
                "LOCAL_VARIABLE" to listOf("LOCAL_VARIABLE"),
                "METHOD" to listOf("FUNCTION", "PROPERTY_GETTER", "PROPERTY_SETTER"),
                "PACKAGE" to listOf("FILE"),
                "PARAMETER" to listOf("VALUE_PARAMETER"),
                "TYPE_PARAMETER" to listOf("TYPE_PARAMETER"),
                "TYPE" to listOf("ANNOTATION_CLASS", "CLASS"),
                "TYPE_USE" to listOf("TYPE_USE")
            ).map { (java, kotlins) ->
                "java.lang.annotation.ElementType.$java" to kotlins.map { "kotlin.annotation.AnnotationTarget.$it" }
            }.toMap()
    }
}
