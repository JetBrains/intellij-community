/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.env.python.typeshed

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.psi.stubs.StubUpdatingIndex
import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl
import com.intellij.util.ThrowableRunnable
import com.intellij.util.indexing.FileBasedIndex
import com.jetbrains.env.PyEnvTaskRunner
import com.jetbrains.env.PyEnvTestCase
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.PySdkUtil
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.PythonSdkUpdater
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.tools.sdkTools.PySdkTools
import org.junit.After
import org.junit.Before

/**
 * @author vlan
 */
abstract class PyTypeShedTestCase(protected val path: String, protected val sdkPath: String) : PyEnvTestCase() {
  protected var fixture: CodeInsightTestFixture? = null

  @Before
  fun initialize() {
    val factory = IdeaTestFixtureFactory.getFixtureFactory()
    val fixtureBuilder = factory.createLightFixtureBuilder()
    fixture = factory.createCodeInsightFixture(fixtureBuilder.fixture, LightTempDirTestFixtureImpl(true))
    fixture?.testDataPath = ""  // testDataPath is required to be not null
    fixture?.setUp()
    initSdk()
  }

  private fun initSdk() {
    val module = fixture?.module ?: return
    val project = fixture?.project ?: return
    val cachedSdk = sdkCache[sdkPath]
    val newSdk = if (cachedSdk == null) createSdk(sdkPath, project) else null
    val sdk = cachedSdk ?: newSdk ?: return
    sdkCache[sdkPath] = sdk
    val skeletonsDir = PySdkUtil.findSkeletonsDir(sdk)
    if (skeletonsDir == null || skeletonsDir.children?.isEmpty() ?: true) {
      PySdkTools.generateTempSkeletonsOrPackages(sdk, true, module)
    }
    EdtTestUtil.runInEdtAndWait(ThrowableRunnable {
      SdkConfigurationUtil.addSdk(sdk)
      ApplicationManager.getApplication().runWriteAction {
        ProjectRootManager.getInstance(project).projectSdk = sdk
      }
      ModuleRootModificationUtil.setModuleSdk(module, sdk)
    })
  }

  private fun createSdk(sdkPath: String, project: Project): Sdk? {
    val sdkFile = StandardFileSystems.local().findFileByPath(sdkPath) ?: return null
    var sdkVar: Sdk? = null
    EdtTestUtil.runInEdtAndWait(ThrowableRunnable {
      sdkVar = SdkConfigurationUtil.setupSdk(emptyArray(), sdkFile, PythonSdkType.getInstance(), true, null, null)
    })
    val sdk = sdkVar ?: return null
    val modificator = sdk.sdkModificator
    val paths = PythonSdkType.getSysPathsFromScript(sdkPath)
    PythonSdkUpdater.filterRootPaths(sdk, paths, project).forEach {
      modificator.addRoot(it, OrderRootType.CLASSES)
    }
    EdtTestUtil.runInEdtAndWait(ThrowableRunnable {
      modificator.commitChanges()
      val index = FileBasedIndex.getInstance()
      index.requestRebuild(StubUpdatingIndex.INDEX_ID)
    })
    return sdk
  }

  @After
  fun deInitialize() {
    fixture?.tearDown()
  }

  companion object {
    private val sdkCache = mutableMapOf<String, Sdk>()

    internal fun getSdkPaths(): List<String> {
      val tags = setOf("typeshed")
      return getPythonRoots()
        .asSequence()
        .filter { PyEnvTaskRunner.isSuitableForTags(loadEnvTags(it), tags) }
        .map { PythonSdkType.getPythonExecutable(it) }
        .filterNotNull()
        .toList()
    }

    internal fun getLanguageLevel(sdkPath: String): LanguageLevel? {
      val flavor = PythonSdkFlavor.getFlavor(sdkPath) ?: return null
      val versionString = flavor.getVersionString(sdkPath) ?: return null
      return LanguageLevel.fromPythonVersion(versionString.removePrefix(flavor.name).trim())
    }
  }
}
