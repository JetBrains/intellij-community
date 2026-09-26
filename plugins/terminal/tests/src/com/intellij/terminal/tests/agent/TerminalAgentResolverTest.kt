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
  fun `binary found via PATH on posix`() {
    runBlocking {
      val agent = TestTerminalAgent(binaryName = "agent")
      val eelApi = mockEelApi(EelOsFamily.Posix, "agent", listOf("/opt/bin/agent"))

      val binaryPath = findTerminalAgentBinaryPath(agent, eelApi)

      assertThat(binaryPath).isEqualTo("/opt/bin/agent")
    }
  }

  @Test
  fun `first candidate is returned when multiple found`() {
    runBlocking {
      val agent = TestTerminalAgent(binaryName = "agent")
      val eelApi = mockEelApi(EelOsFamily.Posix, "agent", listOf("/usr/local/bin/agent", "/opt/bin/agent"))

      val binaryPath = findTerminalAgentBinaryPath(agent, eelApi)

      assertThat(binaryPath).isEqualTo("/usr/local/bin/agent")
    }
  }

  @Test
  fun `agent is unavailable when not found in PATH (Unix)`() {
    runBlocking {
      val agent = TestTerminalAgent(binaryName = "agent")
      val eelApi = mockEelApi(EelOsFamily.Posix, "agent", emptyList())

      val binaryPath = findTerminalAgentBinaryPath(agent, eelApi)

      assertThat(binaryPath).isNull()
      assertThat(agent.getInstallCommand(EelOsFamily.Posix)).isNull()
    }
  }

  @Test
  fun `agent is unavailable when not found in PATH (Windows)`() {
    runBlocking {
      val agent = TestTerminalAgent(binaryName = "agent")
      val eelApi = mockEelApi(EelOsFamily.Windows, "agent", emptyList())

      val binaryPath = findTerminalAgentBinaryPath(agent, eelApi)

      assertThat(binaryPath).isNull()
    }
  }

  @Test
  fun `windows prefers exe over cmd and ps1`() {
    runBlocking {
      val agent = TestTerminalAgent(binaryName = "agent")
      val eelApi = mockEelApi(EelOsFamily.Windows, "agent", listOf(
        "C:\\bin\\agent.ps1",
        "C:\\bin\\agent.cmd",
        "C:\\bin\\agent.exe",
      ))

      val binaryPath = findTerminalAgentBinaryPath(agent, eelApi)

      assertThat(binaryPath).endsWith("agent.exe")
    }
  }

  @Test
  fun `windows picks recognized extension over no extension`() {
    runBlocking {
      val agent = TestTerminalAgent(binaryName = "agent")
      val eelApi = mockEelApi(EelOsFamily.Windows, "agent", listOf(
        "C:\\bin\\agent",
        "C:\\bin\\agent.cmd",
      ))

      val binaryPath = findTerminalAgentBinaryPath(agent, eelApi)

      assertThat(binaryPath).endsWith("agent.cmd")
    }
  }

  @Test
  fun `windows finds binary in home-relative known location`() {
    runBlocking {
      val agent = TestTerminalAgent(
        binaryName = "agent",
        windowsKnownLocationCandidates = listOf($$"$HOME\\AppData\\Roaming\\npm"),
      )
      val homePath = "C:\\Users\\Someone.Else"
      val expectedPath = "$homePath\\AppData\\Roaming\\npm\\agent.exe"
      val eelApi = mockEelApi(
        osFamily = EelOsFamily.Windows,
        binaryName = "agent",
        pathResults = emptyList(),
        homePath = homePath,
        existingFiles = setOf(expectedPath),
      )

      val binaryPath = findTerminalAgentBinaryPath(agent, eelApi)

      assertThat(binaryPath).isEqualTo(expectedPath)
    }
  }

  @Test
  fun `windows ignores binary found only outside the known-location candidates`() {
    runBlocking {
      val agent = TestTerminalAgent(
        binaryName = "agent",
        windowsKnownLocationCandidates = listOf($$"$HOME\\AppData\\Roaming\\npm"),
      )
      val homePath = "C:\\Users\\Someone.Else"
      val localBinPath = "$homePath\\.local\\bin\\agent.cmd"
      val eelApi = mockEelApi(
        osFamily = EelOsFamily.Windows,
        binaryName = "agent",
        pathResults = emptyList(),
        homePath = homePath,
        existingFiles = setOf(localBinPath),
      )

      val binaryPath = findTerminalAgentBinaryPath(agent, eelApi)

      assertThat(binaryPath).isNull()
    }
  }

  @Test
  fun `windows checks known locations in order`() {
    runBlocking {
      val agent = TestTerminalAgent(
        binaryName = "agent",
        windowsKnownLocationCandidates = listOf(
          $$"$HOME\\AppData\\Roaming\\npm",
          $$"$HOME\\.local\\bin",
        ),
      )
      val homePath = "C:\\Users\\Someone.Else"
      val firstKnownLocation = "$homePath\\AppData\\Roaming\\npm\\agent.cmd"
      val secondKnownLocation = "$homePath\\.local\\bin\\agent.exe"
      val eelApi = mockEelApi(
        osFamily = EelOsFamily.Windows,
        binaryName = "agent",
        pathResults = emptyList(),
        homePath = homePath,
        existingFiles = setOf(firstKnownLocation, secondKnownLocation),
      )

      val binaryPath = findTerminalAgentBinaryPath(agent, eelApi)

      assertThat(binaryPath).isEqualTo(firstKnownLocation)
    }
  }

  @Test
  fun `windows prefers binary found in PATH over known locations`() {
    runBlocking {
      val agent = TestTerminalAgent(
        binaryName = "agent",
        windowsKnownLocationCandidates = listOf($$"$HOME\\AppData\\Roaming\\npm"),
      )
      val homePath = "C:\\Users\\Someone.Else"
      val eelApi = mockEelApi(
        osFamily = EelOsFamily.Windows,
        binaryName = "agent",
        pathResults = listOf("C:\\bin\\agent.ps1"),
        homePath = homePath,
        existingFiles = setOf("$homePath\\AppData\\Roaming\\npm\\agent.exe"),
      )

      val binaryPath = findTerminalAgentBinaryPath(agent, eelApi)

      assertThat(binaryPath).isEqualTo("C:\\bin\\agent.ps1")
    }
  }

  @Test
  fun `posix finds binary in home-relative known location`() {
    runBlocking {
      val agent = TestTerminalAgent(
        binaryName = "agent",
        posixKnownLocationCandidates = listOf($$"$HOME/.local/bin"),
      )
      val homePath = "/home/Someone.Else"
      val expectedPath = "$homePath/.local/bin/agent"
      val eelApi = mockEelApi(
        osFamily = EelOsFamily.Posix,
        binaryName = "agent",
        pathResults = emptyList(),
        homePath = homePath,
        existingFiles = setOf(expectedPath),
      )

      val binaryPath = findTerminalAgentBinaryPath(agent, eelApi)

      assertThat(binaryPath).isEqualTo(expectedPath)
    }
  }

  @Test
  fun `posix finds binary in absolute known location`() {
    runBlocking {
      val agent = TestTerminalAgent(
        binaryName = "agent",
        posixKnownLocationCandidates = listOf("/usr/local/bin"),
      )
      val eelApi = mockEelApi(
        osFamily = EelOsFamily.Posix,
        binaryName = "agent",
        pathResults = emptyList(),
        existingFiles = setOf("/usr/local/bin/agent"),
      )

      val binaryPath = findTerminalAgentBinaryPath(agent, eelApi)

      assertThat(binaryPath).isEqualTo("/usr/local/bin/agent")
    }
  }

  @Test
  fun `posix prefers first known location when multiple match`() {
    runBlocking {
      val agent = TestTerminalAgent(
        binaryName = "agent",
        posixKnownLocationCandidates = listOf($$"$HOME/.local/bin", "/usr/local/bin"),
      )
      val homePath = "/home/Someone.Else"
      val firstKnownLocation = "$homePath/.local/bin/agent"
      val secondKnownLocation = "/usr/local/bin/agent"
      val eelApi = mockEelApi(
        osFamily = EelOsFamily.Posix,
        binaryName = "agent",
        pathResults = emptyList(),
        homePath = homePath,
        existingFiles = setOf(firstKnownLocation, secondKnownLocation),
      )

      val binaryPath = findTerminalAgentBinaryPath(agent, eelApi)

      assertThat(binaryPath).isEqualTo(firstKnownLocation)
    }
  }

  @Test
  fun `posix ignores binary found only outside the known-location candidates`() {
    runBlocking {
      val agent = TestTerminalAgent(
        binaryName = "agent",
        posixKnownLocationCandidates = listOf($$"$HOME/.local/bin"),
      )
      val eelApi = mockEelApi(
        osFamily = EelOsFamily.Posix,
        binaryName = "agent",
        pathResults = emptyList(),
        existingFiles = setOf("/usr/local/bin/agent"),
      )

      val binaryPath = findTerminalAgentBinaryPath(agent, eelApi)

      assertThat(binaryPath).isNull()
    }
  }

  @Test
  fun `windows finds binary using a custom executable extension list`() {
    runBlocking {
      val agent = TestTerminalAgent(
        binaryName = "agent",
        windowsKnownLocationCandidates = listOf($$"$HOME\\.local\\bin"),
        windowsExecutableExtensions = listOf("bat"),
      )
      val homePath = "C:\\Users\\Someone.Else"
      val expectedPath = "$homePath\\.local\\bin\\agent.bat"
      val eelApi = mockEelApi(
        osFamily = EelOsFamily.Windows,
        binaryName = "agent",
        pathResults = emptyList(),
        homePath = homePath,
        existingFiles = setOf(expectedPath),
      )

      val binaryPath = findTerminalAgentBinaryPath(agent, eelApi)

      assertThat(binaryPath).isEqualTo(expectedPath)
    }
  }

  @Test
  fun `posix prefers binary found in PATH over known locations`() {
    runBlocking {
      val agent = TestTerminalAgent(
        binaryName = "agent",
        posixKnownLocationCandidates = listOf($$"$HOME/.local/bin", "/usr/local/bin"),
      )
      val homePath = "/home/Someone.Else"
      val eelApi = mockEelApi(
        osFamily = EelOsFamily.Posix,
        binaryName = "agent",
        pathResults = listOf("/opt/agent/agent"),
        homePath = homePath,
        existingFiles = setOf("$homePath/.local/bin/agent", "/usr/local/bin/agent"),
      )

      val binaryPath = findTerminalAgentBinaryPath(agent, eelApi)

      assertThat(binaryPath).isEqualTo("/opt/agent/agent")
    }
  }

  @Test
  fun `windows matches binary name case-insensitively in known location`() {
    runBlocking {
      val agent = TestTerminalAgent(
        binaryName = "agent",
        windowsKnownLocationCandidates = listOf($$"$HOME\\AppData\\Roaming\\npm"),
      )
      val homePath = "C:\\Users\\Someone.Else"
      val expectedPath = "$homePath\\AppData\\Roaming\\npm\\Agent.exe"
      val eelApi = mockEelApi(
        osFamily = EelOsFamily.Windows,
        binaryName = "agent",
        pathResults = emptyList(),
        homePath = homePath,
        existingFiles = setOf(expectedPath),
      )

      val binaryPath = findTerminalAgentBinaryPath(agent, eelApi)

      assertThat(binaryPath).isEqualTo(expectedPath)
    }
  }

  @Test
  fun `windows matches extension case-insensitively in known location`() {
    runBlocking {
      val agent = TestTerminalAgent(
        binaryName = "agent",
        windowsKnownLocationCandidates = listOf($$"$HOME\\AppData\\Roaming\\npm"),
      )
      val homePath = "C:\\Users\\Someone.Else"
      val expectedPath = "$homePath\\AppData\\Roaming\\npm\\agent.EXE"
      val eelApi = mockEelApi(
        osFamily = EelOsFamily.Windows,
        binaryName = "agent",
        pathResults = emptyList(),
        homePath = homePath,
        existingFiles = setOf(expectedPath),
      )

      val binaryPath = findTerminalAgentBinaryPath(agent, eelApi)

      assertThat(binaryPath).isEqualTo(expectedPath)
    }
  }

  @Test
  fun `windows ignores unrelated files in known location`() {
    runBlocking {
      val agent = TestTerminalAgent(
        binaryName = "agent",
        windowsKnownLocationCandidates = listOf($$"$HOME\\AppData\\Roaming\\npm"),
      )
      val homePath = "C:\\Users\\Someone.Else"
      val expectedPath = "$homePath\\AppData\\Roaming\\npm\\agent.cmd"
      val eelApi = mockEelApi(
        osFamily = EelOsFamily.Windows,
        binaryName = "agent",
        pathResults = emptyList(),
        homePath = homePath,
        existingFiles = setOf(
          "$homePath\\AppData\\Roaming\\npm\\unrelated.exe",
          "$homePath\\AppData\\Roaming\\npm\\agentlike.exe",
          expectedPath,
        ),
      )

      val binaryPath = findTerminalAgentBinaryPath(agent, eelApi)

      assertThat(binaryPath).isEqualTo(expectedPath)
    }
  }

  @Test
  fun `windows ignores binary without extension in known location`() {
    runBlocking {
      val agent = TestTerminalAgent(
        binaryName = "agent",
        windowsKnownLocationCandidates = listOf($$"$HOME\\AppData\\Roaming\\npm"),
      )
      val homePath = "C:\\Users\\Someone.Else"
      val eelApi = mockEelApi(
        osFamily = EelOsFamily.Windows,
        binaryName = "agent",
        pathResults = emptyList(),
        homePath = homePath,
        existingFiles = setOf("$homePath\\AppData\\Roaming\\npm\\agent"),
      )

      val binaryPath = findTerminalAgentBinaryPath(agent, eelApi)

      assertThat(binaryPath).isNull()
    }
  }

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
