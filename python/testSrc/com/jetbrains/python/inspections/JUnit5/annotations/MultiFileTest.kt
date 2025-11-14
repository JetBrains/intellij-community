// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.JUnit5.annotations

import com.intellij.python.junit5Tests.framework.metaInfo.TestMetaInfo
import org.jetbrains.annotations.ApiStatus
import org.junit.jupiter.api.Test

@Test
@ApiStatus.Experimental
@TestMetaInfo(resourcePath = $$"$TEST_NAME/a.py")
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class MultiFileTest