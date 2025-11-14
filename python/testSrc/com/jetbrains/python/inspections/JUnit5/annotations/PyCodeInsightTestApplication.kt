// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.JUnit5.annotations

import com.intellij.python.junit5Tests.framework.metaInfo.TestMetaInfoExtension
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.LookupFixtureExtension
import com.jetbrains.python.inspections.JUnit5.impl.PyCodeInsightFixturesExtension
import com.jetbrains.python.inspections.JUnit5.impl.PyJUnit5CodeInsightFixtureInjector
import com.jetbrains.python.inspections.JUnit5.impl.PyTestDataExtension
import com.jetbrains.python.inspections.JUnit5.impl.PyWithLanguageLevelExtension
import org.jetbrains.annotations.ApiStatus
import org.junit.jupiter.api.extension.ExtendWith


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

