// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.testFramework.fixtures

import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.util.containers.CollectionFactory
import junit.framework.TestCase
import junit.framework.TestCase.assertEquals

fun assertUnorderedPathsAreEqual(actual: Collection<String>, expected: Collection<String>) {
  assertEquals(createFilePathSet(expected), createFilePathSet(actual))
}

private fun createFilePathSet(paths: Collection<String>) =
  CollectionFactory.createFilePathSet(paths.map { FileUtil.toSystemIndependentName(it) })

fun <T> assertContain(actual: Collection<T>, vararg expected: T) {
  val expectedList = expected.toList()
  if (actual.containsAll(expectedList)) return
  val absent: MutableSet<T> = HashSet(expectedList)
  absent.removeAll(actual.toSet())
  TestCase.fail(
    """
expected: $expectedList
actual: $actual
this elements not present: $absent
""".trimIndent()
  )
}

fun <T> assertUnorderedElementsAreEqual(actual: Collection<T>, vararg expected: T) {
  assertUnorderedElementsAreEqual(actual, expected.toList())
}

fun <T> assertUnorderedElementsAreEqual(actual: Collection<T>, expected: Collection<T>) {
  UsefulTestCase.assertSameElements(actual, expected)
}

fun <T> assertDoNotContain(actual: List<T>, vararg expected: T) {
  val actualCopy: MutableList<T> = ArrayList(actual)
  actualCopy.removeAll(expected.toSet())
  TestCase.assertEquals(actual.toString(), actualCopy.size, actual.size)
}

fun <T> assertOrderedElementsAreEqual(actual: Collection<T>, vararg expected: T) {
  assertOrderedElementsAreEqual(actual, expected.toList())
}

fun <T> assertOrderedElementsAreEqual(actual: Collection<T>, expected: List<T>) {
  val s = "\nexpected: $expected\nactual: $actual"
  TestCase.assertEquals(s, expected.size, actual.size)

  val actualList: List<T> = ArrayList(actual)
  for (i in expected.indices) {
    val expectedElement = expected[i]
    val actualElement = actualList[i]
    if (actualElement != expectedElement) {
      TestCase.failNotEquals(
        "collections have different elements or order",
        expected.joinToString("\n"),
        actual.joinToString("\n"),
      )
    }
    assertEquals(s, expectedElement, actualElement)
  }
}