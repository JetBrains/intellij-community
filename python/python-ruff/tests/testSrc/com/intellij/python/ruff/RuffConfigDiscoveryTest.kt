// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ruff

import com.intellij.idea.TestFor
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

@TestFor(issues = ["PY-85409"])
@TestApplication
internal class RuffConfigDiscoveryTest {
  private val tempDir by tempPathFixture()

  @Test
  fun `dot ruff toml comes before ruff toml and pyproject toml`() {
    write("dir/pyproject.toml", "[tool.ruff]\nline-length = 1\n")
    write("dir/ruff.toml", "line-length = 2\n")
    write("dir/.ruff.toml", "line-length = 3\n")

    assertEquals("dir/.ruff.toml", relative(ruffConfigIn(dir("dir"))?.file))
  }

  @Test
  fun `pyproject toml counts only with a tool ruff table`() {
    write("plain/pyproject.toml", "[project]\nname = \"plain\"\n")
    write("dotted/pyproject.toml", "[tool.ruff.lint]\nselect = [\"E\"]\n")
    write("broken/pyproject.toml", "[tool.ruff\n")

    assertNull(ruffConfigIn(dir("plain")))
    assertEquals("dotted/pyproject.toml", relative(ruffConfigIn(dir("dotted"))?.file))
    assertNull(ruffConfigIn(dir("broken")))
  }

  @Test
  fun `the nearest ancestor config wins`() {
    write("ruff.toml", "line-length = 1\n")
    write("a/ruff.toml", "line-length = 2\n")
    tempDir.resolve("a/b/c").createDirectories()

    assertEquals("a/ruff.toml", relative(findRuffConfig(dir("a/b/c"))?.file))
  }

  @Test
  fun `a folder without a config gets the project config`() {
    write("project/pyproject.toml", "[tool.ruff.format]\nquote-style = \"single\"\n")
    tempDir.resolve("second").createDirectories()

    assertEquals("project/pyproject.toml", relative(ruffFallbackConfig(dir("second"), dir("project"))?.file))
  }

  @Test
  fun `a folder with its own config gets no project config`() {
    write("project/ruff.toml", "line-length = 1\n")
    write("second/pyproject.toml", "[tool.ruff]\nline-length = 2\n")
    write("project/inner/x.py", "")

    assertNull(ruffFallbackConfig(dir("second"), dir("project")))
    assertNull(ruffFallbackConfig(dir("project/inner"), dir("project")))
  }

  @Test
  fun `no folder gets a project config when the project has none`() {
    write("project/pyproject.toml", "[project]\nname = \"p\"\n")
    tempDir.resolve("second").createDirectories()

    assertNull(ruffFallbackConfig(dir("second"), dir("project")))
    assertNull(ruffFolderFallback(listOf(dir("second")), dir("project")))
  }

  @Test
  fun `only the folders without a config get the project config`() {
    write("project/ruff.toml", "line-length = 1\n")
    write("own/.ruff.toml", "line-length = 2\n")
    tempDir.resolve("bare").createDirectories()

    val fallback = ruffFolderFallback(listOf(dir("project"), dir("own"), dir("bare")), dir("project"))

    assertEquals(listOf("bare"), fallback?.folders?.map(::relative))
    assertEquals("project/ruff.toml", relative(fallback?.config?.file))
  }

  @Test
  fun `a changed ruff table changes the fallback`() {
    write("project/pyproject.toml", "[tool.ruff]\nline-length = 1\n")
    tempDir.resolve("bare").createDirectories()
    val before = ruffFolderFallback(listOf(dir("bare")), dir("project"))

    write("project/pyproject.toml", "[project]\nname = \"p\"\n[tool.ruff]\nline-length = 1\n")
    assertEquals(before, ruffFolderFallback(listOf(dir("bare")), dir("project")))

    write("project/pyproject.toml", "[tool.ruff]\nline-length = 2\n")
    assertNotEquals(before, ruffFolderFallback(listOf(dir("bare")), dir("project")))
  }

  @Test
  fun `initialization options name each folder and prefer the folder config`() {
    write("project/ruff.toml", "line-length = 1\n")
    tempDir.resolve("one").createDirectories()
    tempDir.resolve("two").createDirectories()
    val fallback = ruffFolderFallback(listOf(dir("one"), dir("two")), dir("project"))!!

    val options = ruffInitializationOptions(fallback, "/p/ruff.toml") { "uri:${it.name}" }

    assertEquals(
      mapOf(
        "globalSettings" to emptyMap<String, Any>(),
        "settings" to listOf(
          mapOf("workspace" to "uri:one", "configuration" to "/p/ruff.toml", "configurationPreference" to "filesystemFirst"),
          mapOf("workspace" to "uri:two", "configuration" to "/p/ruff.toml", "configurationPreference" to "filesystemFirst"),
        ),
      ),
      options,
    )
  }

  @Test
  fun `a config file event and a directory event count, other events do not`() {
    write("project/ruff.toml", "line-length = 1\n")
    write("project/main.py", "")
    val config = dir("project/ruff.toml")
    val source = dir("project/main.py")
    val project = dir("project")

    assertTrue(isRuffConfigEvent(VFileContentChangeEvent(null, config, 0, 1)))
    assertTrue(isRuffConfigEvent(VFileDeleteEvent(null, config)))
    assertTrue(isRuffConfigEvent(VFileDeleteEvent(null, project)))
    assertTrue(isRuffConfigEvent(VFileMoveEvent(null, project, dir(""))))
    assertTrue(isRuffConfigEvent(VFilePropertyChangeEvent(null, source, VirtualFile.PROP_NAME, "main.py", "ruff.toml")))
    assertTrue(isRuffConfigEvent(VFilePropertyChangeEvent(null, config, VirtualFile.PROP_NAME, "ruff.toml", "old.toml")))
    assertTrue(isRuffConfigEvent(VFilePropertyChangeEvent(null, project, VirtualFile.PROP_NAME, "project", "renamed")))

    assertFalse(isRuffConfigEvent(VFileContentChangeEvent(null, source, 0, 1)))
    assertFalse(isRuffConfigEvent(VFileDeleteEvent(null, source)))
    assertFalse(isRuffConfigEvent(VFilePropertyChangeEvent(null, source, VirtualFile.PROP_NAME, "main.py", "app.py")))
    assertFalse(isRuffConfigEvent(VFilePropertyChangeEvent(null, config, VirtualFile.PROP_WRITABLE, true, false)))
  }

  @Test
  fun `the command line gets the project config and its directory`() {
    write("project/ruff.toml", "line-length = 1\n")
    val fallback = ruffFallbackConfig(dir(""), dir("project"))

    val command = ruffStdinCommand("/x/a.py", fallback, "format")

    val configPath = dir("project/ruff.toml").ruffPath()!!
    assertEquals(listOf("format", "--config", configPath, "--force-exclude", "--stdin-filename", "/x/a.py", "-"), command.arguments)
    assertEquals(tempDir.resolve("project").toRealPath(), command.workingDir?.toRealPath())

    val plain = ruffStdinCommand("/x/a.py", null, "check", "--fix-only")
    assertEquals(listOf("check", "--fix-only", "--force-exclude", "--stdin-filename", "/x/a.py", "-"), plain.arguments)
    assertNull(plain.workingDir)
  }

  private fun write(relativePath: String, text: String) {
    val path = tempDir.resolve(relativePath)
    path.parent.createDirectories()
    path.writeText(text)
    LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)!!.refresh(false, false)
  }

  private fun dir(relativePath: String): VirtualFile {
    val path: Path = tempDir.resolve(relativePath)
    return LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)!!
  }

  private fun relative(file: VirtualFile?): String? {
    file ?: return null
    val root = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(tempDir)!!
    return file.path.removePrefix(root.path + "/")
  }
}
