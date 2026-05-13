// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.junit5.framework.annotations

import com.intellij.python.junit5Tests.framework.metaInfo.TestMetaInfo
import org.jetbrains.annotations.ApiStatus
import org.junit.jupiter.api.Test

/**
 * Marks a JUnit 5 multi-file Python code insight test.
 *
 * The subdirectory containing the main test file is copied to the test project before
 * the test runs, and the main file is injected as a [com.intellij.psi.PsiFile] parameter.
 *
 * ## Subdirectory and main file
 *
 * By default the main file is `<TestDataPath>/<testName>/a.py`, where `<testName>` is
 * derived from the test method name by [com.intellij.testFramework.PlatformTestUtil.getTestName]:
 * the optional leading `test` is stripped and the first letter is lower-cased. Examples:
 *
 * - `fun multiFileTest()` → main file `<TestDataPath>/multiFileTest/a.py`,
 *   directory `<TestDataPath>/multiFileTest/` is copied.
 * - `fun testFoo()` → main file `<TestDataPath>/foo/a.py`,
 *   directory `<TestDataPath>/foo/` is copied.
 *
 * The main file path can be overridden with [TestMetaInfo]
 * (e.g. `@TestMetaInfo("sub/testCase/a.py")`); in that case the *parent* of the
 * overridden path is copied as the test subdirectory.
 *
 * `@MultiFileTest` already implies [Test]; do not add `@Test` to the method.
 */
@Test
@ApiStatus.Experimental
@TestMetaInfo(resourcePath = $$"$TEST_NAME/a.py")
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class MultiFileTest