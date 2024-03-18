// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.test.common.kotlin

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.impl.light.LightMethodBuilder
import com.intellij.psi.impl.light.LightParameter
import com.intellij.psi.impl.light.LightTypeParameterBuilder
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.asJava.elements.LightVariableBuilder
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.uast.UFile
import org.jetbrains.uast.test.common.kotlin.UastTestSuffix.TXT
import java.io.File

interface UastResolveEverythingTestBase : UastPluginSelection, UastFileComparisonTestBase {
    private fun getResolvedFile(filePath: String, suffix: String): File = getTestMetadataFileFromPath(filePath, "resolved$suffix")

    private fun getIdenticalResolvedFile(filePath: String): File = getResolvedFile(filePath, TXT)

    private fun getPluginResolvedFile(filePath: String): File {
        val identicalFile = getIdenticalResolvedFile(filePath)
        if (identicalFile.exists()) return identicalFile
        return getResolvedFile(filePath, "$pluginSuffix$TXT")
    }

    fun check(filePath: String, file: UFile) {
        val resolvedFile = getPluginResolvedFile(filePath)

        KotlinTestUtils.assertEqualsToFile(resolvedFile, file.resolvableWithTargets(::renderLightElementDifferently))

        cleanUpIdenticalFile(
            resolvedFile,
            getResolvedFile(filePath, "$counterpartSuffix$TXT"),
            getIdenticalResolvedFile(filePath),
            kind = "resolved"
        )
    }

    fun renderLightElementDifferently(element: PsiElement?): String {
        var str = element.toString()
        if (!element.needsDifferentRender) return str
        // NB: many declarations in (FIR) LC are internal, so type checking won't work.
        REGEXES.forEach { (lcRegex, tag) -> str = lcRegex.replace(str, tag) }
        TAGS.forEach { (lc, tag) -> str = str.replace(lc, tag) }
        return str
    }

    companion object {
        private val PsiElement?.needsDifferentRender: Boolean
            get() = this is KtLightElement<*, *> ||
                    this is PsiClass ||
                    this is PsiMethod ||
                    this is PsiField ||
                    this is LightMethodBuilder ||
                    this is LightVariableBuilder ||
                    this is LightParameter ||
                    this is LightTypeParameterBuilder

        private const val TAG_CLASS = "Kotlin_Light_Class"
        private const val TAG_CLASS_DECOMPILED = "Decompiled_Class"
        private const val TAG_METHOD = "Kotlin_Light_Method"
        private const val TAG_METHOD_DECOMPILED = "Decompiled_Method"
        private const val TAG_VARIABLE = "Kotlin_Light_Variable"
        private const val TAG_VARIABLE_DECOMPILED = "Decompiled_Variable"
        private const val TAG_VALUE_PARAMETER = "Kotlin_Light_Value_Parameter"
        private const val TAG_TYPE_PARAMETER = "Kotlin_Light_Type_Parameter"

        private val TAGS: Map<String, String> = mapOf(
            // NB: class details include `annotation`/`interface`/`enum`, primary ctor(), etc., hence not filtering out.
            "KtUltraLightClassForLocalDeclaration" to TAG_CLASS,
            "KtUltraLightClass" to TAG_CLASS,

            "Light Parameter" to TAG_VALUE_PARAMETER,
        )

        // NB: Except for class, all other kinds' name is redundant, hence captured by regex and removed.
        private val REGEXES: Map<Regex, String> = mapOf(
            Regex("^SymbolLightClassFor.*?:") to "$TAG_CLASS:",

            Regex("^PsiClass:.+$") to TAG_CLASS_DECOMPILED,

            Regex("^SymbolLightConstructor:.+$") to TAG_METHOD,
            Regex("^SymbolLight.*Method:.+$") to TAG_METHOD,

            Regex("^KtUltraLightMethodForSourceDeclaration:.+$") to TAG_METHOD,
            Regex("^LightMethodBuilder:.+$") to TAG_METHOD,

            Regex("^KtLightMethodForDecompiledDeclaration of .+:.+$") to TAG_METHOD_DECOMPILED,
            Regex("^UastFake.+LightMethod of .+$") to TAG_METHOD_DECOMPILED,
            Regex("^PsiMethod:.+$") to TAG_METHOD_DECOMPILED,

            Regex("^KtLightField:.+$") to TAG_VARIABLE,
            Regex("^LightVariableBuilder:.+$") to TAG_VARIABLE,

            Regex("^KtLightFieldForDecompiledDeclaration of .+:.+$") to TAG_VARIABLE_DECOMPILED,
            Regex("^KtLightEnumEntryForDecompiledDeclaration.+:.+$") to TAG_VARIABLE_DECOMPILED,
            Regex("^PsiField:.+$") to TAG_VARIABLE_DECOMPILED,

            Regex("^SymbolLightTypeParameter:.+$") to TAG_TYPE_PARAMETER,
            Regex("^Light PSI class: .+$") to TAG_TYPE_PARAMETER,

            Regex("^SymbolLight.*Parameter:.+$") to TAG_VALUE_PARAMETER,

            // NB: tags are recursively built, e.g., KtLightMethodForDecompiled... of KtLightClassForDecompiled... of ...
            // Therefore, we should try regex patterns for member names before class names.
            Regex("^KtLight.*ClassForDecompiled.+ of .+:.+$") to TAG_CLASS_DECOMPILED,
            Regex("^KtLightClassForDecompiledFacade:.+$") to TAG_CLASS_DECOMPILED,
        )

    }
}
