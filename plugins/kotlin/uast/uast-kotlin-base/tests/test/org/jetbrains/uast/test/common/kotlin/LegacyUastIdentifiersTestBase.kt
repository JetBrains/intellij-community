// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.uast.test.common.kotlin

import java.io.File

interface LegacyUastIdentifiersTestBase : UastIdentifiersTestBase {
    override fun getTestMetadataFileFromPath(filePath: String, ext: String): File {
        // We're using test files from .../uast-kotlin/tests/testData/...
        // but want to store metadata under .../uast-kotlin-fir/tests/testData/legacyIdentifiers/...
        val revisedFilePath = filePath.replace("uast-kotlin", "uast-kotlin-fir")
            .replace("testData", "testData${File.separatorChar}legacyIdentifiers")
        return super.getTestMetadataFileFromPath(revisedFilePath, ext)
    }
}
