package org.toml.grazie

import com.intellij.grazie.GrazieTestBase
import com.intellij.grazie.jlanguage.Lang

class TomlGrazieSupportTest : GrazieTestBase() {

    override fun getBasePath(): String = "plugins/toml/tests/testData/grazie"
    override fun isCommunity(): Boolean = true

    fun `test comments`() = runHighlightTestForFile("comments.toml")
    fun `test literals`()  {
        enableProofreadingFor(setOf(Lang.RUSSIAN, Lang.GERMANY_GERMAN))
        runHighlightTestForFile("literals.toml")
    }
    fun `test keys`() = runHighlightTestForFile("keys.toml")
}
