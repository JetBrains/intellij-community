// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.starter.build.server

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class DevBuildServerRunnerChoiceTest {
  @Test
  fun `local run outside Bazel gets the Bazel dev distribution`() {
    assertNull(legacyRunnerReason(onCi = false, testJvmOfBuildScripts = false, underBazel = false))
  }

  @Test
  fun `CI goes before the test JVM of the build scripts and Bazel`() {
    assertEquals("the run is on CI", legacyRunnerReason(onCi = true, testJvmOfBuildScripts = true, underBazel = true))
    assertEquals(
      "the build scripts started the test JVM ('intellij.build.test.jvm.of.build.scripts')",
      legacyRunnerReason(onCi = false, testJvmOfBuildScripts = true, underBazel = true),
    )
    assertEquals("the test runs under Bazel", legacyRunnerReason(onCi = false, testJvmOfBuildScripts = false, underBazel = true))
  }

  @Test
  fun `run under Bazel runfiles without the targets file fails with a recipe`() {
    assertTrue(legacyEngineHasNoBazelInputs(underBazel = true, bazelTargetsJsonFile = null))
    // The AT-5301 workaround keeps the legacy engine.
    assertFalse(legacyEngineHasNoBazelInputs(underBazel = true, bazelTargetsJsonFile = "_main/build/bazel-targets.json"))
    assertFalse(legacyEngineHasNoBazelInputs(underBazel = false, bazelTargetsJsonFile = null))
  }

  @Test
  fun `plain request gets no legacy engine reason`() {
    assertNull(legacyEngineReason(scrambling = false, docker = false, platformPrefix = "idea", baseIdePlatformPrefixForFrontend = null))
    assertNull(legacyEngineReason(scrambling = false, docker = false, platformPrefix = "JetBrainsClient", baseIdePlatformPrefixForFrontend = "idea"))
  }

  @Test
  fun `scrambling goes before Docker and the product`() {
    assertEquals(
      "the test asks for scrambling, and a Bazel dev distribution has none",
      legacyEngineReason(scrambling = true, docker = true, platformPrefix = "Rider", baseIdePlatformPrefixForFrontend = null),
    )
    assertEquals(
      "the test runs the IDE in a Docker container, so the target OS is not the host OS",
      legacyEngineReason(scrambling = false, docker = true, platformPrefix = "Rider", baseIdePlatformPrefixForFrontend = null),
    )
  }

  @Test
  fun `Rider, its frontend and Gateway keep the legacy engine`() {
    assertEquals(
      "the Bazel dev distribution of 'Rider' has known gaps",
      legacyEngineReason(scrambling = false, docker = false, platformPrefix = "Rider", baseIdePlatformPrefixForFrontend = null),
    )
    assertEquals(
      "the Bazel dev distribution of 'RiderJetBrainsClient' has known gaps",
      legacyEngineReason(scrambling = false, docker = false, platformPrefix = "JetBrainsClient", baseIdePlatformPrefixForFrontend = "Rider"),
    )
    assertEquals(
      "the Bazel dev distribution of 'Gateway' has known gaps",
      legacyEngineReason(scrambling = false, docker = false, platformPrefix = "Gateway", baseIdePlatformPrefixForFrontend = null),
    )
  }
}
