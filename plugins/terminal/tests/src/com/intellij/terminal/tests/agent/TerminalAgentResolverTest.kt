// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.agent

import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelExecApi
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.EelResult
import com.intellij.platform.eel.EelUserInfo
import com.intellij.platform.eel.fs.EelFileInfo
import com.intellij.platform.eel.fs.EelFileSystemApi
import com.intellij.platform.eel.path.EelPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.agent.DefaultTerminalAgentProvider
import org.jetbrains.plugins.terminal.agent.TerminalAgent
import org.jetbrains.plugins.terminal.agent.findTerminalAgentBinaryPath
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(JUnit4::class)
internal class TerminalAgentResolverTest : BasePlatformTestCase() {
  @Test
  fun `claude found in PATH on posix`() {
    runBlocking {
      val claude = bundledAgentByKey("claude_code")
      val eelApi = mockEelApi(EelOsFamily.Posix, "claude", listOf("/opt/bin/claude"))

      val binaryPath = findTerminalAgentBinaryPath(claude, eelApi)

      assertThat(binaryPath).isEqualTo("/opt/bin/claude")
    }
  }

  @Test
  fun `codex found in PATH on posix`() {
    runBlocking {
      val codex = bundledAgentByKey("codex")
      val eelApi = mockEelApi(EelOsFamily.Posix, "codex", listOf("/usr/local/bin/codex"))

      val binaryPath = findTerminalAgentBinaryPath(codex, eelApi)

      assertThat(binaryPath).isEqualTo("/usr/local/bin/codex")
    }
  }

  @Test
  fun `first candidate is returned when multiple found`() {
    runBlocking {
      val codex = bundledAgentByKey("codex")
      val eelApi = mockEelApi(EelOsFamily.Posix, "codex", listOf("/usr/local/bin/codex", "/opt/bin/codex"))

      val binaryPath = findTerminalAgentBinaryPath(codex, eelApi)

      assertThat(binaryPath).isEqualTo("/usr/local/bin/codex")
    }
  }

  @Test
  fun `codex is unavailable when not found in PATH (Unix)`() {
    runBlocking {
      val codex = bundledAgentByKey("codex")
      val eelApi = mockEelApi(EelOsFamily.Posix, "codex", emptyList())

      val binaryPath = findTerminalAgentBinaryPath(codex, eelApi)

      assertThat(binaryPath).isNull()
      assertThat(codex.getInstallCommand(EelOsFamily.Posix)).isNull()
    }
  }

  @Test
  fun `codex is unavailable when not found in PATH (Windows)`() {
    runBlocking {
      val codex = bundledAgentByKey("codex")
      val eelApi = mockEelApi(EelOsFamily.Windows, "codex", emptyList())

      val binaryPath = findTerminalAgentBinaryPath(codex, eelApi)

      assertThat(binaryPath).isNull()
    }
  }

  @Test
  fun `windows prefers exe over cmd and ps1`() {
    runBlocking {
      val codex = bundledAgentByKey("codex")
      val eelApi = mockEelApi(EelOsFamily.Windows, "codex", listOf(
        "C:\\bin\\codex.ps1",
        "C:\\bin\\codex.cmd",
        "C:\\bin\\codex.exe",
      ))

      val binaryPath = findTerminalAgentBinaryPath(codex, eelApi)

      assertThat(binaryPath).endsWith("codex.exe")
    }
  }

  @Test
  fun `windows picks recognized extension over no extension`() {
    runBlocking {
      val codex = bundledAgentByKey("codex")
      val eelApi = mockEelApi(EelOsFamily.Windows, "codex", listOf(
        "C:\\bin\\codex",
        "C:\\bin\\codex.cmd",
      ))

      val binaryPath = findTerminalAgentBinaryPath(codex, eelApi)

      assertThat(binaryPath).endsWith("codex.cmd")
    }
  }

  @Test
  fun `windows finds codex in roaming npm known location`() {
    runBlocking {
      val codex = bundledAgentByKey("codex")
      val homePath = "C:\\Users\\Someone.Else"
      val expectedPath = "$homePath\\AppData\\Roaming\\npm\\codex.exe"
      val eelApi = mockEelApi(
        osFamily = EelOsFamily.Windows,
        binaryName = "codex",
        pathResults = emptyList(),
        homePath = homePath,
        existingFiles = setOf(expectedPath),
      )

      val binaryPath = findTerminalAgentBinaryPath(codex, eelApi)

      assertThat(binaryPath).isEqualTo(expectedPath)
    }
  }

  @Test
  fun `windows ignores codex in local bin known location`() {
    runBlocking {
      val codex = bundledAgentByKey("codex")
      val homePath = "C:\\Users\\Someone.Else"
      val localBinPath = "$homePath\\.local\\bin\\codex.cmd"
      val eelApi = mockEelApi(
        osFamily = EelOsFamily.Windows,
        binaryName = "codex",
        pathResults = emptyList(),
        homePath = homePath,
        existingFiles = setOf(localBinPath),
      )

      val binaryPath = findTerminalAgentBinaryPath(codex, eelApi)

      assertThat(binaryPath).isNull()
    }
  }

  @Test
  fun `windows checks claude known locations in order`() {
    runBlocking {
      val claude = bundledAgentByKey("claude_code")
      val homePath = "C:\\Users\\Someone.Else"
      val firstKnownLocation = "$homePath\\AppData\\Roaming\\npm\\claude.cmd"
      val secondKnownLocation = "$homePath\\.local\\bin\\claude.exe"
      val eelApi = mockEelApi(
        osFamily = EelOsFamily.Windows,
        binaryName = "claude",
        pathResults = emptyList(),
        homePath = homePath,
        existingFiles = setOf(firstKnownLocation, secondKnownLocation),
      )

      val binaryPath = findTerminalAgentBinaryPath(claude, eelApi)

      assertThat(binaryPath).isEqualTo(firstKnownLocation)
    }
  }

  @Test
  fun `windows prefers codex found in PATH over known locations`() {
    runBlocking {
      val codex = bundledAgentByKey("codex")
      val homePath = "C:\\Users\\Someone.Else"
      val eelApi = mockEelApi(
        osFamily = EelOsFamily.Windows,
        binaryName = "codex",
        pathResults = listOf("C:\\bin\\codex.ps1"),
        homePath = homePath,
        existingFiles = setOf("$homePath\\AppData\\Roaming\\npm\\codex.exe"),
      )

      val binaryPath = findTerminalAgentBinaryPath(codex, eelApi)

      assertThat(binaryPath).isEqualTo("C:\\bin\\codex.ps1")
    }
  }

  @Test
  fun `windows finds claude in local bin known location`() {
    runBlocking {
      val claude = bundledAgentByKey("claude_code")
      val homePath = "C:\\Users\\Someone.Else"
      val expectedPath = "$homePath\\.local\\bin\\claude.cmd"
      val eelApi = mockEelApi(
        osFamily = EelOsFamily.Windows,
        binaryName = "claude",
        pathResults = emptyList(),
        homePath = homePath,
        existingFiles = setOf(expectedPath),
      )

      val binaryPath = findTerminalAgentBinaryPath(claude, eelApi)

      assertThat(binaryPath).isEqualTo(expectedPath)
    }
  }

  @Test
  fun `posix finds codex in home local bin known location`() {
    runBlocking {
      val codex = bundledAgentByKey("codex")
      val homePath = "/home/Someone.Else"
      val expectedPath = "$homePath/.local/bin/codex"
      val eelApi = mockEelApi(
        osFamily = EelOsFamily.Posix,
        binaryName = "codex",
        pathResults = emptyList(),
        homePath = homePath,
        existingFiles = setOf(expectedPath),
      )

      val binaryPath = findTerminalAgentBinaryPath(codex, eelApi)

      assertThat(binaryPath).isEqualTo(expectedPath)
    }
  }

  @Test
  fun `posix finds codex in usr local bin known location`() {
    runBlocking {
      val codex = bundledAgentByKey("codex")
      val eelApi = mockEelApi(
        osFamily = EelOsFamily.Posix,
        binaryName = "codex",
        pathResults = emptyList(),
        existingFiles = setOf("/usr/local/bin/codex"),
      )

      val binaryPath = findTerminalAgentBinaryPath(codex, eelApi)

      assertThat(binaryPath).isEqualTo("/usr/local/bin/codex")
    }
  }

  @Test
  fun `posix finds claude in usr local bin known location`() {
    runBlocking {
      val claude = bundledAgentByKey("claude_code")
      val eelApi = mockEelApi(
        osFamily = EelOsFamily.Posix,
        binaryName = "claude",
        pathResults = emptyList(),
        existingFiles = setOf("/usr/local/bin/claude"),
      )

      val binaryPath = findTerminalAgentBinaryPath(claude, eelApi)

      assertThat(binaryPath).isEqualTo("/usr/local/bin/claude")
    }
  }

  @Test
  fun `posix prefers home local bin over usr local bin for codex`() {
    runBlocking {
      val codex = bundledAgentByKey("codex")
      val homePath = "/home/Someone.Else"
      val firstKnownLocation = "$homePath/.local/bin/codex"
      val secondKnownLocation = "/usr/local/bin/codex"
      val eelApi = mockEelApi(
        osFamily = EelOsFamily.Posix,
        binaryName = "codex",
        pathResults = emptyList(),
        homePath = homePath,
        existingFiles = setOf(firstKnownLocation, secondKnownLocation),
      )

      val binaryPath = findTerminalAgentBinaryPath(codex, eelApi)

      assertThat(binaryPath).isEqualTo(firstKnownLocation)
    }
  }

  @Test
  fun `junie stays unavailable when only usr local bin contains it on posix`() {
    runBlocking {
      val junie = bundledAgentByKey("junie")
      val eelApi = mockEelApi(
        osFamily = EelOsFamily.Posix,
        binaryName = "junie",
        pathResults = emptyList(),
        existingFiles = setOf("/usr/local/bin/junie"),
      )

      val binaryPath = findTerminalAgentBinaryPath(junie, eelApi)

      assertThat(binaryPath).isNull()
    }
  }

  @Test
  fun `junie finds bat entry on windows`() {
    runBlocking {
      val junie = bundledAgentByKey("junie")
      val homePath = "C:\\Users\\Someone.Else"
      val expectedPath = "$homePath\\.local\\bin\\junie.bat"
      val eelApi = mockEelApi(
        osFamily = EelOsFamily.Windows,
        binaryName = "junie",
        pathResults = emptyList(),
        homePath = homePath,
        existingFiles = setOf(expectedPath),
      )

      val binaryPath = findTerminalAgentBinaryPath(junie, eelApi)

      assertThat(binaryPath).isEqualTo(expectedPath)
    }
  }

  @Test
  fun `posix prefers codex found in PATH over known locations`() {
    runBlocking {
      val codex = bundledAgentByKey("codex")
      val homePath = "/home/Someone.Else"
      val eelApi = mockEelApi(
        osFamily = EelOsFamily.Posix,
        binaryName = "codex",
        pathResults = listOf("/opt/codex/codex"),
        homePath = homePath,
        existingFiles = setOf("$homePath/.local/bin/codex", "/usr/local/bin/codex"),
      )

      val binaryPath = findTerminalAgentBinaryPath(codex, eelApi)

      assertThat(binaryPath).isEqualTo("/opt/codex/codex")
    }
  }

  @Test
  fun `windows matches binary name case-insensitively in known location`() {
    runBlocking {
      val codex = bundledAgentByKey("codex")
      val homePath = "C:\\Users\\Someone.Else"
      val expectedPath = "$homePath\\AppData\\Roaming\\npm\\Codex.exe"
      val eelApi = mockEelApi(
        osFamily = EelOsFamily.Windows,
        binaryName = "codex",
        pathResults = emptyList(),
        homePath = homePath,
        existingFiles = setOf(expectedPath),
      )

      val binaryPath = findTerminalAgentBinaryPath(codex, eelApi)

      assertThat(binaryPath).isEqualTo(expectedPath)
    }
  }

  @Test
  fun `windows matches extension case-insensitively in known location`() {
    runBlocking {
      val codex = bundledAgentByKey("codex")
      val homePath = "C:\\Users\\Someone.Else"
      val expectedPath = "$homePath\\AppData\\Roaming\\npm\\codex.EXE"
      val eelApi = mockEelApi(
        osFamily = EelOsFamily.Windows,
        binaryName = "codex",
        pathResults = emptyList(),
        homePath = homePath,
        existingFiles = setOf(expectedPath),
      )

      val binaryPath = findTerminalAgentBinaryPath(codex, eelApi)

      assertThat(binaryPath).isEqualTo(expectedPath)
    }
  }

  @Test
  fun `windows ignores unrelated files in known location`() {
    runBlocking {
      val codex = bundledAgentByKey("codex")
      val homePath = "C:\\Users\\Someone.Else"
      val expectedPath = "$homePath\\AppData\\Roaming\\npm\\codex.cmd"
      val eelApi = mockEelApi(
        osFamily = EelOsFamily.Windows,
        binaryName = "codex",
        pathResults = emptyList(),
        homePath = homePath,
        existingFiles = setOf(
          "$homePath\\AppData\\Roaming\\npm\\unrelated.exe",
          "$homePath\\AppData\\Roaming\\npm\\codexlike.exe",
          expectedPath,
        ),
      )

      val binaryPath = findTerminalAgentBinaryPath(codex, eelApi)

      assertThat(binaryPath).isEqualTo(expectedPath)
    }
  }

  @Test
  fun `windows ignores binary without extension in known location`() {
    runBlocking {
      val codex = bundledAgentByKey("codex")
      val homePath = "C:\\Users\\Someone.Else"
      val eelApi = mockEelApi(
        osFamily = EelOsFamily.Windows,
        binaryName = "codex",
        pathResults = emptyList(),
        homePath = homePath,
        existingFiles = setOf("$homePath\\AppData\\Roaming\\npm\\codex"),
      )

      val binaryPath = findTerminalAgentBinaryPath(codex, eelApi)

      assertThat(binaryPath).isNull()
    }
  }

  private fun bundledAgentByKey(agentKey: String) = bundledAgentByKey(TerminalAgent.AgentKey(agentKey))

  private fun bundledAgentByKey(agentKey: TerminalAgent.AgentKey) =
    DefaultTerminalAgentProvider().getTerminalAgents().first { it.agentKey == agentKey }

  private suspend fun mockEelApi(
    osFamily: EelOsFamily,
    binaryName: String,
    pathResults: List<String>,
    homePath: String = if (osFamily == EelOsFamily.Windows) "C:\\Users\\tester" else "/home/tester",
    existingFiles: Set<String> = emptySet(),
  ): EelApi {
    // The resolver calls `findExeFilesInPath(binaryName)` once per agent (no extension on Windows
    // either — extensions are filtered from the returned list afterwards), so we stub a single entry.
    return mockEelApi(osFamily, mapOf(binaryName to pathResults), homePath, existingFiles)
  }

  private suspend fun mockEelApi(
    osFamily: EelOsFamily,
    pathResultsByBinaryName: Map<String, List<String>>,
    homePath: String,
    existingFiles: Set<String>,
  ): EelApi {
    val descriptor = mock<EelDescriptor>()
    whenever(descriptor.osFamily).thenReturn(osFamily)

    val separator = if (osFamily == EelOsFamily.Windows) '\\' else '/'

    val parsedResultsByBinaryName = pathResultsByBinaryName.mapValues { (_, pathResults) ->
      pathResults.map { EelPath.parse(it, descriptor) }
    }
    val homeEelPath = EelPath.parse(homePath, descriptor)

    val regularFileType = mock<EelFileInfo.Type.Regular>()
    whenever(regularFileType.size).thenReturn(1L)
    val regularFileInfo = mock<EelFileInfo>()
    whenever(regularFileInfo.type).thenReturn(regularFileType)
    val regularFileResult = mock<EelResult.Ok<EelFileInfo>>()
    whenever(regularFileResult.value).thenReturn(regularFileInfo)
    val missingFileResult = mock<EelResult.Error<EelFileSystemApi.StatError>>()

    val exec = mock<EelExecApi>()
    whenever(exec.descriptor).thenReturn(descriptor)
    whenever(exec.findExeFilesInPath(any())).thenReturn(emptyList())
    for ((candidateName, parsedResults) in parsedResultsByBinaryName) {
      whenever(exec.findExeFilesInPath(candidateName)).thenReturn(parsedResults)
    }

    val userInfo = mock<EelUserInfo>()
    whenever(userInfo.home).thenReturn(homeEelPath)

    // Pre-compute `listDirectoryWithAttrs` results per parent directory so the stub does a
    // thread-safe map lookup instead of constructing new mocks on each parallel invocation.
    val listingByParent: Map<String, List<Pair<String, EelFileInfo>>> = existingFiles
      .groupBy { it.substringBeforeLast(separator) }
      .mapValues { (_, fullPaths) ->
        fullPaths.map { fullPath -> fullPath.substringAfterLast(separator) to regularFileInfo }
      }
    val listResultByParent: Map<String, EelResult.Ok<Collection<Pair<String, EelFileInfo>>>> =
      listingByParent.mapValues { (_, children) ->
        val ok = mock<EelResult.Ok<Collection<Pair<String, EelFileInfo>>>>()
        whenever(ok.value).thenReturn(children)
        ok
      }
    val emptyListResult = mock<EelResult.Ok<Collection<Pair<String, EelFileInfo>>>>()
    whenever(emptyListResult.value).thenReturn(emptyList())

    val fileSystem = mock<EelFileSystemApi>()
    whenever(fileSystem.user).thenReturn(userInfo)
    whenever(fileSystem.stat(any(), eq(EelFileSystemApi.SymlinkPolicy.RESOLVE_AND_FOLLOW))).thenAnswer { invocation ->
      val path = invocation.getArgument<EelPath>(0).toString()
      if (path in existingFiles) regularFileResult else missingFileResult
    }
    whenever(fileSystem.listDirectoryWithAttrs(any<EelFileSystemApi.ListDirectoryWithAttrsArgs>())).thenAnswer { invocation ->
      val args = invocation.getArgument<EelFileSystemApi.ListDirectoryWithAttrsArgs>(0)
      listResultByParent[args.path.toString()] ?: emptyListResult
    }

    val eelApi = mock<EelApi>()
    whenever(eelApi.descriptor).thenReturn(descriptor)
    whenever(eelApi.exec).thenReturn(exec)
    whenever(eelApi.fs).thenReturn(fileSystem)
    return eelApi
  }
}
