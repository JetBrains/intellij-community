package org.toml.grazie

import com.intellij.grazie.grazieTestFixture
import com.intellij.grazie.jlanguage.Lang
import com.intellij.platform.bazel.runfiles.BazelLabel
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.common.BazelTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Test
import java.nio.file.Path

@TestApplication
class TomlGrazieSupportTest {
    companion object {
        private val projectFixture = projectFixture(openAfterCreation = true)
    }

    private val grazie by grazieTestFixture(projectFixture, testDataPath = tomlGrazieTestDataPath())

    @Test
    fun `test comments`(): Unit = grazie.runHighlightTestForFile("comments.toml")
    @Test
    fun `test literals`()  {
        grazie.enableProofreadingFor(setOf(Lang.RUSSIAN, Lang.GERMANY_GERMAN))
        grazie.runHighlightTestForFile("literals.toml")
    }
    @Test
    fun `test keys`(): Unit = grazie.runHighlightTestForFile("keys.toml")
}

private fun tomlGrazieTestDataPath(): String {
    if (BazelTestUtil.isUnderBazelTest) {
        val label = BazelLabel.fromString("@community//plugins/toml/tests:testData")
        return BazelTestUtil.getFileFromBazelRuntime(label).resolve("grazie").toString()
    }
    return Path.of(PlatformTestUtil.getCommunityPath(), "plugins/toml/tests/testData/grazie").toString()
}