/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

// BACKCOMPAT: 2019.1
@file:Suppress("DEPRECATION")

package org.toml.ide

import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase

// BACKCOMPAT: 2019.1. Use BasePlatformTestCase instead
class TomlAnnotatorTest : LightPlatformCodeInsightFixtureTestCase() {
    fun `test inline tables`() = doTest("""
        a = {something = "", another = ""}
        a = <error>{something = "",
            another = ""}</error>
    """)

    private fun doTest(text: String) {
        myFixture.configureByText("example.toml", text)
        myFixture.testHighlighting(true, true, true)
    }
}
