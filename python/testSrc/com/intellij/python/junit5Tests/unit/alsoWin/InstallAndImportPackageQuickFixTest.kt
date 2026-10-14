// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.alsoWin

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.replaceService
import com.jetbrains.python.Result
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection
import com.jetbrains.python.junit5.framework.annotations.InspectionTest
import com.jetbrains.python.junit5.framework.annotations.PyCodeInsightTestApplication
import com.jetbrains.python.packaging.cache.impl.InMemorySearchPage
import com.jetbrains.python.packaging.management.TestPythonPackageManagerService
import com.jetbrains.python.packaging.pip.PyPiPackageCache
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito

@PyCodeInsightTestApplication
@InspectionTest(PyUnresolvedReferencesInspection::class)
internal class InstallAndImportPackageQuickFixTest {

  // PY-42307
  @Test
  fun testInstallAndImportPackageByNameAlias(fixture: CodeInsightTestFixture, @TestDisposable disposable: Disposable) {
    TestPythonPackageManagerService.replacePythonPackageManagerServiceWithTestInstance(fixture.project, availablePackageNames = listOf("pandas"))

    fixture.configureByText("foo.py", "pd<caret>.array()")

    // standard library
    fixture.findSingleIntention("Import 'turtle.pd'")

    // 'pd' is a common import alias for 'pandas' from PyPI
    fixture.findSingleIntention("Install and import package 'pandas'")
  }
}
