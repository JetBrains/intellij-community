// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.junit5.framework.util

import com.intellij.psi.PsiFile
import com.intellij.python.junit5Tests.framework.metaInfo.Repository
import com.intellij.python.junit5Tests.framework.metaInfo.TestClassInfo
import com.intellij.python.junit5Tests.framework.metaInfo.TestMetaInfo
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.jetbrains.python.inspections.PyTypeCheckerInspection
import com.jetbrains.python.junit5.framework.annotations.InjectCodeInsightTestFixture
import com.jetbrains.python.junit5.framework.annotations.InspectionTest
import com.jetbrains.python.junit5.framework.annotations.PyCodeInsightTestApplication
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 * Holds a [CodeInsightTestFixture] field annotated with [InjectCodeInsightTestFixture].
 *
 * Used together with [PyJUnit5InjectFixtureInheritanceTest] to verify that the injector
 * walks the class hierarchy and populates fields declared on parent classes.
 */
abstract class PyInjectedFixtureFromBaseClass {
  @InjectCodeInsightTestFixture
  lateinit var inheritedFixture: CodeInsightTestFixture
}

/**
 * Regression test for [InjectCodeInsightTestFixture] on a field declared in a parent class.
 *
 * Before the fix, discovery via `Class.getDeclaredFields` returned only fields declared on
 * the exact runtime class, so a `lateinit var` on a base test class was silently skipped
 * and the first access threw [UninitializedPropertyAccessException].
 */
@TestClassInfo(Repository.PY_COMMUNITY)
@TestDataPath($$"$CONTENT_ROOT/../testData/junit5/showcase/PyTypeCheckerInspection")
@PyCodeInsightTestApplication
@InspectionTest(PyTypeCheckerInspection::class)
class PyJUnit5InjectFixtureInheritanceTest : PyInjectedFixtureFromBaseClass() {

  @Test
  @TestMetaInfo("single.py")
  fun inheritedFixtureIsInjected(mainFile: PsiFile) {
    Assertions.assertNotNull(inheritedFixture)
    inheritedFixture.doTestByFile(mainFile)
  }
}
