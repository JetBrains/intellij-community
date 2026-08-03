package com.intellij.python.junit5Tests.unit

import com.intellij.util.SystemProperties
import com.jetbrains.python.sdk.PythonInterpreterPresentation
import com.jetbrains.python.sdk.impl.isNameDerivedFromHomePath
import com.jetbrains.python.sdk.impl.shortenPath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import javax.swing.Icon
import javax.swing.ImageIcon

class ShortenPathTest {

  // --- shortenPath: original path-shortening behavior, untouched ---

  @Test
  fun `unix path returns last segment when keepPrefix is false`() {
    assertEquals("myenv", shortenPath("/home/user/.venvs/myenv", 50, keepPrefix = false))
  }

  @Test
  fun `unix path keeps prefix and ellipsizes middle when keepPrefix is true`() {
    val result = shortenPath("/home/user/projects/very/deep/folder/myenv", 25, keepPrefix = true)
    assertEquals("/home/user/project…/myenv", result)
  }

  @Test
  fun `windows path returns last segment`() {
    assertEquals("myenv", shortenPath("""C:\Users\me\envs\myenv""", 50, keepPrefix = false))
  }

  // --- isNameDerivedFromHomePath: detect default vs label name ---

  @Test
  fun `system python name equals home path`() {
    val home = "/Users/foo/python/3.12/bin/python"
    assertTrue(isNameDerivedFromHomePath(home, home))
  }

  @Test
  fun `system python name with tilde expands and matches home path`() {
    val userHome = SystemProperties.getUserHome()
    val home = "$userHome/python/3.12/bin/python"
    val name = "~/python/3.12/bin/python"
    assertTrue(isNameDerivedFromHomePath(name, home))
  }

  @Test
  fun `venv name is parent directory of home path`() {
    val userHome = SystemProperties.getUserHome()
    val home = "$userHome/.venvs/myenv/bin/python"
    val name = "~/.venvs/myenv"
    assertTrue(isNameDerivedFromHomePath(name, home))
  }

  @Test
  fun `windows venv name is parent directory of home path`() {
    val name = """C:\Users\me\envs\myenv"""
    val home = """C:\Users\me\envs\myenv\Scripts\python.exe"""
    assertTrue(isNameDerivedFromHomePath(name, home))
  }

  @Test
  @DisplayName("name (\\) and home path (/) with different separators still match")
  fun `windows venv name with backslashes matches home path stored with forward slashes`() {
    // `suggestSdkName` yields the name via Path.toString() (backslashes on Windows), while homePath
    // may be stored with forward slashes (EEL/nio). The classification must survive that mismatch.
    val name = """C:\Users\me\envs\myenv"""
    val home = "C:/Users/me/envs/myenv/Scripts/python.exe"
    assertTrue(isNameDerivedFromHomePath(name, home))
  }

  @Test
  fun `venv name with forward slashes matches home path with backslashes`() {
    val name = "C:/Users/me/envs/myenv"
    val home = """C:\Users\me\envs\myenv\Scripts\python.exe"""
    assertTrue(isNameDerivedFromHomePath(name, home))
  }

  @Test
  fun `sibling directory sharing a name prefix is not classified as derived`() {
    // `myenv2` must not be treated as living under `myenv`.
    val name = """C:\Users\me\envs\myenv"""
    val home = """C:\Users\me\envs\myenv2\Scripts\python.exe"""
    assertFalse(isNameDerivedFromHomePath(name, home))
  }

  @Test
  @DisplayName("PY-89560: SSH label is not classified as derived from home path")
  fun `ssh label is not classified as derived from home path`() {
    val name = "SSH (sftp://user@host:22/usr/bin/python)"
    val home = "/usr/bin/python"
    assertFalse(isNameDerivedFromHomePath(name, home))
  }

  @Test
  fun `wsl label is not classified as derived from home path`() {
    val name = "WSL (Ubuntu): (/usr/bin/python)"
    val home = "/usr/bin/python"
    assertFalse(isNameDerivedFromHomePath(name, home))
  }

  @Test
  fun `remote fallback label is not classified as derived from home path`() {
    val name = "Remote (/usr/bin/python)"
    val home = "/usr/bin/python"
    assertFalse(isNameDerivedFromHomePath(name, home))
  }

  @Test
  fun `null home path means not derived`() {
    assertFalse(isNameDerivedFromHomePath("anything", null))
  }

  @Test
  fun `empty name means not derived`() {
    assertFalse(isNameDerivedFromHomePath("", "/usr/bin/python"))
  }

  // --- end-to-end: compactName via PythonInterpreterPresentation ---

  @Test
  fun `path-derived venv name renders basename via shortName`() {
    val pres = newPresentation(
      name = "~/.venvs/myenv",
      isPathDerivedName = true,
      suffix = "3.12.1",
    )
    assertEquals("myenv [3.12.1]", pres.shortName)
  }

  @Test
  @DisplayName("PY-89560: SSH label is rendered as-is in the status bar shortName")
  fun `ssh label name renders as-is via shortName`() {
    val sshName = "SSH (sftp://user@host:22/usr/bin/python)"
    val pres = newPresentation(
      name = sshName,
      isPathDerivedName = false,
      suffix = "3.12.1",
    )
    assertEquals("$sshName [3.12.1]", pres.shortName)
    assertFalse(pres.shortName.startsWith("python)"))
  }

  @Test
  fun `long ssh label name is trimmed in middle rather than reduced to a segment`() {
    val sshName = "SSH (sftp://averylongusername@some.really.long.hostname.example.com:22/opt/python/3.12/bin/python3.12)"
    val pres = newPresentation(
      name = sshName,
      isPathDerivedName = false,
      suffix = null,
    )
    assertTrue(pres.shortName.startsWith("SSH"), "must preserve the SSH prefix")
    assertTrue(pres.shortName.contains('…'), "must contain the middle-ellipsis marker")
    assertTrue(pres.shortName.length <= 50)
  }

  private val noIcon: Icon = ImageIcon()

  private fun newPresentation(
    name: String,
    isPathDerivedName: Boolean,
    suffix: String?,
  ) = PythonInterpreterPresentation(
    name = name,
    suffix = suffix,
    description = "irrelevant",
    modifier = null,
    icon = noIcon,
    isPathDerivedName = isPathDerivedName,
  )
}
