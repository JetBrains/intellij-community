package org.intellij.plugins.markdown.model

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class SingleFileHeaderRenameTest: HeaderSymbolTest("model/headers/rename/file") {
  @Test
  fun `rename header with single reference`() = renameSymbolTest()

  @Test
  fun `rename header with multiple references`() = renameSymbolTest()

  @Test
  fun `rename header with single uppercase anchor`() = renameSymbolTest()
}
