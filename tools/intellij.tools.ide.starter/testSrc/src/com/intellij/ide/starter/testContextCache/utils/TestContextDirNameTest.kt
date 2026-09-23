package com.intellij.ide.starter.testContextCache.utils

import com.intellij.ide.starter.ide.IdeRunMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TestContextDirNameTest {
  @Test
  fun `a name is hyphenated and prefixed with the run mode`() {
    assertEquals("correct-classpath-test", testContextDirName(IdeRunMode.MONOLITH, "CorrectClasspathTest"))
    assertEquals("split-correct-classpath-test", testContextDirName(IdeRunMode.SPLIT, "CorrectClasspathTest"))
  }

  @Test
  fun `a name spells the run mode once`() {
    assertEquals(
      "split-plugin-content-module-loading-test",
      testContextDirName(IdeRunMode.SPLIT, "SplitPluginContentModuleLoadingTest"),
    )
    // a class which only begins like the mode keeps the prefix: `splitter` is a word of its own, not the mode
    assertEquals("split-splitter-test", testContextDirName(IdeRunMode.SPLIT, "SplitterTest"))
    // a class named after the mode at its end names it just as much
    assertEquals("editor-tabs-split", testContextDirName(IdeRunMode.SPLIT, "EditorTabsSplit"))
    assertEquals(
      "monolith-plugin-content-module-loading-test",
      testContextDirName(IdeRunMode.MONOLITH, "MonolithPluginContentModuleLoadingTest"),
    )
  }
}
