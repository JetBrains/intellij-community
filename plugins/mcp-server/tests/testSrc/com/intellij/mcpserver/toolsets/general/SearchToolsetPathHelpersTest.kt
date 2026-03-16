package com.intellij.mcpserver.toolsets.general

import com.intellij.mcpserver.McpExpectedError
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class SearchToolsetPathHelpersTest {

  @TempDir
  lateinit var tempDir: Path

  @Test
  fun `normalizeGlobPattern prefixes patterns without slash with globstar`() {
    val projectDir = tempDir.resolve("project")
    assertThat(normalizeGlobPattern("Foo.kt", projectDir)).isEqualTo("**/Foo.kt")
  }

  @Test
  fun `normalizeGlobPattern strips leading dot slash`() {
    val projectDir = tempDir.resolve("project")
    assertThat(normalizeGlobPattern("./foo/bar.txt", projectDir)).isEqualTo("foo/bar.txt")
  }

  @Test
  fun `normalizeGlobPattern expands trailing slash to double star`() {
    val projectDir = tempDir.resolve("project")
    assertThat(normalizeGlobPattern("foo/", projectDir)).isEqualTo("foo/**")
  }

  @Test
  fun `normalizeGlobPattern makes absolute patterns inside project relative`() {
    val projectDir = tempDir.resolve("project")
    val absolute = projectDir.resolve("sub").resolve("Foo.kt").toString()
    assertThat(normalizeGlobPattern(absolute, projectDir)).isEqualTo("sub/Foo.kt")
  }

  @Test
  fun `normalizeGlobPattern rejects patterns outside project directory`() {
    val projectDir = tempDir.resolve("project")
    assertThatThrownBy { normalizeGlobPattern("../outside/**", projectDir) }
      .isInstanceOf(McpExpectedError::class.java)
      .hasMessageContaining("outside of the project directory")
  }

  @Test
  fun `buildPathScope matches includes and excludes`() {
    val projectDir = tempDir.resolve("project")
    val scope = buildPathScope(projectDir, listOf("subdir1/**", "!**/*.java")) ?: error("Scope must be created")

    assertThat(scope.commonDirectory).isEqualTo(Path.of("subdir1"))
    assertThat(scope.fileFilter).contains("!*.java")

    assertThat(scope.matches(Path.of("subdir1", "foo.txt"))).isTrue()
    assertThat(scope.matches(Path.of("subdir1", "foo.java"))).isFalse()
    assertThat(scope.matches(Path.of("subdir2", "foo.txt"))).isFalse()
  }

  @Test
  fun `buildPathScope does not convert directory excludes into unsafe file masks`() {
    val projectDir = tempDir.resolve("project")
    val scope = buildPathScope(projectDir, listOf("subdir1/**", "!subdir1/excluded/**")) ?: error("Scope must be created")

    assertThat(scope.commonDirectory).isEqualTo(Path.of("subdir1"))
    assertThat(scope.fileFilter).isNull()

    assertThat(scope.matches(Path.of("subdir1", "foo.txt"))).isTrue()
    assertThat(scope.matches(Path.of("subdir1", "excluded", "foo.txt"))).isFalse()
  }

  @Test
  fun `buildPathScope expands explicit directory paths`() {
    val projectDir = tempDir.resolve("project")
    Files.createDirectories(projectDir.resolve("subdir1").resolve("nested"))
    val scope = buildPathScope(projectDir, listOf("subdir1/nested")) ?: error("Scope must be created")

    assertThat(scope.commonDirectory).isEqualTo(Path.of("subdir1", "nested"))
    assertThat(scope.matches(Path.of("subdir1", "nested", "foo.txt"))).isTrue()
    assertThat(scope.matches(Path.of("subdir1", "other", "foo.txt"))).isFalse()
  }

  @Test
  fun `buildPathScope returns null for blank patterns`() {
    val projectDir = tempDir.resolve("project")
    assertThat(buildPathScope(projectDir, listOf("", "  "))).isNull()
  }
}
