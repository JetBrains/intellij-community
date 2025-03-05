// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.test.kotlin.comparison

import org.jetbrains.uast.test.common.kotlin.LegacyUastRenderLogTestBase
import java.io.File

abstract class AbstractFE1LegacyUastDeclarationTest : AbstractFE1UastDeclarationTest(), LegacyUastRenderLogTestBase {
    override fun getTestMetadataFileFromPath(filePath: String, ext: String): File {
        return super<LegacyUastRenderLogTestBase>.getTestMetadataFileFromPath(filePath, ext)
    }
}
