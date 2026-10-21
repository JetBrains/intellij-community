package org.toml.grazie

import com.intellij.grazie.GrazieTestBase
import com.intellij.grazie.jlanguage.Lang
import com.intellij.platform.bazel.runfiles.BazelLabel
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.common.BazelTestUtil
import java.nio.file.Path

class TomlGrazieSupportTest : GrazieTestBase() {

    override fun getTestDataPath(): String {
        if (BazelTestUtil.isUnderBazelTest) {
            val label = BazelLabel.fromString("@community//plugins/toml/tests:testData")
            return BazelTestUtil.getFileFromBazelRuntime(label).resolve("grazie").toString()
        }
        return Path.of(PlatformTestUtil.getCommunityPath(), "plugins/toml/tests/testData/grazie").toString()
    }

    fun `test comments`() = runHighlightTestForFile("comments.toml")
    fun `test literals`()  {
        enableProofreadingFor(setOf(Lang.RUSSIAN, Lang.GERMANY_GERMAN))
        runHighlightTestForFile("literals.toml")
    }
    fun `test keys`() = runHighlightTestForFile("keys.toml")
}
