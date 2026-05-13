// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.junit5.framework.impl

import com.intellij.openapi.project.Project
import com.intellij.testFramework.IndexingTestUtil
import com.jetbrains.python.junit5.framework.annotations.WithLanguageLevel
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

internal class PyWithLanguageLevelExtension : BeforeEachCallback, AfterEachCallback {
  private val namespace: ExtensionContext.Namespace =
    ExtensionContext.Namespace.create(PyWithLanguageLevelExtension::class.java)

  private companion object {
    const val PREV_LEVEL_KEY = "previousLanguageLevel"
  }

  override fun beforeEach(context: ExtensionContext) {
    val testMethod = context.requiredTestMethod
    val testClass = context.requiredTestClass

    val annotation = testMethod.getAnnotation(WithLanguageLevel::class.java)
                     ?: testClass.getAnnotation(WithLanguageLevel::class.java)
                     ?: return

    val level = annotation.level
    val project = context.getParentLookupFixtureManager().getRequired<Project>().get()

    val store = context.getStore(namespace)

    store.put(PREV_LEVEL_KEY, LanguageLevel.FORCE_LANGUAGE_LEVEL)

    PythonLanguageLevelPusher.setForcedLanguageLevel(project, level)
    IndexingTestUtil.waitUntilIndexesAreReady(project)
  }

  override fun afterEach(context: ExtensionContext) {
    val project = context.getParentLookupFixtureManager().getRequired<Project>().get()

    val store = context.getStore(namespace)
    val previousForced = store.remove(PREV_LEVEL_KEY, LanguageLevel::class.java)

    PythonLanguageLevelPusher.setForcedLanguageLevel(project, previousForced)
  }
}