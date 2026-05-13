// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.junit5.framework.annotations

import com.intellij.codeInspection.LocalInspectionTool
import com.jetbrains.python.junit5.framework.impl.PyInspectionTestJUnit5Extension
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.reflect.KClass

/**
 * Enables the given local inspection(s) on the test fixture for the annotated test class.
 *
 * Requires a `CodeInsightTestFixture` to be registered as a JUnit 5 fixture, which is
 * what [PyCodeInsightTestApplication] arranges. If the fixture is missing, the extension
 * fails before the test runs with a message that names what to add.
 *
 * @param inspectionClasses Inspection classes to enable. Pass at least one.
 */
@TestOnly
@ApiStatus.Experimental
@ExtendWith(PyInspectionTestJUnit5Extension::class)
@Target(AnnotationTarget.CLASS, AnnotationTarget.ANNOTATION_CLASS)
annotation class InspectionTest(vararg val inspectionClasses: KClass<out LocalInspectionTool>)