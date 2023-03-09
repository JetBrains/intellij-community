// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.structureView

import com.intellij.ide.structureView.newStructureView.StructureViewComponent
import com.intellij.openapi.ui.Queryable
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import javax.swing.tree.TreePath

abstract class MarkdownStructureViewTestCase : BasePlatformTestCase() {
  abstract override fun getTestDataPath(): String?
  open fun doTest(listVisibility: Boolean = false) {
    Registry.get("markdown.structure.view.list.visibility").setValue(listVisibility)
    myFixture.configureByFile(getTestName(true) + ".md")
    myFixture.testStructureView { svc: StructureViewComponent ->
      val tree = svc.tree
      PlatformTestUtil.expandAll(tree)
      PlatformTestUtil.waitForPromise(svc.select(svc.treeModel.currentEditorElement, false))
      assertSameLinesWithFile(
        testDataPath + '/' + getTestName(true) + ".txt",
        PlatformTestUtil.print(tree, TreePath(tree.model.root), Queryable.PrintInfo(null, null), true))
    }
  }
}
