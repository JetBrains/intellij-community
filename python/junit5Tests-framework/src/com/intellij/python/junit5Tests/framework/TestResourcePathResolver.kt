// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.framework

import com.intellij.python.junit5Tests.framework.metaInfo.TestClassInfoData
import org.junit.jupiter.api.extension.ExtensionContext

fun interface TestResourcePathResolver {
  /**
   * Transforms the `resourcePath` (relative string provided by [com.intellij.python.junit5Tests.framework.metaInfo.TestMetaInfo])
   * and returns the transformed value. Implementations may use the
   * `context`, the resolved `testName`, and the class-level info to compute it.
   */
  fun resolve(
    resourcePath: String,
    context: ExtensionContext,
    testName: String,
    classInfo: TestClassInfoData
  ): String
}