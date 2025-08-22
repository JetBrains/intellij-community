// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.quickFixes

import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.testFramework.TestDataPath
import com.jetbrains.python.PyQuickFixTestCase
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection

@TestDataPath("\$CONTENT_ROOT/../testData/quickFixes/PyMarkDirectoryAsSourceRootQuickFixTest")
class PyMarkDirectoryAsSourceRootQuickFixTest: PyQuickFixTestCase() {
  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    myFixture.copyDirectoryToProject("", "")
  }

  fun testUpdatesSourceRoots() = doSourceRootTest(pyFilePath = "mysrc/foo/abc.py", expectedSourceRootPath = "mysrc")

  fun testNoQuickFixBecauseSourceRootNotFound() = doSourceRootTest(pyFilePath = "mysrc/foo/abc_no_src.py", expectedSourceRootPath = null)

  private fun doSourceRootTest(
    pyFilePath: String,
    expectedSourceRootPath: String?
  ) {
    myFixture.enableInspections(PyUnresolvedReferencesInspection::class.java)
    myFixture.configureByFile(pyFilePath)
    myFixture.checkHighlighting(true, false, false)
    val intentionAction = myFixture.filterAvailableIntentions(QUICK_FIX_NAME_BEGINNING)
    if (expectedSourceRootPath == null) {
      assertEmpty(intentionAction)
      return
    }
    assertSize(1, intentionAction)
    myFixture.launchAction(intentionAction[0])
    myFixture.checkResultByFile(pyFilePath.removeSuffix(".py") + "_after.py", true)

    val moduleRootManager = ModuleRootManager.getInstance(myFixture.module)
    val sourceRoot = moduleRootManager.sourceRoots.firstOrNull {
      it.path == TEST_PROJECT_ROOT_PATH + expectedSourceRootPath
    }
    assertNotNull("Source root '${expectedSourceRootPath}' not found.", sourceRoot)
  }

  companion object {
    private const val TEST_PROJECT_ROOT_PATH = "/src/"
    // QFIX.add.source.root.for.unresolved.import.name
    private const val QUICK_FIX_NAME_BEGINNING = "Mark '"
  }
}
