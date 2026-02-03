// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.test.kotlin.comparison

import org.jetbrains.uast.UFile
import org.jetbrains.uast.test.common.kotlin.UastRenderLogTestBase
import org.jetbrains.uast.test.kotlin.env.AbstractFE1UastTest
import java.io.File

abstract class AbstractFE1UastDeclarationTest : AbstractFE1UastTest(), UastRenderLogTestBase {

    override fun check(filePath: String, file: UFile) {
        super.check(filePath, file)
    }

    override fun getTestMetadataFileFromPath(filePath: String, ext: String): File {
        return File(filePath.removeSuffix(".kt") + '.' + ext)
    }
}
