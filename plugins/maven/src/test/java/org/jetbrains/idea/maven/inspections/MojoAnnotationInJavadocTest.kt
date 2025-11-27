/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.inspections

import com.intellij.codeInspection.javaDoc.JavadocDeclarationInspection
import com.intellij.openapi.application.PluginPathManager
import com.intellij.testFramework.TestObservation
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import kotlinx.coroutines.runBlocking

class MojoAnnotationInJavadocTest : LightJavaCodeInsightFixtureTestCase() {
  override fun runInDispatchThread() = false

  override fun getTestDataPath(): String {
    return PluginPathManager.getPluginHomePath("maven") + "/src/test/data/inspections/javadocMojoValidTags"
  }

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(JavadocDeclarationInspection())
  }

  fun testTestMojo() = runBlocking {
    doTest()
  }

  private suspend fun doTest() {
    myFixture.configureByFile(getTestName(false) + ".java")
    myFixture.checkHighlighting()
    TestObservation.awaitConfiguration(project)
  }
}
