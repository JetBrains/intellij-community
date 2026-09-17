// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.toolchains

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ToolchainsModelTest {

  @Test
  fun testMatchingExactVersion() {
    val requirement = toolchainRequirement("11")
    val model = ToolchainModel("jdk", mapOf("version" to "11"), emptyMap())
    assertTrue(model.matches(requirement))
  }

  @Test
  fun testMatchingRange() {
    val requirement = toolchainRequirement("[11,13]")
    val model11 = ToolchainModel("jdk", mapOf("version" to "11"), emptyMap())
    val model12 = ToolchainModel("jdk", mapOf("version" to "12"), emptyMap())
    val model13 = ToolchainModel("jdk", mapOf("version" to "13"), emptyMap())

    assertTrue(model11.matches(requirement))
    assertTrue(model12.matches(requirement))
    assertTrue(model13.matches(requirement))
  }

  @Test
  fun testMatchingRangeNegative() {
    val requirement = toolchainRequirement("[11,13]")
    val model = ToolchainModel("jdk", mapOf("version" to "14"), emptyMap())
    assertFalse(model.matches(requirement))
  }

  @Test
  fun testNotMatchingOpenRange() {
    val requirement = toolchainRequirement("[11,13)")
    val model = ToolchainModel("jdk", mapOf("version" to "13"), emptyMap())
    assertFalse(model.matches(requirement))
  }

  @Test
  fun testMatchingRangeList() {
    val requirement = toolchainRequirement("11,12)")
    val model = ToolchainModel("jdk", mapOf("version" to "11"), emptyMap())
    assertTrue(model.matches(requirement))
  }

  @Test
  fun testMatchingRangeListNegative() {
    val requirement = toolchainRequirement("11,12)")
    val model = ToolchainModel("jdk", mapOf("version" to "13"), emptyMap())
    assertFalse(model.matches(requirement))
  }

  @Test
  fun testBareVersionRequiresExactMatch() {
    val requirement = toolchainRequirement("47")
    val model = ToolchainModel("jdk", mapOf("version" to "47.0.1"), emptyMap())
    assertFalse(model.matches(requirement))
  }

  @Test
  fun testFullVersionMatchesRange() {
    val requirement = toolchainRequirement("[47.0.1,48)")
    val model = ToolchainModel("jdk", mapOf("version" to "47.0.1"), emptyMap())
    assertTrue(model.matches(requirement))
  }

  @Test
  fun testMatchingVendor() {
    val requirement = ToolchainRequirement.Builder(ToolchainRequirement.JDK_TYPE)
      .set("vendor", "Eclipse Temurin")
      .build()
    val model = ToolchainModel("jdk", mapOf("vendor" to "Eclipse Temurin"), emptyMap())
    assertTrue(model.matches(requirement))
  }

  @Test
  fun testMatchingVendorNegative() {
    val requirement = ToolchainRequirement.Builder(ToolchainRequirement.JDK_TYPE)
      .set("vendor", "Eclipse Temurin")
      .build()
    val wrongVendor = ToolchainModel("jdk", mapOf("vendor" to "Oracle OpenJDK"), emptyMap())
    val noVendor = ToolchainModel("jdk", emptyMap(), emptyMap())
    assertFalse(wrongVendor.matches(requirement))
    assertFalse(noVendor.matches(requirement))
  }

  @Test
  fun testMatchingEnvList() {
    val requirement = ToolchainRequirement.Builder(ToolchainRequirement.JDK_TYPE)
      .set("env", "JAVA_HOME,TEST_HOME")
      .build()
    val model = ToolchainModel("jdk", mapOf("env" to "JAVA_HOME,TEST_HOME,OTHER_HOME"), emptyMap())
    assertTrue(model.matches(requirement))
  }

  @Test
  fun testMatchingEnvListNegative() {
    val requirement = ToolchainRequirement.Builder(ToolchainRequirement.JDK_TYPE)
      .set("env", "JAVA_HOME,TEST_HOME")
      .build()
    val model = ToolchainModel("jdk", mapOf("env" to "JAVA_HOME"), emptyMap())
    assertFalse(model.matches(requirement))
  }

}

