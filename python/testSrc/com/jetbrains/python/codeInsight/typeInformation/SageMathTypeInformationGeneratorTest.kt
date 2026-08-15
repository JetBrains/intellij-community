// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.typeInformation

import com.intellij.idea.TestFor
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestFor(classes = [SageMathTypeInformationGenerator::class])
@Subsystems.CodeInsight
@Layers.Functional
class SageMathTypeInformationGeneratorTest : PyCodeInsightTestCase() {
  @Test
  fun `detects supported SageMath distribution names`() {
    assertTrue(SageMathTypeInformationGenerator.hasSageMathDistribution(listOf("sagemath")))
    assertTrue(SageMathTypeInformationGenerator.hasSageMathDistribution(listOf("SageMath_Standard")))
    assertTrue(SageMathTypeInformationGenerator.hasSageMathDistribution(listOf("sage")))
  }

  @Test
  fun `ignores unrelated distributions`() {
    assertFalse(SageMathTypeInformationGenerator.hasSageMathDistribution(listOf("numpy", "sympy")))
  }
}
