// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging

import com.jetbrains.python.inspections.requirement.PyRequirementVisitor.Companion.splitNameIntoComponents
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test

class PackageNameSplitterTest {

  @Test
  fun `should split name into components limited to 3 parts`() {
    val input = "part1-part2-part3-part4"
    val expected = arrayOf("part1", "part2", "part3-part4")
    assertArrayEquals(expected, splitNameIntoComponents(input))
  }

  @Test
  fun `should return the name as a single component if no delimiter present`() {
    val input = "nameWithoutDash"
    val expected = arrayOf("nameWithoutDash")
    assertArrayEquals(expected, splitNameIntoComponents(input))
  }

  @Test
  fun `should split correctly when less than 3 parts are present`() {
    val input = "part1-part2"
    val expected = arrayOf("part1", "part2")
    assertArrayEquals(expected, splitNameIntoComponents(input))
  }

  @Test
  fun `should handle empty input gracefully`() {
    val input = ""
    val expected = arrayOf("")
    assertArrayEquals(expected, splitNameIntoComponents(input))
  }
}