package com.intellij.mcpserver.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class FsUtilPathParsingTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun `parsePathForProjectLookup accepts plain absolute paths`() {
    val dir = Files.createDirectories(tempDir.resolve("plain absolute path"))

    assertThat(parsePathForProjectLookup(dir.toString())).isEqualTo(dir)
  }

  @Test
  fun `parsePathForProjectLookup accepts encoded file uris`() {
    val dir = Files.createDirectories(tempDir.resolve("encoded uri path"))
    val encodedUri = dir.toUri().toString()

    assertThat(parsePathForProjectLookup(encodedUri)).isEqualTo(dir)
  }

  @Test
  fun `parsePathForProjectLookup accepts file uris with unescaped spaces`() {
    val dir = Files.createDirectories(tempDir.resolve("uri with spaces"))
    val unescapedUri = dir.toUri().toString().replace("%20", " ")

    assertThat(parsePathForProjectLookup(unescapedUri)).isEqualTo(dir)
  }

  @Test
  fun `parsePathForProjectLookup returns null for blank paths`() {
    assertThat(parsePathForProjectLookup("   ")).isNull()
  }

  @Test
  fun `parsePathForProjectLookup returns null for invalid path characters`() {
    assertThat(parsePathForProjectLookup("\u0000")).isNull()
  }
}
