/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.uast.test.common.kotlin

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

        KotlinTestUtils.assertEqualsToFile(resolvedFile, file.resolvableWithTargets())

        cleanUpIdenticalFile(
            resolvedFile,
            getResolvedFile(filePath, "$counterpartSuffix$TXT"),
            getIdenticalResolvedFile(filePath),
            kind = "resolved"
        )
    }
}
