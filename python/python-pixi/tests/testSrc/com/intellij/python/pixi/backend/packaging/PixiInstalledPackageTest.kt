// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.packaging

import com.intellij.idea.TestFor
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.isSuccess
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@Subsystems.PackagingRequirements
@Layers.Functional
@TestFor(issues = ["PY-91735"])
internal class PixiInstalledPackageTest {
  @Test
  fun `a normalized UI name recovers one native name`() {
    val pkg = PixiPackage("typing_extensions", "4.15.0", "conda")
    assertThat(resolvePixiInstalledPackage(listOf(pkg), "typing-extensions").getOrThrow()).isEqualTo(pkg)
  }

  @Test
  fun `a normalized collision cannot change the wrong package`() {
    val packages = listOf(PixiPackage("typing_extensions", "4.15.0", "conda"), PixiPackage("typing-extensions", "4.15.0", "conda"))
    assertThat(resolvePixiInstalledPackage(packages, "typing-extensions").isSuccess).isFalse()
    assertThat(resolvePixiInstalledPackage(packages, "typing_extensions").isSuccess).isFalse()
  }

  @Test
  fun `a normalized browser selection recovers one native name`() {
    val candidates = listOf(PixiNativePackage("raw_name", "1.0"), PixiNativePackage("raw_name", "2.0"),
                            PixiNativePackage("raw_other_name", "1.0"))
    assertThat(resolvePixiNativePackageName("raw-name", candidates).getOrThrow()).isEqualTo("raw_name")
  }

  @Test
  fun `a browser selection cannot guess between distinct native names`() {
    val candidates = listOf(PixiNativePackage("raw_name", "1.0"), PixiNativePackage("raw-name", "9.0"))
    assertThat(resolvePixiNativePackageName("raw-name", candidates).isSuccess).isFalse()
  }
}
