// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger

import com.intellij.idea.TestFor
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@Subsystems.Debugger
@Layers.Functional
@TestFor(issues = ["PY-70276"])
class PyPycachePrefixOptionTest {
  @Test
  fun `a separate -X option sets pycache_prefix`() {
    assertThat(hasPycachePrefixOption(listOf("-u", "-X", "pycache_prefix=/cache"))).isTrue()
  }

  @Test
  fun `a glued -X option sets pycache_prefix`() {
    assertThat(hasPycachePrefixOption(listOf("-u", "-Xpycache_prefix=/cache"))).isTrue()
  }

  @Test
  fun `pycache_prefix without -X before it is not the option`() {
    assertThat(hasPycachePrefixOption(listOf("pycache_prefix=/cache"))).isFalse()
    assertThat(hasPycachePrefixOption(listOf("-u", "pycache_prefix=/cache"))).isFalse()
  }

  @Test
  fun `another -X option is not pycache_prefix`() {
    assertThat(hasPycachePrefixOption(listOf("-X", "dev", "-Xutf8"))).isFalse()
  }
}
