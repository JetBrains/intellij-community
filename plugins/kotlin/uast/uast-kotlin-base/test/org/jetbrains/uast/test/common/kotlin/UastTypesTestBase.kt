// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.test.common.kotlin

import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.uast.UFile
import org.jetbrains.uast.test.common.kotlin.UastTestSuffix.TXT
import java.io.File

interface UastTypesTestBase : UastPluginSelection, UastFileComparisonTestBase {
    private fun getTypesFile(filePath: String, suffix: String): File = getTestMetadataFileFromPath(filePath, "types$suffix")

    private fun getIdenticalTypesFile(filePath: String): File = getTypesFile(filePath, TXT)

    private fun getPluginTypesFile(filePath: String): File {
        val identicalFile = getIdenticalTypesFile(filePath)
        if (identicalFile.exists()) return identicalFile
        return getTypesFile(filePath, "$pluginSuffix$TXT")
    }

    fun check(filePath: String, file: UFile) {
        val typesFile = getPluginTypesFile(filePath)

        KotlinTestUtils.assertEqualsToFile(typesFile, file.asLogTypes())

        cleanUpIdenticalFile(
            typesFile,
            getTypesFile(filePath, "$counterpartSuffix$TXT"),
            getIdenticalTypesFile(filePath),
            kind = "types"
        )
    }
}
