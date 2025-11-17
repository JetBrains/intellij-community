// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.junit5.framework.impl

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.junit5.fixture.LookupFixtureExtension.Companion.getLookupFixtureManager
import com.jetbrains.python.junit5.framework.annotations.InspectionTest
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.Extension
import org.junit.jupiter.api.extension.ExtensionContext
import kotlin.reflect.full.findAnnotation

internal class PyInspectionTestJUnit5Extension : BeforeEachCallback, Extension {

  override fun beforeEach(context: ExtensionContext) {
    val testClass = context.requiredTestClass
    val annotation = testClass.kotlin.findAnnotation<InspectionTest>()
                     ?: error("@InspectionTest is missing on ${testClass.name}")

    val inspectionClasses = annotation.inspectionClasses.map { it.java }
    val testMethodLevelManager = context.getLookupFixtureManager()
    val codeInsightFixture = testMethodLevelManager.getRequired<CodeInsightTestFixture>()
    codeInsightFixture.get().enableInspections(inspectionClasses)
  }
}