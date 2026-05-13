// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.junit5.framework.impl

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.junit5.fixture.LookupFixture
import com.intellij.testFramework.junit5.fixture.LookupFixtureExtension.Companion.getLookupFixtureManager
import com.intellij.testFramework.junit5.fixture.LookupFixtureExtension.Companion.registerImplicitFixtures
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.jetbrains.python.PyNames
import com.jetbrains.python.PythonMockSdk
import com.jetbrains.python.junit5.framework.pyCodeInsightFixture
import com.jetbrains.python.junit5.framework.pyMockSdkFixture
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.impl.IntentionalUnstubbing
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.Extension
import org.junit.jupiter.api.extension.ExtensionContext
import java.nio.file.Path

private const val DEFAULT_PROJECT: String = "DEFAULT_PROJECT"
private const val DEFAULT_PY_MODULE: String = "DEFAULT_PY_MODULE"
private const val DEFAULT_TMP_DIR: String = "DEFAULT_TMP_DIR"
private const val MOCK_SDK: String = "MOCK_SDK"
private const val DEFAULT_SOURCE_ROOT: String = "DEFAULT_SOURCE_ROOT"
private const val DEFAULT_CODE_INSIGHT: String = "DEFAULT_CODE_INSIGHT"

internal class PyCodeInsightFixturesExtension : BeforeAllCallback, BeforeEachCallback, AfterEachCallback, Extension {

  override fun beforeAll(context: ExtensionContext) {
    val manager = context.getLookupFixtureManager()

    val implicitFixtures = mutableListOf<LookupFixture>()

    val tmpDirFixture = manager.getOrDefault {
      tempPathFixture().also {
        implicitFixtures += LookupFixture(DEFAULT_TMP_DIR, it, true)
      }
    }

    val project = manager.getOrDefault {
      projectFixture(openAfterCreation = true, pathFixture = tmpDirFixture).also {
        implicitFixtures += LookupFixture(DEFAULT_PROJECT, it, true)
      }
    }

    val module = manager.getOrDefault {
      project.moduleFixture(name = context.uniqueId, moduleType = PyNames.PYTHON_MODULE_ID).also {
        implicitFixtures += LookupFixture(DEFAULT_PY_MODULE, it, true)
      }
    }

    manager.getOrDefault {
      project.pyMockSdkFixture(module) { PythonMockSdk.create(LanguageLevel.getLatest()) }.also {
        implicitFixtures += LookupFixture(MOCK_SDK, it, true)
      }
    }

    runBlocking {
      context.registerImplicitFixtures(implicitFixtures, static = true)
    }

    IndexingTestUtil.waitUntilIndexesAreReady(project.get())
  }

  override fun beforeEach(context: ExtensionContext) {
    val manager = context.getLookupFixtureManager()
    val implicitFixtures = mutableListOf<LookupFixture>()

    val classLevelManager = context.getParentLookupFixtureManager()
    val project = classLevelManager.getRequired<Project>()
    val tempDirFixture = classLevelManager.getRequired<Path>()
    val module = classLevelManager.getRequired<Module>()

    manager.getOrDefault {
      module.sourceRootFixture(
        pathFixture = tempDirFixture,
      ).also {
        implicitFixtures += LookupFixture(DEFAULT_SOURCE_ROOT, it, true)
      }
    }

    manager.getOrDefault {
      pyCodeInsightFixture(project, tempDirFixture).also {
        implicitFixtures += LookupFixture(DEFAULT_CODE_INSIGHT, it, true)
      }
    }

    runBlocking {
      context.registerImplicitFixtures(implicitFixtures, static = false)
    }
  }

  override fun afterEach(context: ExtensionContext) {
    IntentionalUnstubbing.resetForciblyUnstubbedFileSet()
  }
}