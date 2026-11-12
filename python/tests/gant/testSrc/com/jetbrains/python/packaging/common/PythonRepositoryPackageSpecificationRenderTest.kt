// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.common

import com.intellij.python.requirements.pyRequirement
import com.intellij.python.requirements.pyRequirementVersionSpec
import com.intellij.testFramework.junit5.TestApplication
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.packaging.repository.PyPackageRepository
import com.jetbrains.python.packaging.requirement.PyRequirementRelation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@Subsystems.Packaging
@Layers.Functional
@TestApplication
class PythonRepositoryPackageSpecificationRenderTest {
  private val repository = PyPackageRepository()

  // PY-91457: nameWithVersionSpec used to render only the first spec, silently dropping the upper bound.
  @Test
  fun testAllVersionSpecsAreRendered() {
    val requirement = pyRequirement(
      "pytest",
      listOf(
        pyRequirementVersionSpec(PyRequirementRelation.GTE, "7.0"),
        pyRequirementVersionSpec(PyRequirementRelation.LT, "8.0"),
      ),
      emptyList(),
      null,
    )
    val spec = PythonRepositoryPackageSpecification(repository, requirement)

    assertEquals("pytest>=7.0,<8.0", spec.nameWithVersionSpecs)
  }

  @Test
  fun testSingleVersionSpecIsRendered() {
    val spec = PythonRepositoryPackageSpecification(repository, "pytest", "7.0")

    assertEquals("pytest==7.0", spec.nameWithVersionSpecs)
  }

  @Test
  fun testNameOnlyWhenNoVersionSpec() {
    val spec = PythonRepositoryPackageSpecification(repository, "pytest")

    assertEquals("pytest", spec.nameWithVersionSpecs)
  }
}
