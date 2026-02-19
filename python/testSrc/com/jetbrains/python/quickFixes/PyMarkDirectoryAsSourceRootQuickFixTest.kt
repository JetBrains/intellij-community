// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.quickFixes

import com.intellij.ide.projectView.actions.MarkRootsManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.TestDataPath
import com.jetbrains.python.PyQuickFixTestCase
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection
import com.jetbrains.python.module.PySourceRootDetectionService

@TestDataPath("\$CONTENT_ROOT/../testData/quickFixes/PyMarkDirectoryAsSourceRootQuickFixTest")
class PyMarkDirectoryAsSourceRootQuickFixTest: PyQuickFixTestCase() {
  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    myFixture.copyDirectoryToProject("", "")
    Registry.get("python.source.root.suggest.quickfix.auto.apply").setValue(true)
    Registry.get("python.source.root.suggest.quickfix").setValue(true)
  }

  override fun tearDown() {
    cleanupSourceRoots()
    myFixture.project.service<PySourceRootDetectionService>().resetState()
    super.tearDown()
  }

  private fun cleanupSourceRoots() {
    WriteAction.run<Throwable> {
      val moduleRootManager = ModuleRootManager.getInstance(myFixture.module)
      val sourceRootsToCleanUp = moduleRootManager.sourceRoots.filter {
        it.path != DEFAULT_ROOT
      }

      val model = ModuleRootManager.getInstance(myFixture.module).modifiableModel
      sourceRootsToCleanUp.forEach { sourceRoot ->
        val entry = MarkRootsManager.findContentEntry(model, sourceRoot) ?: return@forEach
        val toRemove = entry.sourceFolders.firstOrNull { it.file == this || it.url == sourceRoot.url }
        if (toRemove != null) {
          entry.removeSourceFolder(toRemove)
        }
      }
      model.commit()
    }
  }

  fun testNoChangesBecauseFeatureDisabled() {
    Registry.get("python.source.root.suggest.quickfix").setValue(false)

    testSourceRoot(expectedSourceRootPaths = emptySet())
    openAndHighlightFile("mysrc/foo/abc_no_src.py")
    testSourceRoot(expectedSourceRootPaths = emptySet())
    findAndExecuteSourcesQuickFix(isQuickFixExpected = false)
    testSourceRoot(expectedSourceRootPaths = emptySet())
  }

  fun testAutoFixDisabled() {
    Registry.get("python.source.root.suggest.quickfix.auto.apply").setValue(false)

    testSourceRoot(expectedSourceRootPaths = emptySet())
    openAndHighlightFile("mysrc/foo/abc.py")
    testSourceRoot(expectedSourceRootPaths = emptySet())
    findAndExecuteSourcesQuickFix(isQuickFixExpected = true)
    testSourceRoot(expectedSourceRootPaths = setOf("/src/mysrc"))
  }

  fun testUpdatesSourceRootsAutomatically() {
    testSourceRoot(expectedSourceRootPaths = emptySet())
    openAndHighlightFile("mysrc/foo/abc_auto.py")
    testSourceRoot(expectedSourceRootPaths = setOf("/src/mysrc"))
    // quick fix is not expected because it will be already automatically applied
    findAndExecuteSourcesQuickFix(isQuickFixExpected = false)
    testSourceRoot(expectedSourceRootPaths = setOf("/src/mysrc"))
  }

  fun testDoesNotUpdateSourceRootsAutomaticallyIfWasHidden() {
    testSourceRoot(expectedSourceRootPaths = emptySet())
    openAndHighlightFile("mysrc/foo/abc_auto.py")
    testSourceRoot(expectedSourceRootPaths = setOf("/src/mysrc"))

    // Now we remove detected source roots; they should not be added one more time automatically, only via quick fix
    cleanupSourceRoots()

    testSourceRoot(expectedSourceRootPaths = emptySet())
    openAndHighlightFile("mysrc/foo/abc.py")
    testSourceRoot(expectedSourceRootPaths = emptySet())
    // quick fix is expected because it does not matter if the source root was hidden before
    findAndExecuteSourcesQuickFix(isQuickFixExpected = true)
    testSourceRoot(expectedSourceRootPaths = setOf("/src/mysrc"))
  }

  fun testNoQuickFixBecauseSourceRootNotFound() {
    testSourceRoot(expectedSourceRootPaths = emptySet())
    openAndHighlightFile("mysrc/foo/abc_no_src.py")
    testSourceRoot(expectedSourceRootPaths = emptySet())
    // quick fix is not expected because there is no source root to mark
    findAndExecuteSourcesQuickFix(isQuickFixExpected = false)
    testSourceRoot(expectedSourceRootPaths = emptySet())
  }

  fun testNoQuickFixBecauseResolvedToFolderWithoutInitPy() {
    testSourceRoot(expectedSourceRootPaths = emptySet())
    openAndHighlightFile("mysrc/foo/abc_folder_no_init.py")
    testSourceRoot(expectedSourceRootPaths = emptySet())
    findAndExecuteSourcesQuickFix(isQuickFixExpected = true)
    testSourceRoot(expectedSourceRootPaths = setOf("/src/mysrc"))
  }

  fun testNoQuickFixBecauseResolvedToFolderWithInitPy() {
    testSourceRoot(expectedSourceRootPaths = emptySet())
    openAndHighlightFile("mysrc/foo/abc_folder_with_init.py")
    testSourceRoot(expectedSourceRootPaths = setOf("/src/mysrc"))
    // quick fix is not expected because it will be already automatically applied
    findAndExecuteSourcesQuickFix(isQuickFixExpected = false)
    testSourceRoot(expectedSourceRootPaths = setOf("/src/mysrc"))
  }

  private fun testSourceRoot(expectedSourceRootPaths: Set<String>) {
    val moduleRootManager = ModuleRootManager.getInstance(myFixture.module)
    val detectedSourceRoots = moduleRootManager.sourceRoots.map {
      it.path
    }.toSet() - DEFAULT_ROOT
    assertEquals(expectedSourceRootPaths, detectedSourceRoots)
  }

  private fun openAndHighlightFile(pyFilePath: String) {
    myFixture.enableInspections(PyUnresolvedReferencesInspection::class.java)
    myFixture.configureByFile(pyFilePath)
    myFixture.checkHighlighting(true, false, false)
  }

  private fun findAndExecuteSourcesQuickFix(isQuickFixExpected: Boolean, filePath: String? = "mysrc/foo/abc.py") {
    val intentionAction = myFixture.filterAvailableIntentions(QUICK_FIX_NAME_BEGINNING)
    if (!isQuickFixExpected) {
      assertEmpty(intentionAction)
      return
    }
    assertSize(1, intentionAction)
    myFixture.launchAction(intentionAction[0])
    myFixture.checkResultByFile(myFixture.file.virtualFile.path.removePrefix("$DEFAULT_ROOT/").removeSuffix(".py") + "_after.py", true)
  }

  companion object {
    private const val DEFAULT_ROOT = "/src"
    // QFIX.add.source.root.for.unresolved.import.name
    private const val QUICK_FIX_NAME_BEGINNING = "Mark '"
  }
}
