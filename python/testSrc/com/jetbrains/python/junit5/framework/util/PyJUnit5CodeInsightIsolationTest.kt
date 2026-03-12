// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.junit5.framework.util

import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.psi.PsiFile
import com.intellij.python.junit5Tests.framework.metaInfo.Repository
import com.intellij.python.junit5Tests.framework.metaInfo.TestClassInfo
import com.intellij.python.junit5Tests.framework.metaInfo.TestMetaInfo
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.jetbrains.python.inspections.PyTypeCheckerInspection
import com.jetbrains.python.junit5.framework.annotations.InspectionTest
import com.jetbrains.python.junit5.framework.annotations.MultiFileTest
import com.jetbrains.python.junit5.framework.annotations.PyCodeInsightTestApplication
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@TestClassInfo(Repository.PY_COMMUNITY)
@TestDataPath("\$CONTENT_ROOT/../testData/junit5/showcase/PyTypeCheckerInspection")
@PyCodeInsightTestApplication
@InspectionTest(PyTypeCheckerInspection::class)
class PyJUnit5CodeInsightIsolationTest {

  @Order(1)
  @MultiFileTest
  fun multiFileTest(mainFile: PsiFile, fixture: CodeInsightTestFixture) {
    assertSingleManagedSourceRoot(fixture)
    Assertions.assertNotNull(fixture.findFileInTempDir("multiFileTest/lib.py"))
    fixture.doTestByFile(mainFile)
  }

  @Test
  @Order(2)
  @TestMetaInfo("single.py")
  fun nextTestStartsWithCleanTempDir(mainFile: PsiFile, fixture: CodeInsightTestFixture) {
    assertSingleManagedSourceRoot(fixture)
    Assertions.assertNull(fixture.tempDirFixture.getFile("multiFileTest/lib.py"))
    fixture.doTestByFile(mainFile)
  }

  private fun assertSingleManagedSourceRoot(fixture: CodeInsightTestFixture) {
    val rootModel = ModuleRootManager.getInstance(fixture.module)
    val contentEntriesWithSourceFolders = rootModel.contentEntries.filter { it.sourceFolders.isNotEmpty() }
    Assertions.assertEquals(
      1,
      contentEntriesWithSourceFolders.size,
      "Expected a single managed content entry, but got: ${contentEntriesWithSourceFolders.map { it.url }}",
    )
    Assertions.assertEquals(
      1,
      rootModel.sourceRoots.size,
      "Expected a single source root, but got: ${rootModel.sourceRoots.map { it.path }}",
    )
  }
}