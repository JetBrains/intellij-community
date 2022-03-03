/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.intellij.lang.annotations.Language

abstract class TomlTestBase : BasePlatformTestCase() {
    open val dataPath: String = ""

    override fun getTestDataPath(): String = getTomlTestsResourcesPath().resolve(dataPath).toString()

    @Suppress("TestFunctionName")
    protected fun InlineFile(@Language("TOML") text: String, name: String = "example.toml") {
        myFixture.configureByText(name, text.trimIndent())
    }

    protected fun checkByText(
        @Language("TOML") before: String,
        @Language("TOML") after: String,
        action: () -> Unit
    ) {
        InlineFile(before)
        action()
        myFixture.checkResult(after)
    }

    protected val testName: String
        get() = getTestName(true)

    override fun getTestName(lowercaseFirstLetter: Boolean): String {
        val camelCase = super.getTestName(lowercaseFirstLetter)
        return TestCase.camelOrWordsToSnake(camelCase)
    }
}
