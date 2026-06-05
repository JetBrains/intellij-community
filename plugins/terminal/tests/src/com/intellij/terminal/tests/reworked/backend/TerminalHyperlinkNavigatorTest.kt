// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.backend

import com.intellij.execution.filters.FileHyperlinkInfoBase
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.ide.navigation.NavigationOptions
import com.intellij.platform.ide.navigation.NavigationService
import com.intellij.pom.Navigatable
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.hyperlinks.TerminalHyperlinkNavigator
import org.junit.jupiter.api.Test

@TestApplication
internal class TerminalHyperlinkNavigatorTest {
  private val testProject = projectFixture()
  private val testModule = testProject.moduleFixture()
  private val sourceRoot = testModule.sourceRootFixture()

  private val project get() = testProject.get()

  @Test
  fun `directory hyperlink info base uses async file navigation`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    val navigationService = RecordingNavigationService()
    project.replaceService(NavigationService::class.java, navigationService, disposable)
    val hyperlinkInfo = SyncFailingFileHyperlinkInfo(project, sourceRoot.get().virtualFile)

    TerminalHyperlinkNavigator.navigate(project, hyperlinkInfo, mouseEvent = null)

    assertThat(hyperlinkInfo.syncNavigateCalls).isZero()
    assertThat(navigationService.requestCalls).isEqualTo(1)
    assertThat(navigationService.navigatableCalls).isZero()
  }
}

private class SyncFailingFileHyperlinkInfo(project: Project, override val virtualFile: VirtualFile) :
  FileHyperlinkInfoBase(project, 0, 0) {
  var syncNavigateCalls: Int = 0
    private set

  override fun navigate(project: Project) {
    syncNavigateCalls++
    error("Sync navigation should not be used for terminal file hyperlinks")
  }
}

private class RecordingNavigationService : NavigationService {
  var requestCalls: Int = 0
    private set
  var navigatableCalls: Int = 0
    private set

  override suspend fun navigate(dataContext: DataContext, options: NavigationOptions) {
    error("Unexpected data-context navigation")
  }

  override suspend fun navigate(request: NavigationRequest, options: NavigationOptions, dataContext: DataContext?) {
    requestCalls++
  }

  override suspend fun navigate(navigatables: List<Navigatable>, options: NavigationOptions, dataContext: DataContext?): Boolean {
    navigatableCalls++
    return true
  }
}
