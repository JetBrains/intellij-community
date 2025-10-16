// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.JUnit5.util

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.python.junit5Tests.framework.PyDefaultTestApplication
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.junit5.fixture.LookupFixtureExtension.Companion.getLookupFixtureManager
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.*
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation

@TestOnly
@PyDefaultTestApplication
@ExtendWith(PyCodeInsightJUnit5Extension::class)
@ExtendWith(PyInspectionTestJUnit5Extension::class)
@ExtendWith(PyWithLanguageLevelExtension::class)
annotation class InspectionTest(val inspectionClass: KClass<out LocalInspectionTool>)

fun InspectionTest.getInspectionClass(): Class<out LocalInspectionTool> = this.inspectionClass.java

class PyInspectionTestJUnit5Extension : AfterEachCallback, BeforeEachCallback, Extension {

  override fun beforeEach(context: ExtensionContext) {
    val testClass = context.requiredTestClass
    val annotation = testClass.kotlin.findAnnotation<InspectionTest>()
                     ?: error("@InspectionTest is missing on ${testClass.name}")

    val inspectionClass: Class<out LocalInspectionTool> = annotation.inspectionClass.java
    val testMethodLevelManager = context.getLookupFixtureManager()
    val codeInsightFixture = testMethodLevelManager.getRequired<CodeInsightTestFixture>()
    codeInsightFixture.get().enableInspections(inspectionClass)
    InspectionProfileImpl.INIT_INSPECTIONS = true
  }

  override fun afterEach(context: ExtensionContext) {
    InspectionProfileImpl.INIT_INSPECTIONS = false
  }
}