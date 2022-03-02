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
package org.jetbrains.idea.maven.importing

import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.junit.Test

/**
 * @author Sergey Evdokimov
 */
class MavenDontExcludeTargetTest extends MavenMultiVersionImportingTestCase {

  void testDontExcludeTargetTest() {
    MavenProjectsManager.getInstance(myProject).importingSettings.excludeTargetFolder = false

    def classA = createProjectSubFile("target/classes/A.class")
    def testClass = createProjectSubFile("target/test-classes/ATest.class")

    def a = createProjectSubFile("target/a.txt")
    def aaa = createProjectSubFile("target/aaa/a.txt")

    importProject """
<groupId>test</groupId>
<artifactId>project</artifactId>
<version>1</version>
"""

    def fileIndex = ProjectRootManager.getInstance(myProject).fileIndex

    assert !fileIndex.isInContent(classA)
    assert !fileIndex.isInContent(testClass)
    assert fileIndex.isInContent(a)
    assert fileIndex.isInContent(aaa)
  }

  @Test
  void testDontExcludeTargetTest2() {
    MavenProjectsManager.getInstance(myProject).importingSettings.excludeTargetFolder = false

    def realClassA = createProjectSubFile("customOutput/A.class")
    def realTestClass = createProjectSubFile("customTestOutput/ATest.class")

    def classA = createProjectSubFile("target/classes/A.class")
    def testClass = createProjectSubFile("target/test-classes/ATest.class")

    def a = createProjectSubFile("target/a.txt")
    def aaa = createProjectSubFile("target/aaa/a.txt")

    importProject """
<groupId>test</groupId>
<artifactId>project</artifactId>
<version>1</version>

<build>
<outputDirectory>customOutput</outputDirectory>
<testOutputDirectory>customTestOutput</testOutputDirectory>
</build>
"""

    def fileIndex = ProjectRootManager.getInstance(myProject).fileIndex

    assert fileIndex.isInContent(classA)
    assert fileIndex.isInContent(testClass)
    assert fileIndex.isInContent(a)
    assert fileIndex.isInContent(aaa)
    assert !fileIndex.isInContent(realClassA)
    assert !fileIndex.isInContent(realTestClass)
  }

}
