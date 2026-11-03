// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver

import com.intellij.execution.services.ServiceEventListener
import com.intellij.execution.services.ServiceViewContributor
import com.intellij.mcpserver.toolwindow.McpDiagnosticService
import com.intellij.mcpserver.toolwindow.McpSessionInfo
import com.intellij.mcpserver.toolwindow.TransportType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.application
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
internal class McpServiceViewContributorTest {
  private val projectFixture = projectFixture()
  private val project by projectFixture

  @Suppress("UNCHECKED_CAST")
  private val contributor = ServiceViewContributor.CONTRIBUTOR_EP_NAME.extensionList.single {
    it.javaClass.name == "com.intellij.mcpserver.frontend.services.McpServiceViewContributor"
  } as ServiceViewContributor<McpSessionInfo>

  @Test
  fun `root descriptors share one tool calls panel`(): Unit = timeoutRunBlocking {
    val editors = withContext(Dispatchers.EDT) {
      val oldEditors = EditorFactory.getInstance().allEditors.toSet()
      repeat(3) { contributor.getViewDescriptor(project).contentComponent }
      EditorFactory.getInstance().allEditors.toSet() - oldEditors
    }
    assertThat(editors).hasSize(2)
  }

  @Test
  fun `session content survives descriptor recreation and is disposed when the session ends`(): Unit = timeoutRunBlocking {
    awaitReset { contributor.getViewDescriptor(project) }
    val diagnosticService = service<McpDiagnosticService>()
    val sessionId = "service-view-reuse"
    try {
      awaitReset { startSession(sessionId) }
      val session = diagnosticService.getSessions().single { it.sessionId == sessionId }
      val descriptors = withContext(Dispatchers.Default) {
        List(3) { contributor.getServiceDescriptor(project, session) }
      }
      val [components, editors] = withContext(Dispatchers.EDT) {
        val oldEditors = EditorFactory.getInstance().allEditors.toSet()
        val components = descriptors.map { it.contentComponent }
        components to (EditorFactory.getInstance().allEditors.toSet() - oldEditors)
      }
      assertThat(components.first()).isNotNull()
      assertThat(components).allSatisfy { assertThat(it).isSameAs(components.first()) }
      assertThat(editors).hasSize(2)

      awaitReset { diagnosticService.sessionEnded(sessionId) }
      assertThat(editors).allSatisfy { assertThat(it.isDisposed).isTrue() }
    }
    finally {
      withContext(Dispatchers.EDT) { diagnosticService.sessionEnded(sessionId) }
    }
  }

  @Test
  fun `a new session with the same ID gets new content`(): Unit = timeoutRunBlocking {
    awaitReset { contributor.getViewDescriptor(project) }
    val diagnosticService = service<McpDiagnosticService>()
    val sessionId = "service-view-replacement"
    try {
      awaitReset { startSession(sessionId) }
      val oldSession = diagnosticService.getSessions().single { it.sessionId == sessionId }
      val [oldContent, oldEditors] = withContext(Dispatchers.EDT) {
        val editors = EditorFactory.getInstance().allEditors.toSet()
        val content = contributor.getServiceDescriptor(project, oldSession).contentComponent
        content to (EditorFactory.getInstance().allEditors.toSet() - editors)
      }

      awaitReset { diagnosticService.sessionEnded(sessionId) }
      awaitReset { startSession(sessionId) }
      val newSession = diagnosticService.getSessions().single { it.sessionId == sessionId }
      val newContent = withContext(Dispatchers.EDT) {
        contributor.getServiceDescriptor(project, newSession).contentComponent
      }
      assertThat(newSession).isNotSameAs(oldSession)
      assertThat(newContent).isNotSameAs(oldContent)
      assertThat(oldEditors).hasSize(2).allSatisfy { assertThat(it.isDisposed).isTrue() }
    }
    finally {
      withContext(Dispatchers.EDT) { diagnosticService.sessionEnded(sessionId) }
    }
  }

  @Test
  fun `closing a project releases content while the session stays active`(): Unit = timeoutRunBlocking {
    awaitReset { contributor.getViewDescriptor(project) }
    val diagnosticService = service<McpDiagnosticService>()
    val sessionId = "service-view-project-close"
    try {
      awaitReset { startSession(sessionId) }
      val session = diagnosticService.getSessions().single { it.sessionId == sessionId }
      val editors = withContext(Dispatchers.EDT) {
        val oldEditors = EditorFactory.getInstance().allEditors.toSet()
        contributor.getViewDescriptor(project).contentComponent
        contributor.getServiceDescriptor(project, session).contentComponent
        EditorFactory.getInstance().allEditors.toSet() - oldEditors
      }

      ProjectManagerEx.getInstanceEx().forceCloseProjectAsync(project, save = false)

      assertThat(diagnosticService.getSessions()).contains(session)
      assertThat(editors).hasSize(4).allSatisfy { assertThat(it.isDisposed).isTrue() }
    }
    finally {
      withContext(Dispatchers.EDT) { diagnosticService.sessionEnded(sessionId) }
    }
  }

  private fun startSession(sessionId: String) {
    service<McpDiagnosticService>().sessionStarted(sessionId, null, TransportType.STREAMABLE_HTTP, System.currentTimeMillis(), null, 0)
  }

  private suspend fun awaitReset(action: () -> Unit) {
    val reset = CompletableDeferred<Unit>()
    val connection = application.messageBus.connect()
    connection.subscribe(ServiceEventListener.TOPIC, ServiceEventListener { event ->
      if (event.contributorClass == contributor.javaClass) {
        reset.complete(Unit)
      }
    })
    try {
      withContext(Dispatchers.EDT) { action() }
      reset.await()
    }
    finally {
      connection.disconnect()
    }
  }
}
