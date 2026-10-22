// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties

import com.intellij.openapi.application.readAction
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.HunkLines
import com.intellij.openapi.vcs.changes.HunkName
import com.intellij.openapi.vcs.changes.StructuralHunkNamer
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@TestApplication
internal class PropertiesStructuralHunkNamerTest {
  companion object {
    private val projectFixture = projectFixture()
  }

  @Test
  fun `a hunk names its changed, added, and removed properties as assignments`(): Unit = timeoutRunBlocking {
    val name = nameHunks("changed=before\nremoved=gone\nunchanged=value", "changed=after\nadded=fresh\nunchanged=value",
                         HunkLines(0, 2, 0, 2)).single()

    assertEquals(listOf("changed=after" to FileStatus.MODIFIED, "added=fresh" to FileStatus.ADDED, "removed=gone" to FileStatus.DELETED),
                 name.parts.map { it.text to it.status })
  }

  @Test
  fun `a renamed key with the same value is a rename`(): Unit = timeoutRunBlocking {
    val part = nameHunks("old.key=value", "new.key=value", HunkLines(0, 1, 0, 1)).single().parts.single()

    assertEquals(Triple("new.key=value", FileStatus.MODIFIED, "old.key=value"), Triple(part.text, part.status, part.previousText))
  }

  @Test
  fun `separators and empty values show as assignments`(): Unit = timeoutRunBlocking {
    val name = nameHunks(null, "empty=\ncolon: value with spaces\nspace value", HunkLines(0, 0, 0, 3)).single()

    assertEquals(listOf("empty=", "colon=value with spaces", "space=value"), name.parts.map { it.text })
  }

  @Test
  fun `a continued value stays one part`(): Unit = timeoutRunBlocking {
    val name = nameHunks("continued=first", "continued=first\\\n  second", HunkLines(0, 1, 0, 2)).single()

    assertEquals(listOf("continued=first\\\\n  second" to FileStatus.MODIFIED), name.parts.map { it.text to it.status })
  }

  @Test
  fun `a duplicate key keeps its identity when its value changes`(): Unit = timeoutRunBlocking {
    val name = nameHunks("duplicate=first\nduplicate=before", "duplicate=first\nduplicate=after", HunkLines(1, 2, 1, 2)).single()

    assertEquals(listOf(Triple("duplicate=after", FileStatus.MODIFIED, null)), name.parts.map { Triple(it.text, it.status, it.previousText) })
  }

  private suspend fun nameHunks(before: String?, after: String, vararg hunks: HunkLines): List<HunkName> = readAction {
    val factory = PsiFileFactory.getInstance(projectFixture.get())
    val afterFile = factory.createFileFromText("sample.properties", PropertiesFileType.INSTANCE, after)
    val beforeFile = before?.let { factory.createFileFromText("sample.properties", PropertiesFileType.INSTANCE, it) }
    StructuralHunkNamer.name(beforeFile, afterFile, hunks.asList())
  }
}
