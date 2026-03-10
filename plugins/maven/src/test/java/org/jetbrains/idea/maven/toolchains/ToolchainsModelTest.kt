// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.toolchains

import com.intellij.testFramework.UsefulTestCase

class ToolchainsModelTest : UsefulTestCase() {

  fun testMatchingExactVersion() {
    val requirement = toolchainRequirement("11")
    val model = ToolchainModel("jdk", mapOf("version" to "11"), emptyMap())
    assertTrue(model.matches(requirement))
  }

  fun testMatchingRange() {
    val requirement = toolchainRequirement("[11,13]")
    val model11 = ToolchainModel("jdk", mapOf("version" to "11"), emptyMap())
    val model12 = ToolchainModel("jdk", mapOf("version" to "12"), emptyMap())
    val model13 = ToolchainModel("jdk", mapOf("version" to "12"), emptyMap())

    assertTrue(model11.matches(requirement))
    assertTrue(model12.matches(requirement))
    assertTrue(model13.matches(requirement))
  }
  fun testMatchingRangeNegative() {
    val requirement = toolchainRequirement("[11,13]")
    val model = ToolchainModel("jdk", mapOf("version" to "14"), emptyMap())
    assertFalse(model.matches(requirement))
  }

  fun testNotMatchingOpenRange() {
    val requirement = toolchainRequirement("[11,13)")
    val model = ToolchainModel("jdk", mapOf("version" to "13"), emptyMap())
    assertFalse(model.matches(requirement))
  }

  fun testMatchingRangeList() {
    val requirement = toolchainRequirement("11,12)")
    val model = ToolchainModel("jdk", mapOf("version" to "11"), emptyMap())
    assertTrue(model.matches(requirement))
  }

  fun testMatchingRangeListNegative() {
    val requirement = toolchainRequirement("11,12)")
    val model = ToolchainModel("jdk", mapOf("version" to "13"), emptyMap())
    assertFalse(model.matches(requirement))
  }

}


