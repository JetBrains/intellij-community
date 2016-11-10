/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.codeInsight

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl
import com.intellij.util.ThrowableRunnable
import com.jetbrains.env.PyEnvTaskRunner
import com.jetbrains.env.PyEnvTestCase
import com.jetbrains.python.codeInsight.typing.PyTypeShed
import com.jetbrains.python.inspections.PyTypeCheckerInspection
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.PySdkUtil
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.sdkTools.PyTestSdkTools
import com.jetbrains.python.sdkTools.SdkCreationType
import junit.framework.TestCase
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/**
 * @author vlan
 */
@RunWith(Parameterized::class)
class PyTypeShedTest(val path: String, val sdkPath: String) : PyEnvTestCase() {
  var fixture: CodeInsightTestFixture? = null

  @Before
  fun initialize() {
    val factory = IdeaTestFixtureFactory.getFixtureFactory()
    val fixtureBuilder = factory.createLightFixtureBuilder()
    fixture = factory.createCodeInsightFixture(fixtureBuilder.fixture, LightTempDirTestFixtureImpl(true))
    fixture?.testDataPath = ""  // testDataPath is required to be not null
    fixture?.setUp()
    initSdk()
  }

  fun initSdk() {
    val sdkByPath = PythonSdkType.findSdkByPath(sdkPath)
    val module = fixture?.module ?: return
    val project = fixture?.project ?: return
    if (sdkByPath != null) {
      ModuleRootModificationUtil.setModuleSdk(module, sdkByPath)
      EdtTestUtil.runInEdtAndWait(ThrowableRunnable {
        ApplicationManager.getApplication().runWriteAction {
          ProjectRootManager.getInstance(project).projectSdk = sdkByPath
        }
      })
      if (PySdkUtil.findSkeletonsDir(sdkByPath) == null) {
        PyTestSdkTools.generateTempSkeletonsOrPackages(sdkByPath, true, module)
      }
    }
    else {
      val sdkFile = StandardFileSystems.local().findFileByPath(sdkPath) ?: return
      PyTestSdkTools.createTempSdk(sdkFile, SdkCreationType.SDK_PACKAGES_AND_SKELETONS, module)
    }
  }

  @After
  fun deInitialize() {
    fixture?.tearDown()
  }

  @Test
  fun test() {
    EdtTestUtil.runInEdtAndWait(ThrowableRunnable {
      val typeShedPath = PyTypeShed.directoryPath ?: return@ThrowableRunnable
      fixture?.configureByFile("$typeShedPath/$path")
      fixture?.enableInspections(PyUnresolvedReferencesInspection::class.java)
      fixture?.enableInspections(PyTypeCheckerInspection::class.java)
      fixture?.checkHighlighting(true, false, true)
      val moduleSdk = PythonSdkType.findPythonSdk(fixture?.module)
      TestCase.assertNotNull(moduleSdk)
    })
  }

  companion object {
    @Parameterized.Parameters(name = "{0}: {1}")
    @JvmStatic fun params(): List<Array<Any>> {
      LightPlatformTestCase.initApplication()
      val tags = setOf("typeshed")
      val typeShedPath = PyTypeShed.directoryPath ?: return emptyList()
      val typeShedFile = File(typeShedPath)
      return getPythonRoots()
          .asSequence()
          .filter { PyEnvTaskRunner.isSuitableForTags(PyEnvTestCase.loadEnvTags(it), tags) }
          .map { PythonSdkType.getPythonExecutable(it) }
          .filterNotNull()
          .flatMap { sdkPath ->
            val flavor = PythonSdkFlavor.getFlavor(sdkPath) ?: return@flatMap emptySequence<Array<Any>>()
            val versionString = flavor.getVersionString(sdkPath) ?: return@flatMap emptySequence<Array<Any>>()
            val level = LanguageLevel.fromPythonVersion(versionString.removePrefix(flavor.name + " "))
            PyTypeShed.findRootsForLanguageLevel(level).asSequence()
                .flatMap { root: String ->
                  val results = File("$typeShedPath/$root").walk()
                      .filter { it.isFile && it.extension == "pyi" }
                      .map { arrayOf<Any>(it.relativeTo(typeShedFile).toString(), sdkPath) }
                  results
                }
          }
          .toList()
    }
  }
}
