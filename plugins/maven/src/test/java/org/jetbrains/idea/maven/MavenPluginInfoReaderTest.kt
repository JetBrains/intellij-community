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
package org.jetbrains.idea.maven

import com.intellij.maven.testFramework.MavenTestCase
import org.jetbrains.idea.maven.utils.MavenArtifactUtil
import org.jetbrains.idea.maven.utils.MavenPluginInfo
import java.nio.file.Path

class MavenPluginInfoReaderTest : MavenTestCase() {
  override fun runInDispatchThread() = false

  private var p: MavenPluginInfo? = null

  override fun setUp() {
    super.setUp()
    repositoryPath = MavenCustomRepositoryHelper(dir, "plugins").getTestDataPath("plugins")
    p = MavenArtifactUtil.readPluginInfo(Path.of(repositoryFile.toAbsolutePath().toString(), "org/apache/maven/plugins", "maven-compiler-plugin", "2.0.2", "maven-compiler-plugin-2.0.2.jar"))
  }

  fun testLoadingPluginInfo() {
    assertEquals("org.apache.maven.plugins", p!!.groupId)
    assertEquals("maven-compiler-plugin", p!!.artifactId)
    assertEquals("2.0.2", p!!.version)
  }

  fun testGoals() {
    assertEquals("compiler", p!!.goalPrefix)

    val qualifiedGoals: MutableList<String> = ArrayList()
    val displayNames: MutableList<String> = ArrayList()
    val goals: MutableList<String> = ArrayList()
    for (m in p!!.mojos) {
      goals.add(m.goal)
      qualifiedGoals.add(m.qualifiedGoal)
      displayNames.add(m.displayName)
    }

    assertOrderedElementsAreEqual(goals, "compile", "testCompile")
    assertOrderedElementsAreEqual(qualifiedGoals,
                                  "org.apache.maven.plugins:maven-compiler-plugin:2.0.2:compile",
                                  "org.apache.maven.plugins:maven-compiler-plugin:2.0.2:testCompile")
    assertOrderedElementsAreEqual(displayNames, "compiler:compile", "compiler:testCompile")
  }
}
