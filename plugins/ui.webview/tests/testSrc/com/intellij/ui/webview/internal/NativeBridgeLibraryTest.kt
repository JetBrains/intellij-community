// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

internal class NativeBridgeLibraryTest {
  @Test
  fun availability_resolvesSourceTreeFile(@TempDir root: Path) {
    val libraryPath = root.resolve("lib/webview-native/linux/x86_64/test_bridge.so")

    val library = testLibrary(
      sourceFileLookup = { relativePath -> if (relativePath == "lib/webview-native/linux/x86_64/test_bridge.so") libraryPath else null },
    )

    assertEquals(NativeBridgeLibraryAvailability.Available(libraryPath.toAbsolutePath().normalize()), library.availability())
  }

  @Test
  fun availability_resolvesDevProjectRootSourceTreeFile(@TempDir root: Path) {
    val relativePath = "lib/webview-native/win/x86_64/test_bridge.dll"
    val libraryPath = root.resolve("community/platform/ui.webview").resolve(relativePath)
    Files.createDirectories(libraryPath.parent)
    Files.createFile(libraryPath)
    withSystemProperty("idea.dev.project.root", root.toString()) {
      val library = NativeBridgeLibrary(
        displayName = "Test bridge library",
        logEvent = "test-bridge-load",
        relativePaths = listOf(relativePath),
        rebuildHint = "Rebuild community/platform/ui.webview/native/TestBridge.",
        loadFailureHint = "Rebuild the test bridge.",
        pluginAnchorClass = NativeBridgeLibraryTest::class.java,
        pluginResourceLookup = { _, _ -> null },
      )

      assertEquals(NativeBridgeLibraryAvailability.Available(libraryPath.toAbsolutePath().normalize()), library.availability())
    }
  }

  @Test
  fun availability_resolvesPluginResourceFile(@TempDir root: Path) {
    val libraryPath = root.resolve("plugin/lib/webview-native/linux/x86_64/test_bridge.so")

    val library = testLibrary(
      pluginResourceLookup = { _, relativePath -> if (relativePath == "lib/webview-native/linux/x86_64/test_bridge.so") libraryPath else null },
    )

    assertEquals(NativeBridgeLibraryAvailability.Available(libraryPath.toAbsolutePath().normalize()), library.availability())
  }

  @Test
  fun availability_checksRelativePathAliasesInOrder(@TempDir root: Path) {
    val libraryPath = root.resolve("plugin/lib/webview-native/linux/x86_64/libtest_bridge.so")
    val checkedPaths = mutableListOf<String>()

    val library = testLibrary(
      pluginResourceLookup = { _, relativePath ->
        checkedPaths.add(relativePath)
        if (relativePath == "lib/webview-native/linux/x86_64/libtest_bridge.so") libraryPath else null
      },
    )

    assertEquals(NativeBridgeLibraryAvailability.Available(libraryPath.toAbsolutePath().normalize()), library.availability())
    assertEquals(
      listOf(
        "lib/webview-native/linux/x86_64/test_bridge.so",
        "lib/webview-native/linux/x86_64/libtest_bridge.so",
      ),
      checkedPaths,
    )
  }

  @Test
  fun availability_listsPluginResourceDetailsWhenLibraryIsMissing() {
    val library = testLibrary(
      missingFileDetails = { relativePath -> "'$relativePath' not found in plugin source or distribution" },
    )

    val availability = library.availability()

    assertTrue(availability is NativeBridgeLibraryAvailability.Missing, availability.toString())
    val problem = (availability as NativeBridgeLibraryAvailability.Missing).message
    assertTrue(
      problem.contains("Checked WebView plugin resources: lib/webview-native/linux/x86_64/test_bridge.so, lib/webview-native/linux/x86_64/libtest_bridge.so"),
      problem,
    )
    assertTrue(problem.contains("'lib/webview-native/linux/x86_64/test_bridge.so' not found in plugin source or distribution"), problem)
  }

  @Test
  fun verifyAbi_reportsRebuildHintWhenNativeSymbolIsMissing() {
    val library = testLibrary()

    val error = assertThrows(IllegalStateException::class.java) {
      library.verifyAbi(Path.of("/test/bin/test_bridge.so"), "expected-abi") { throw UnsatisfiedLinkError("missing") }
    }

    assertTrue(error.message!!.contains("Expected ABI 'expected-abi'"), error.message)
    assertTrue(error.message!!.contains("Rebuild community/platform/ui.webview/native/TestBridge."), error.message)
  }

  private fun testLibrary(
    sourceFileLookup: (String) -> Path? = { null },
    pluginResourceLookup: (Class<*>, String) -> Path? = { _, _ -> null },
    missingFileDetails: (String) -> String = { "" },
  ): NativeBridgeLibrary {
    return NativeBridgeLibrary(
      displayName = "Test bridge library",
      logEvent = "test-bridge-load",
      relativePaths = listOf(
        "lib/webview-native/linux/x86_64/test_bridge.so",
        "lib/webview-native/linux/x86_64/libtest_bridge.so",
      ),
      rebuildHint = "Rebuild community/platform/ui.webview/native/TestBridge.",
      loadFailureHint = "Rebuild the test bridge.",
      pluginAnchorClass = NativeBridgeLibraryTest::class.java,
      sourceFileLookup = sourceFileLookup,
      pluginResourceLookup = pluginResourceLookup,
      missingFileDetails = missingFileDetails,
    )
  }

  private fun withSystemProperty(key: String, value: String, action: () -> Unit) {
    val previousValue = System.getProperty(key)
    try {
      System.setProperty(key, value)
      action()
    }
    finally {
      if (previousValue == null) {
        System.clearProperty(key)
      }
      else {
        System.setProperty(key, previousValue)
      }
    }
  }
}
