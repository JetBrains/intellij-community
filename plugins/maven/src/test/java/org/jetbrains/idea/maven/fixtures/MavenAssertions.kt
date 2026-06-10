// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.fixtures

import com.intellij.testFramework.UsefulTestCase.assertSameElements
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.fail
import junit.framework.TestCase.failNotEquals

internal object MavenAssertions {
  fun <T> assertContain(actual: Collection<T>, vararg expected: T) {
    val expectedList = expected.toList()
    if (actual.containsAll(expectedList)) return
    val absent: MutableSet<T> = HashSet(expectedList)
    absent.removeAll(actual.toSet())
    fail("""
  expected: $expectedList
  actual: $actual
  this elements not present: $absent
  """.trimIndent())
  }

  fun <T> assertUnorderedElementsAreEqual(actual: Collection<T>, vararg expected: T) {
    assertUnorderedElementsAreEqual(actual, expected.toList())
  }

  fun <T> assertUnorderedElementsAreEqual(actual: Collection<T>, expected: Collection<T>) {
    assertSameElements(actual, expected)
  }

  fun <T> assertDoNotContain(actual: List<T>, vararg expected: T) {
    val actualCopy: MutableList<T> = ArrayList(actual)
    actualCopy.removeAll(expected.toSet())
    assertEquals(actual.toString(), actualCopy.size, actual.size)
  }

  fun <T> assertOrderedElementsAreEqual(actual: Collection<T>, vararg expected: T) {
    assertOrderedElementsAreEqual(actual, expected.toList())
  }

  fun <T> assertOrderedElementsAreEqual(actual: Collection<T>, expected: List<T>) {
    val s = "\nexpected: $expected\nactual: $actual"
    assertEquals(s, expected.size, actual.size)

    val actualList: List<T> = ArrayList(actual)
    for (i in expected.indices) {
      val expectedElement = expected[i]
      val actualElement = actualList[i]
      if (actualElement != expectedElement) {
        failNotEquals(
          "collections have different elements or order",
          expected.joinToString("\n"),
          actual.joinToString("\n"),
        )
      }
      assertEquals(s, expectedElement, actualElement)
    }
  }
}