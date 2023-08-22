// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.test.common.kotlin

import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.uast.UFile
import org.jetbrains.uast.test.common.kotlin.UastTestSuffix.TXT
import java.io.File

interface UastValuesTestBase : UastPluginSelection, UastFileComparisonTestBase {
    private fun getValuesFile(filePath: String, suffix: String): File = getTestMetadataFileFromPath(filePath, "values$suffix")

    private fun getIdenticalValuesFile(filePath: String): File = getValuesFile(filePath, TXT)

    private fun getPluginValuesFile(filePath: String): File {
        val identicalFile = getIdenticalValuesFile(filePath)
        if (identicalFile.exists()) return identicalFile
        return getValuesFile(filePath, "$pluginSuffix$TXT")
    }

    fun check(filePath: String, file: UFile) {
        val valuesFile = getPluginValuesFile(filePath)

        KotlinTestUtils.assertEqualsToFile(valuesFile, file.asLogValues())

        cleanUpIdenticalFile(
            valuesFile,
            getValuesFile(filePath, "$counterpartSuffix$TXT"),
            getIdenticalValuesFile(filePath),
            kind = "values"
        )
    }
}
