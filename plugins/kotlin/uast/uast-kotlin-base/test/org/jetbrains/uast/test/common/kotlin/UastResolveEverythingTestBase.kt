/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.uast.test.common.kotlin

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.asJava.elements.KtLightElement
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
        if (!isFirUastPlugin || element !is KtLightElement<*, *>) return str
        // NB: all declarations in FIR LC are internal, so type checking won't work.
        TAG.forEach { (firLC, uLC) -> str = str.replace(firLC, uLC) }
        REGEX.forEach { (firRegex, uLC) -> str = firRegex.replace(str, uLC) }
        return str
    }

    companion object {
        private val TAG: Map<String, String> = mapOf(
            "FirLightAnnotationClassSymbol" to "KtUltraLightClass",
            "FirLightClassForSymbol" to "KtUltraLightClass",
            "FirLightInterfaceClassSymbol" to "KtUltraLightClass",

            "FirLightSimpleMethodForSymbol" to "KtUltraLightMethodForSourceDeclaration",
            "FirLightAccessorMethodForSymbol" to "KtUltraLightMethodForSourceDeclaration",
            "FirLightConstructorForSymbol" to "KtUltraLightMethodForSourceDeclaration",

            "FirLightTypeParameter:" to "Light PSI class: ",
        )

        private val REGEX: Map<Regex, String> = mapOf(
            Regex("Fir Light Parameter .+$") to "Light Parameter",
        )
    }
}
