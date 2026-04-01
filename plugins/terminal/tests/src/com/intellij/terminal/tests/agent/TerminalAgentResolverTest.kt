// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.agent

import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelExecApi
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.path.EelPath
import com.intellij.terminal.backend.rpc.TerminalAgentResolutionContext
import com.intellij.terminal.backend.rpc.findTerminalAgentBinaryPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.agent.DefaultTerminalAgentProvider
import org.jetbrains.plugins.terminal.agent.TerminalAgent
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(JUnit4::class)
internal class TerminalAgentResolverTest : BasePlatformTestCase() {
  @Test
  fun `claude falls back to eel where on posix`() {
    runBlocking {
      val claude = bundledAgentByKey("claude_code")
      val eelApi = mockEelApi(EelOsFamily.Posix, "claude", "/opt/bin/claude")

      val binaryPath = findTerminalAgentBinaryPath(claude, TerminalAgentResolutionContext(eelApi, EelOsFamily.Posix, emptyMap()))

      assertThat(binaryPath).isEqualTo("/opt/bin/claude")
    }
  }

  @Test
  fun `codex falls back to eel where on posix`() {
    runBlocking {
      val codex = bundledAgentByKey("codex")
      val eelApi = mockEelApi(EelOsFamily.Posix, "codex", "/usr/local/bin/codex")

      val binaryPath = findTerminalAgentBinaryPath(codex, TerminalAgentResolutionContext(eelApi, EelOsFamily.Posix, emptyMap()))

      assertThat(binaryPath).isEqualTo("/usr/local/bin/codex")
    }
  }

  @Test
  fun `codex is unavailable when eel where returns null`() {
    runBlocking {
      val codex = bundledAgentByKey("codex")
      val eelApi = mockEelApi(EelOsFamily.Posix, "codex", null)

      val binaryPath = findTerminalAgentBinaryPath(codex, TerminalAgentResolutionContext(eelApi, EelOsFamily.Posix, emptyMap()))

      assertThat(binaryPath).isNull()
      assertThat(codex.getInstallCommand(EelOsFamily.Posix)).isNull()
    }
  }

  private fun bundledAgentByKey(agentKey: String) = bundledAgentByKey(TerminalAgent.AgentKey(agentKey))

  private fun bundledAgentByKey(agentKey: TerminalAgent.AgentKey) =
    DefaultTerminalAgentProvider().getTerminalAgents().first { it.agentKey == agentKey }

  private suspend fun mockEelApi(osFamily: EelOsFamily, binaryName: String, whereResult: String?): EelApi {
    val descriptor = mock<EelDescriptor>()
    whenever(descriptor.osFamily).thenReturn(osFamily)

    val exec = mock<EelExecApi>()
    whenever(exec.descriptor).thenReturn(descriptor)
    val findResult = whereResult?.let { listOf(EelPath.parse(it, descriptor)) } ?: emptyList()
    whenever(exec.findExeFilesInPath(binaryName)).thenReturn(findResult)

    return mock<EelApi> {
      on { this.exec }.thenReturn(exec)
    }
  }
}
