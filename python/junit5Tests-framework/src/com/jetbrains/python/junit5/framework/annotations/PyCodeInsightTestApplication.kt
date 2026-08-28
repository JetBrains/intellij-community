// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.junit5.framework.annotations

import com.intellij.python.junit5Tests.framework.metaInfo.TestMetaInfoExtension
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.LookupFixtureExtension
import com.jetbrains.python.junit5.framework.impl.PyCodeInsightFixturesExtension
import com.jetbrains.python.junit5.framework.impl.PyJUnit5CodeInsightFixtureInjector
import com.jetbrains.python.junit5.framework.impl.PyTestDataExtension
import com.jetbrains.python.junit5.framework.impl.PyWithLanguageLevelExtension
import org.jetbrains.annotations.ApiStatus
import org.junit.jupiter.api.extension.ExtendWith

/**
 * PyDefaultTestApplication is a test annotation used to initialize a shared application context,
 * adapted for code-insight tests. It enriches it with various predefined test extensions
 * (project, module, source root, mock SDK and [com.intellij.testFramework.fixtures.CodeInsightTestFixture] itself).
 *
 * It initializes a shared [com.intellij.openapi.application.Application] instance before any tests are run
 * and disposes it after all tests finish, through the [TestApplication] annotation.
 */
@TestApplication
@ApiStatus.Experimental
@ExtendWith(LookupFixtureExtension::class)
@ExtendWith(TestMetaInfoExtension::class)
@ExtendWith(PyCodeInsightFixturesExtension::class)
@ExtendWith(PyTestDataExtension::class)
@ExtendWith(PyWithLanguageLevelExtension::class)
@ExtendWith(PyJUnit5CodeInsightFixtureInjector::class)
@Target(AnnotationTarget.CLASS)
annotation class PyCodeInsightTestApplication

