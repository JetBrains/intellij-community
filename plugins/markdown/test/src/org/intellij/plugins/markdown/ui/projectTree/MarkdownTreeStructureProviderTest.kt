// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.projectTree

import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.markdown.backend.projectTree.MarkdownTreeStructureProvider
import com.intellij.mock.MockPsiFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import org.intellij.plugins.markdown.settings.MarkdownSettings
import org.junit.jupiter.api.Test

@TestApplication
class MarkdownTreeStructureProviderTest {
  private val project by projectFixture()

  @Test
  @PerformanceUnitTest
  fun `file grouping reads file names fast`() {
    val files = List(100) { LightVirtualFile("file$it.md") }
    val psiManager = PsiManager.getInstance(project)
    val children = files.mapTo(mutableListOf<AbstractTreeNode<*>>()) {
      PsiFileNode(project, MockPsiFile(it, psiManager), null)
    }

    val settings = MarkdownSettings.getInstance(project)
    val wasGroupingEnabled = settings.isFileGroupingEnabled
    try {
      settings.isFileGroupingEnabled = true
      val provider = MarkdownTreeStructureProvider(project)
      Benchmark.newBenchmark("Modify via MarkdownTreeStructureProvider") { provider.modify(children.first(), children, null) }
        .setup { PsiManager.getInstance(project).dropPsiCaches() }
        .runAsStressTest()
        .start()
    } finally {
      settings.isFileGroupingEnabled = wasGroupingEnabled
    }
  }
}
