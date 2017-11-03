/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

class TomlAnnotatorTest : LightCodeInsightFixtureTestCase() {
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
