/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml

import com.intellij.testFramework.PlatformTestUtil
import java.nio.file.Path

interface TestCase {
    val testFileExtension: String
    fun getTestDataPath(): String
    fun getTestName(lowercaseFirstLetter: Boolean): String

    companion object {
        const val testResourcesPath = "resources"

        @JvmStatic
        fun camelOrWordsToSnake(name: String): String {
            if (' ' in name) return name.trim().replace(" ", "_")

            return name.split("(?=[A-Z])".toRegex()).joinToString("_", transform = String::toLowerCase)
        }
    }
}

fun getTomlTestsResourcesPath(): Path =
    Path.of(PlatformTestUtil.getCommunityPath(), "plugins/toml/core/src/test", TestCase.testResourcesPath)

fun TestCase.pathToSourceTestFile(): Path =
    getTomlTestsResourcesPath().resolve("${getTestDataPath()}/${getTestName(true)}.$testFileExtension")

fun TestCase.pathToGoldTestFile(): Path =
    getTomlTestsResourcesPath().resolve("${getTestDataPath()}/${getTestName(true)}.txt")
