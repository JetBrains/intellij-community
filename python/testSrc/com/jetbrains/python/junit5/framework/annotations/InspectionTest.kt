// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.junit5.framework.annotations

import com.intellij.codeInspection.LocalInspectionTool
import com.jetbrains.python.junit5.framework.impl.PyInspectionTestJUnit5Extension
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.reflect.KClass

/**
 * Enables the specific inspection(s) for the test class.
 */
@TestOnly
@ApiStatus.Experimental
@ExtendWith(PyInspectionTestJUnit5Extension::class)
@Target(AnnotationTarget.CLASS, AnnotationTarget.ANNOTATION_CLASS)
annotation class InspectionTest(vararg val inspectionClasses: KClass<out LocalInspectionTool>)