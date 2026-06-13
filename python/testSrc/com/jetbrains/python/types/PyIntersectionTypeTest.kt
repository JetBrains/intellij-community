// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.types

import com.intellij.idea.TestFor
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.fixtures.PyCodeInsightTestCase.OrderRootTypeEnum
import com.jetbrains.python.fixtures.PyCodeInsightTestCase.SdkRoot
import com.jetbrains.python.fixtures.PyCodeInsightTestCase.TestCaseOptions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Type tests for the `ty_extensions.Intersection` form, and for operations whose subject is an
 * intersection type: attribute, property and descriptor access, and calls.
 * [PySubtypingTypeTest] covers the assignability of an intersection.
 */
@Subsystems.Typing
@Components.TypeInference
@Layers.Functional
class PyIntersectionTypeTest : PyCodeInsightTestCase() {

  /**
   * `ty_extensions` is an SDK root here, so [TestCaseOptions.assertSdkRootsNotParsed] also holds that the
   * form resolves from the stub, without a parse of `ty_extensions.pyi`.
   */
  @Nested
  inner class TyExtensionsForm {

    @Test
    @TestFor(issues = ["PY-87024"])
    @TestCaseOptions(additionalSdkRoots = [SdkRoot(TY_EXTENSIONS_ROOT, OrderRootTypeEnum.CLASSES)])
    fun `Intersection of two members`() = test("""
      from ty_extensions import Intersection

      expr: Intersection[int, str]
      # └ TYPE int & str
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-87024"])
    @TestCaseOptions(additionalSdkRoots = [SdkRoot(TY_EXTENSIONS_ROOT, OrderRootTypeEnum.CLASSES)])
    fun `Intersection of one member collapses to that member`() = test("""
      from ty_extensions import Intersection

      expr: Intersection[int]
      # └ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-87024"])
    @TestCaseOptions(additionalSdkRoots = [SdkRoot(TY_EXTENSIONS_ROOT, OrderRootTypeEnum.CLASSES)])
    fun `Intersection of no members collapses to the top type`() = test("""
      from ty_extensions import Intersection

      expr: Intersection[()]
      # └ TYPE object
      """.trimIndent())
  }

  companion object {
    private const val TY_EXTENSIONS_ROOT = "types/tyExtensions"
  }
}
