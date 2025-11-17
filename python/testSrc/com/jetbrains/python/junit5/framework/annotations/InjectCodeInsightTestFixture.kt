// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.junit5.framework.annotations

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

/**
 * Injects [com.intellij.testFramework.fixtures.CodeInsightTestFixture] into a test class field to reduce verbosity.
 *
 * See example in [com.jetbrains.python.junit5.framework.showcase.PyJUnit5CodeInsightExampleTest].
 */
@TestOnly
@ApiStatus.Experimental
@Target(AnnotationTarget.FIELD)
annotation class InjectCodeInsightTestFixture