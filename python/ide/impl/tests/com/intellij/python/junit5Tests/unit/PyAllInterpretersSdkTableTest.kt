// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.pycharm.community.ide.impl.configuration.interpreter.listedPythonSdks
import com.intellij.pycharm.community.ide.impl.configuration.interpreter.removeFromSdkTable
import com.intellij.python.junit5Tests.framework.env.pyMockSdkFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The "All Interpreters" page must edit the SDKs of the SDK table, not copies of them.
 * A change to a copy did not reach the table, so Remove (PY-93286) and Interpreter Paths (PY-93168) had no effect.
 */
@TestApplication
internal class PyAllInterpretersSdkTableTest {
  private val project = projectFixture()
  private val sdkFixture = project.pyMockSdkFixture(tempPathFixture())
  private val addedPathFixture = tempPathFixture()

  @Test
  fun `the page lists the SDK of the SDK table`(): Unit = runBlocking {
    val sdk = sdkFixture.get()
    val listed = listedPythonSdks(project.get(), emptySet()).single { it.name == sdk.name }
    assertSame(ProjectJdkTable.getInstance().findJdk(sdk.name), listed)
  }

  @Test
  fun `a pending removal hides the SDK and keeps it in the SDK table`(): Unit = runBlocking {
    val sdk = sdkFixture.get()
    assertTrue(listedPythonSdks(project.get(), setOf(sdk)).none { it.name == sdk.name })
    assertNotNull(ProjectJdkTable.getInstance().findJdk(sdk.name))
  }

  @Test
  fun `remove takes the listed SDK out of the SDK table`(): Unit = runBlocking {
    val sdk = sdkFixture.get()
    val listed = listedPythonSdks(project.get(), emptySet()).single { it.name == sdk.name }
    withContext(Dispatchers.EDT) { removeFromSdkTable(listOf(listed)) }
    assertNull(ProjectJdkTable.getInstance().findJdk(sdk.name))
    assertFalse(listedPythonSdks(project.get(), emptySet()).any { it.name == sdk.name })
  }

  @Test
  fun `a path added to the listed SDK reaches the SDK table`(): Unit = runBlocking {
    val sdk = sdkFixture.get()
    val listed = listedPythonSdks(project.get(), emptySet()).single { it.name == sdk.name }
    val addedPath = withContext(Dispatchers.IO) { VfsUtil.findFile(addedPathFixture.get(), true)!! }
    edtWriteAction {
      val modificator = listed.sdkModificator
      modificator.addRoot(addedPath, OrderRootType.CLASSES)
      modificator.commitChanges()
    }
    val stored = ProjectJdkTable.getInstance().findJdk(sdk.name)!!
    assertTrue(addedPath in stored.rootProvider.getFiles(OrderRootType.CLASSES))
  }
}
