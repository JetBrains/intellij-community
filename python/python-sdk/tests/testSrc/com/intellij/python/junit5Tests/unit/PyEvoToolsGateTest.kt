// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.python.sdk.common.evolution.PyEvoRegistry
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Guards the rule that hides the tools of the interpreter widget on a non-local eel while `python.eel.native` is off
 * (PY-93438). There the popup keeps only "Associated", "Custom", "Settings" and "Manage Packages".
 */
@TestApplication
internal class PyEvoToolsGateTest {
  /** A machine that is not the local one, as a WSL distribution or a Docker container is. */
  private val remoteDescriptor = object : EelDescriptor {
    override val name: String = "Remote"
    override val osFamily: EelOsFamily = EelOsFamily.Posix
  }

  @Test
  @RegistryKey("python.eel.native", "false")
  fun `tools are hidden on a non-local eel when the flag is off`() {
    assertThat(PyEvoRegistry.areToolsEnabledFor(remoteDescriptor)).isFalse()
  }

  @Test
  @RegistryKey("python.eel.native", "true")
  fun `tools are shown on a non-local eel when the flag is on`() {
    assertThat(PyEvoRegistry.areToolsEnabledFor(remoteDescriptor)).isTrue()
  }

  @Test
  @RegistryKey("python.eel.native", "false")
  fun `tools are shown on the local machine when the flag is off`() {
    assertThat(PyEvoRegistry.areToolsEnabledFor(LocalEelDescriptor)).isTrue()
  }
}
