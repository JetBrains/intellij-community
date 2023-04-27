// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.test.common.kotlin

import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.uast.UFile
import org.jetbrains.uast.asRecursiveLogString
import org.jetbrains.uast.test.common.kotlin.UastTestSuffix.TXT
import java.io.File

interface UastRenderLogTestBase : UastPluginSelection, UastFileComparisonTestBase {
    private fun getRenderFile(filePath: String, suffix: String): File = getTestMetadataFileFromPath(filePath, "render$suffix")
    private fun getLogFile(filePath: String, suffix: String): File = getTestMetadataFileFromPath(filePath, "log$suffix")

    private fun getIdenticalRenderFile(filePath: String): File = getRenderFile(filePath, TXT)
    private fun getIdenticalLogFile(filePath: String): File = getLogFile(filePath, TXT)

    private fun getPluginRenderFile(filePath: String): File {
        val identicalFile = getIdenticalRenderFile(filePath)
        if (identicalFile.exists()) return identicalFile
        return getRenderFile(filePath, "$pluginSuffix$TXT")
    }

    private fun getPluginLogFile(filePath: String): File {
        val identicalFile = getIdenticalLogFile(filePath)
        if (identicalFile.exists()) return identicalFile
        return getLogFile(filePath, "$pluginSuffix$TXT")
    }

    fun check(filePath: String, file: UFile) {
        val renderFile = getPluginRenderFile(filePath)
        val logFile = getPluginLogFile(filePath)

        KotlinTestUtils.assertEqualsToFile(renderFile, file.asRenderString())
        KotlinTestUtils.assertEqualsToFile(logFile, file.asRecursiveLogString())

        cleanUpIdenticalFile(
            renderFile,
            getRenderFile(filePath, "$counterpartSuffix$TXT"),
            getIdenticalRenderFile(filePath),
            kind = "render"
        )
        cleanUpIdenticalFile(
            logFile,
            getLogFile(filePath, "$counterpartSuffix$TXT"),
            getIdenticalLogFile(filePath),
            kind = "log"
        )
    }
}
