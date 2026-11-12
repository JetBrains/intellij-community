// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.conda

import com.intellij.testFramework.junit5.TestApplication
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.management.PythonPackageInstallRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@Subsystems.Packaging
@Layers.Functional
@TestApplication
class CondaInstallationArgumentsTest {
  // PY-91412: specs are passed to conda as individual argv elements (no shell), so they must reach conda verbatim.
  // A stray leading double-quote used to be glued onto every spec, which conda 26.x rejects with InvalidMatchSpec.
  @Test
  fun testCondaSpecsAreNotQuoted() {
    val specs = listOf(
      PythonRepositoryPackageSpecification(CondaPackageRepository, "seaborn"),
      PythonRepositoryPackageSpecification(CondaPackageRepository, "seaborn", "0.13.2"),
    )
    val request = PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications(specs)

    val args = request.buildCondaInstallationArguments().getOrThrow()

    assertEquals(specs.map { it.nameWithVersionSpecs }, args)
    assertTrue(args.none { it.contains('"') }, "Conda specs must not contain quote characters: $args")
  }
}
