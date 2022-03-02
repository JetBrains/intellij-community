// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.test.common.kotlin

import java.io.File

interface LegacyUastValuesTestBase : UastValuesTestBase {
    override fun getTestMetadataFileFromPath(filePath: String, ext: String): File {
        // We're using test files from .../uast-kotlin/tests/testData/...
        // but want to store metadata under .../uast-kotlin-fir/testData/legacyValues/...
        val revisedFilePath = filePath.replace("uast-kotlin/tests", "uast-kotlin-fir").replace("testData", "testData/legacyValues")
        return super.getTestMetadataFileFromPath(revisedFilePath, ext)
    }
}
