// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.framework.metaInfo

import com.intellij.python.junit5Tests.framework.TestResourcePathResolver
import org.jetbrains.annotations.TestOnly
import kotlin.reflect.KClass

/**
 * Annotation used to specify a custom resolver for test resource paths.
 */
@TestOnly
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS)
annotation class WithCustomTestResourcePathResolver(val value: KClass<out TestResourcePathResolver>)