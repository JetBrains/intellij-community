// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.JUnit5.util

import com.intellij.openapi.application.writeAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.python.junit5Tests.framework.MultiFileTest
import com.intellij.python.junit5Tests.framework.metaInfo.TestMetaInfoExtension.Companion.getTestClassInfo
import com.intellij.python.junit5Tests.framework.metaInfo.resolveTestName
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.junit5.fixture.LookupFixture
import com.intellij.testFramework.junit5.fixture.LookupFixtureExtension.Companion.getLookupFixtureManager
import com.intellij.testFramework.junit5.fixture.LookupFixtureExtension.Companion.registerImplicitFixtures
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.pathInProjectFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.jetbrains.python.PythonMockSdk
import com.jetbrains.python.psi.LanguageLevel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import java.nio.file.Path

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class InjectCodeInsightTestFixture

class PyCodeInsightJUnit5Extension : BeforeAllCallback, BeforeEachCallback {

  companion object {
    private const val DEFAULT_CODE_INSIGHT: String = "DEFAULT_CODE_INSIGHT"
    private const val MOCK_SDK: String = "MOCK_SDK"
  }

  override fun beforeAll(context: ExtensionContext) {
    val manager = context.getLookupFixtureManager()
    val projectFixture = manager.getRequired<Project>()
    val moduleFixture = manager.getRequired<Module>()
    val implicitFixtures = mutableListOf<LookupFixture>()

    manager.getOrDefault {
      projectFixture.pyMockSdkFixture(projectFixture, moduleFixture) { PythonMockSdk.create(LanguageLevel.getLatest()) }.also {
        implicitFixtures += LookupFixture(MOCK_SDK, it, true)
      }
    }

    runBlocking {
      context.registerImplicitFixtures(implicitFixtures, static = true)
    }

    IndexingTestUtil.waitUntilIndexesAreReady(projectFixture.get())
  }

  override fun beforeEach(context: ExtensionContext) {
    val classLevelManager = context.parent.get().getLookupFixtureManager()
    val projectFixture = classLevelManager.getRequired<Project>()
    val implicitFixtures = mutableListOf<LookupFixture>()
    val metaInfo = context.getTestClassInfo()
    val testName = context.resolveTestName()

    val codeInsightFixture = codeInsightFixture(projectFixture, projectFixture.pathInProjectFixture(Path.of("")))
    classLevelManager.getOrDefault {
      codeInsightFixture.also {
        implicitFixtures += LookupFixture(DEFAULT_CODE_INSIGHT, it, true)
      }
    }
    runBlocking {
      context.registerImplicitFixtures(implicitFixtures, static = false)
    }
    codeInsightFixture.get().testDataPath = metaInfo.testDataPath?.resolve(testName)?.toString()
                                            ?: error("Cannot resolve test data path for $testName")

    val isMultiFile = context.testMethod.get().getAnnotation(MultiFileTest::class.java) != null
    if (isMultiFile) {
      codeInsightFixture.get().copyDirectoryToProject("", "")
    }

    val testInstance = context.requiredTestInstance
    testInstance::class.java.declaredFields
      .filter { it.isAnnotationPresent(InjectCodeInsightTestFixture::class.java) }
      .forEach { field ->

        require(CodeInsightTestFixture::class.java.isAssignableFrom(field.type)) {
          "Field ${field.name} annotated with @InjectCodeInsightTestFixture is not of type CodeInsightTestFixture"
        }

        field.isAccessible = true
        field.set(testInstance, codeInsightFixture.get())
      }
  }
}

fun TestFixture<Project>.pyMockSdkFixture(project: TestFixture<Project>, module: TestFixture<Module>, sdkProvider: () -> Sdk):
  TestFixture<Sdk> = testFixture {
  this@pyMockSdkFixture.init()
  val sdk = sdkProvider()
  writeAction {
    ProjectJdkTable.getInstance().addJdk(sdk)
    ProjectRootManager.getInstance(project.get()).projectSdk = sdk
    ModuleRootModificationUtil.setModuleSdk(module.get(), sdk)
  }
  initialized(sdk) {
    writeAction {
      ModuleRootModificationUtil.setModuleSdk(module.get(), null)
      ProjectRootManager.getInstance(project.get()).projectSdk = null
      ProjectJdkTable.getInstance().removeJdk(sdk)
    }
  }
}