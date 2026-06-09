// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.fixtures

import junit.framework.TestCase.fail

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
}