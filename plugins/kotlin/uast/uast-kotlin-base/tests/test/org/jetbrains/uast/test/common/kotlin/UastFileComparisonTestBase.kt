// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.test.common.kotlin

import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import java.io.File

interface UastFileComparisonTestBase {
    fun getTestMetadataFileFromPath(filePath: String, ext: String): File {
        return File(filePath.removeSuffix(".kt") + '.' + ext)
    }

    private val isTeamCityBuild: Boolean
        get() = System.getenv("TEAMCITY_VERSION") != null
                || KotlinTestUtils.IS_UNDER_TEAMCITY

}
