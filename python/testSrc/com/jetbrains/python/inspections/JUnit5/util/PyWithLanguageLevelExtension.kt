// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.JUnit5.util

import com.intellij.openapi.project.Project
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.junit5.fixture.LookupFixtureExtension.Companion.getClassLevelLookupFixtureManager
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class PyWithLanguageLevelExtension : BeforeEachCallback, AfterEachCallback {
  private var previousForced: LanguageLevel? = null

  override fun beforeEach(context: ExtensionContext) {
    val testMethod = context.requiredTestMethod
    val testClass = context.requiredTestClass

    val annotation = testMethod.getAnnotation(WithLanguageLevel::class.java)
                     ?: testClass.getAnnotation(WithLanguageLevel::class.java)
                     ?: return

    val level = annotation.level
    val project = context.getClassLevelLookupFixtureManager().getRequired<Project>().get()

    previousForced = LanguageLevel.FORCE_LANGUAGE_LEVEL

    PythonLanguageLevelPusher.setForcedLanguageLevel(project, level)
    IndexingTestUtil.waitUntilIndexesAreReady(project)
  }

  override fun afterEach(context: ExtensionContext) {
    val project = context.getClassLevelLookupFixtureManager().getRequired<Project>().get()

    PythonLanguageLevelPusher.setForcedLanguageLevel(project, previousForced)
  }
}
