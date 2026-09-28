// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.testFramework.fixtures

import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.testFramework.core.FileComparisonFailedError
import com.intellij.testFramework.UsefulTestCase
import com.intellij.util.containers.CollectionFactory
import junit.framework.TestCase
import junit.framework.TestCase.assertEquals

fun assertEqualPaths(actual: String, expected: String) {
  assertEquals(FileUtil.toSystemIndependentName(expected), FileUtil.toSystemIndependentName(actual))
}

fun assertUnorderedPathsAreEqual(actual: Collection<String>, expected: Collection<String>) {
  assertEquals(createFilePathSet(expected), createFilePathSet(actual))
}

private fun createFilePathSet(paths: Collection<String>) =
  CollectionFactory.createFilePathSet(paths.map { FileUtil.toSystemIndependentName(it) })

fun <T> assertContain(actual: Collection<T>, vararg expected: T) {
  assertContain(actual, expected.toList())
}

fun <T> assertContain(actual: Collection<T>, expected: Collection<T>) {
  if (actual.containsAll(expected)) return
  val absent: MutableSet<T> = HashSet(expected)
  absent.removeAll(actual.toSet())
  TestCase.fail(
    """
expected: $expected
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

fun <T> assertUnorderedElementsAreEqual(message: String, actual: Collection<T>, expected: Collection<T>) {
  UsefulTestCase.assertSameElements(message, actual, expected)
}

fun <T> assertDoNotContain(actual: Collection<T>, vararg expected: T) {
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

/** Asserts that [actual] has the elements of [expected] in the same order. */
fun <T> assertOrderedEquals(actual: Iterable<T>, vararg expected: T) {
  UsefulTestCase.assertOrderedEquals(actual, *expected)
}

/** Asserts that [actual] has the elements of [expected] in the same order. */
fun <T> assertOrderedEquals(actual: Array<T>, vararg expected: T) {
  UsefulTestCase.assertOrderedEquals(actual, *expected)
}

/** Asserts that [actual] has the elements of [expected] in the same order. */
fun <T> assertOrderedEquals(actual: Iterable<T>, expected: Iterable<T>) {
  UsefulTestCase.assertOrderedEquals(actual, expected)
}

/** Asserts that [collection] contains the [expected] elements in the given order. Other elements can sit between them. */
fun <T> assertContainsOrdered(collection: Collection<T>, vararg expected: T) {
  UsefulTestCase.assertContainsOrdered(collection, *expected)
}

/** Asserts that [collection] has exactly [expectedSize] elements. */
fun assertSize(expectedSize: Int, collection: Collection<*>) {
  UsefulTestCase.assertSize(expectedSize, collection)
}

/** Asserts that [array] has exactly [expectedSize] elements. */
fun assertSize(expectedSize: Int, array: Array<*>) {
  UsefulTestCase.assertSize(expectedSize, array)
}

/** Asserts that [collection] has no elements. The failure message lists the elements. */
fun assertEmpty(collection: Collection<*>) {
  UsefulTestCase.assertEmpty(collection)
}

/** Asserts that [collection] has no elements. The failure message starts with [message]. */
fun assertEmpty(message: String, collection: Collection<*>) {
  UsefulTestCase.assertEmpty(message, collection)
}

/** Asserts that [s] is null or empty. */
fun assertEmpty(s: String?) {
  UsefulTestCase.assertEmpty(s)
}

/** Asserts that [collection] is not null and has at least one element. */
fun assertNotEmpty(collection: Collection<*>?) {
  UsefulTestCase.assertNotEmpty(collection)
}

/** Asserts that [expected] and [actual] are equal after a trim and a line separator normalization. */
fun assertSameLines(expected: String, actual: String) {
  UsefulTestCase.assertSameLines(expected, actual)
}

/**
 * Asserts that the file at [filePath] contains the same lines as [expectedText], ignoring line order.
 * Ported from `MavenTestCase.assertUnorderedLinesWithFile` for fixture-based JUnit 5 tests.
 */
fun assertUnorderedLinesWithFile(filePath: String, expectedText: String) {
  try {
    UsefulTestCase.assertSameLinesWithFile(filePath, expectedText)
  }
  catch (e: FileComparisonFailedError) {
    val expected = e.expectedStringPresentation
    val actual = e.actualStringPresentation
    assertUnorderedElementsAreEqual(
      expected.split("\n").dropLastWhile { it.isEmpty() },
      *actual.split("\n").dropLastWhile { it.isEmpty() }.toTypedArray()
    )
  }
}
