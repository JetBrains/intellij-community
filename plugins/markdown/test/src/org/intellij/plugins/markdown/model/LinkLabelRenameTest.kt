// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.model

import com.intellij.model.psi.impl.targetSymbols
import com.intellij.openapi.util.registry.Registry
import com.intellij.refactoring.rename.api.RenameTarget
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class LinkLabelRenameTest: BasePlatformTestCase() {
  @Test
  fun `test renaming original element updates markdown reference`() {
    doRename(
      before = """
        Here is a link to [Google][google].

        [goo<caret>gle]: https://google.com
      """,
      oldName = "google",
      newName = "Search",
      after = """
        Here is a link to [Google][Search].

        [Search]: https://google.com
      """
    )
  }

  @Test
  fun `test renaming markdown reference updates original element`() {
    doRename(
      before = """
        Here is a link to [Google][goo<caret>gle].

        [google]: https://google.com
      """,
      oldName = "google",
      newName = "Search",
      after = """
        Here is a link to [Google][Search].

        [Search]: https://google.com
      """
    )
  }

  @Test
  fun `test renaming link label updates all references`() {
    doRename(
      before = """
        [First link][google]
        [Second link][google]

        [goo<caret>gle]: https://google.com
      """,
      oldName = "google",
      newName = "Search Engine",
      after = """
        [First link][Search Engine]
        [Second link][Search Engine]

        [Search Engine]: https://google.com
      """
    )
  }

  @Test
  fun `test renaming collapsed reference updates original element`() {
    Registry.get("markdown.validate.short.links").setValue(true, testRootDisposable)
    doRename(
      before = """
        Here is a link to [Goo<caret>gle][].

        [Google]: https://google.com
      """,
      oldName = "Google",
      newName = "Search",
      after = """
        Here is a link to [Search][].

        [Search]: https://google.com
      """
    )
  }

  private fun doRename(before: String, oldName: String, newName: String, after: String) {
    myFixture.configureByText("some.md", before.trimIndent())
    val target = findRenameTargetAtCaret()
    println("Target: ${target::class.java}")
    assertEquals(oldName, target.targetName)

    myFixture.renameTarget(target, newName)
    myFixture.checkResult(after.trimIndent())
  }

  private fun findRenameTargetAtCaret(): RenameTarget =
    targetSymbols(myFixture.file, myFixture.caretOffset)
      .filterIsInstance<RenameTarget>()
      .firstOrNull()
    ?: error("Failed to find rename target at caret")
}
