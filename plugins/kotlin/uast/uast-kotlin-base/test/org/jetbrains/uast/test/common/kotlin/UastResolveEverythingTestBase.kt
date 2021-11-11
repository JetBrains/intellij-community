/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.uast.test.common.kotlin

import com.intellij.psi.PsiElement
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
                    this is LightMethodBuilder ||
                    this is LightVariableBuilder ||
                    this is LightParameter ||
                    this is LightTypeParameterBuilder

        private const val TAG_CLASS = "Kotlin_Light_Class"
        private const val TAG_METHOD = "Kotlin_Light_Method"
        private const val TAG_VARIABLE = "Kotlin_Light_Variable"
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
            Regex("^FirLight.*Class.*Symbol:") to "$TAG_CLASS:",

            Regex("^FirLightConstructorForSymbol:.+$") to TAG_METHOD,
            Regex("^FirLight.*Method.*Symbol:.+$") to TAG_METHOD,
            Regex("^KtUltraLightMethodForSourceDeclaration:.+$") to TAG_METHOD,
            Regex("^LightMethodBuilder:.+$") to TAG_METHOD,

            Regex("^KtLightField:.+$") to TAG_VARIABLE,
            Regex("^LightVariableBuilder:.+$") to TAG_VARIABLE,

            Regex("^Fir Light Parameter .+$") to TAG_VALUE_PARAMETER,

            Regex("^FirLightTypeParameter:.+$") to TAG_TYPE_PARAMETER,
            Regex("^Light PSI class: .+$") to TAG_TYPE_PARAMETER,
        )
    }
}
