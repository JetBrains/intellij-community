/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.folding

import org.toml.TomlTestBase

class TomlFoldingBuilderTest : TomlTestBase() {
    override val dataPath = "org/toml/ide/folding/fixtures"

    fun `test table`() = doTest()
    fun `test array table`() = doTest()
    fun `test inline table`() = doTest()
    fun `test array`() = doTest()
    fun `test custom regions`() = doTest()

    private fun doTest() {
        val fileName = "$testName.toml"
        myFixture.testFolding("$testDataPath/$fileName")
    }
}
