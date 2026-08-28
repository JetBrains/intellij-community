// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.junit5.framework.annotations

import com.jetbrains.python.psi.LanguageLevel
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

/**
 * Annotation to override the default (latest) language level used for a test.
 */
@ApiStatus.Experimental
@TestOnly
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class WithLanguageLevel(val level: LanguageLevel)