// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.structureView

import com.intellij.ide.structureView.newStructureView.StructureViewComponent
import com.intellij.openapi.ui.Queryable.PrintInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.tree.TreeUtil
import org.intellij.plugins.markdown.MarkdownTestingUtil
import javax.swing.tree.TreePath

class MarkdownOnlyHeadersInStructureViewTest : BasePlatformTestCase() {
  override fun getTestDataPath(): String {
    return MarkdownTestingUtil.TEST_DATA_PATH + "/structureView/withOnlyHeaders/"
  }

  fun doTest() {
    Registry.get("markdown.structure.view.list.visibility").setValue(false)
    myFixture.configureByFile(getTestName(true) + ".md")
    myFixture.testStructureView { svc: StructureViewComponent ->
      val tree = svc.tree
      TreeUtil.expandAll(tree)
      PlatformTestUtil.waitForPromise(svc.select(svc.treeModel.currentEditorElement, false))
      assertSameLinesWithFile(
        testDataPath + '/' + getTestName(true) + ".txt",
        PlatformTestUtil.print(tree, TreePath(tree.model.root), PrintInfo(null, null), true))
    }
  }

  fun testHeadersUnderBlockquotesAndLists() {
    doTest()
  }

  fun testNormalATXDocument() {
    doTest()
  }

  fun testPuppetlabsCoreTypes() {
    doTest()
  }
}
