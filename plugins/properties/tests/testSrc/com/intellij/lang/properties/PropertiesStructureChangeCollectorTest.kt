// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties

import com.intellij.openapi.application.readAction
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.StructureChange
import com.intellij.openapi.vcs.changes.StructureChangeCollector
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
internal class PropertiesStructureChangeCollectorTest {
  companion object {
    private val projectFixture = projectFixture()
  }

  @Test
  fun `modified properties show changed keys and values`(): Unit = timeoutRunBlocking {
    val before = """
      changed=before
      removed=value
      unchanged=value
    """.trimIndent()
    val after = """
      changed=after
      added=value
      unchanged=value
    """.trimIndent()

    val changes = collect(before, after)
    assertEquals(mapOf("changed=after" to FileStatus.MODIFIED, "added=value" to FileStatus.ADDED, "removed=value" to FileStatus.DELETED),
                 changes.associate { it.text to it.status })
    assertEquals(after.indexOf("changed="), changes.single { it.text == "changed=after" }.offset)
    assertEquals(after.indexOf("added="), changes.single { it.text == "added=value" }.offset)
    assertNull(changes.single { it.text == "removed=value" }.offset)
    assertEquals(TextRange(0, "changed=after".length), changes.single { it.text == "changed=after" }.range)
    assertEquals(TextRange.from(after.indexOf("added="), "added=value".length), changes.single { it.text == "added=value" }.range)
    assertNull(changes.single { it.text == "removed=value" }.range)
  }

  @Test
  fun `added properties files show all keys and values`(): Unit = timeoutRunBlocking {
    val text = "first=value\nsecond=value"
    val changes = collect(null, text)
    assertEquals(setOf("first=value", "second=value"), changes.map { it.text }.toSet())
    assertTrue(changes.all { it.status == FileStatus.ADDED })
    assertEquals(listOf(0, text.indexOf("second=")), changes.map { it.offset })
  }

  @Test
  fun `unchanged properties have no structure changes`(): Unit = timeoutRunBlocking {
    val text = "first=value\nsecond=value"
    assertTrue(collect(text, text).isEmpty())
  }

  @Test
  fun `property separators and empty values show as assignments`(): Unit = timeoutRunBlocking {
    val text = "empty=\ncolon: value with spaces\nspace value"
    assertEquals(listOf("empty=", "colon=value with spaces", "space=value"), collect(null, text).map { it.text })
  }

  @Test
  fun `continued property values stay on one tree row`(): Unit = timeoutRunBlocking {
    val text = "continued=first\\\n  second"
    val change = collect(null, text).single()
    assertEquals("continued=first\\\\n  second", change.text)
    assertEquals(TextRange(0, text.length), change.range)
  }

  @Test
  fun `duplicate property keys keep their identity when values change`(): Unit = timeoutRunBlocking {
    val before = "duplicate=first\nduplicate=before"
    val after = "duplicate=first\nduplicate=after"
    val change = collect(before, after).single()
    assertEquals("duplicate=after", change.text)
    assertEquals(FileStatus.MODIFIED, change.status)
    assertEquals(after.lastIndexOf("duplicate="), change.offset)
  }

  private suspend fun collect(before: String?, after: String): List<StructureChange> = readAction {
    val factory = PsiFileFactory.getInstance(projectFixture.get())
    val afterFile = factory.createFileFromText("sample.properties", PropertiesFileType.INSTANCE, after)
    val beforeFile = before?.let { factory.createFileFromText("sample.properties", PropertiesFileType.INSTANCE, it) }
    StructureChangeCollector.collect(beforeFile, afterFile)
  }
}
