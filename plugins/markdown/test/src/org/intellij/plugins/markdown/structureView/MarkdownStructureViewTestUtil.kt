// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.structureView

import com.intellij.ide.structureView.newStructureView.StructureViewComponent
import com.intellij.openapi.ui.Queryable
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.StructureViewComponentTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.annotations.TestOnly
import javax.swing.tree.TreePath

/**
 * Checks the structure view of a Markdown file against a text file.
 * The test class sets the `markdown.structure.view.list.visibility` registry key with `@RegistryKey`.
 */
@TestOnly
internal object MarkdownStructureViewTestUtil {
  /**
   * Configures the `<testName>.md` file of the test data, and compares the structure view tree with the `<testName>.txt` file.
   */
  fun doTest(fixture: CodeInsightTestFixture, testName: String) {
    fixture.configureByFile("$testName.md")
    StructureViewComponentTestUtil.testStructureView(fixture) { svc: StructureViewComponent ->
      val tree = svc.tree
      PlatformTestUtil.expandAll(tree)
      PlatformTestUtil.waitForPromise(svc.select(svc.treeModel.currentEditorElement, false))
      PlatformTestUtil.assertSameLinesWithFile(
        fixture.testDataPath + '/' + testName + ".txt",
        PlatformTestUtil.print(tree, TreePath(tree.model.root), Queryable.PrintInfo(null, null), true))
    }
  }
}
