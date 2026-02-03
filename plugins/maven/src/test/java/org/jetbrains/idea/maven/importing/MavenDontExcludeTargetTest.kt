// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.roots.ProjectRootManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.junit.Test

class MavenDontExcludeTargetTest : MavenMultiVersionImportingTestCase() {
  
  fun testDontExcludeTargetTest() = runBlocking {
    MavenProjectsManager.getInstance(project).importingSettings.isExcludeTargetFolder = false

    val classA = createProjectSubFile("target/classes/A.class")
    val testClass = createProjectSubFile("target/test-classes/ATest.class")

    val a = createProjectSubFile("target/a.txt")
    val aaa = createProjectSubFile("target/aaa/a.txt")

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    val fileIndex = ProjectRootManager.getInstance(project).getFileIndex()

    withContext(Dispatchers.EDT) {
      assert(!fileIndex.isInContent(classA))
      assert(!fileIndex.isInContent(testClass))
      assert(fileIndex.isInContent(a))
      assert(fileIndex.isInContent(aaa))
    }
  }

  @Test
  fun testDontExcludeTargetTest2() = runBlocking {
    MavenProjectsManager.getInstance(project).importingSettings.isExcludeTargetFolder = false

    val realClassA = createProjectSubFile("customOutput/A.class")
    val realTestClass = createProjectSubFile("customTestOutput/ATest.class")

    val classA = createProjectSubFile("target/classes/A.class")
    val testClass = createProjectSubFile("target/test-classes/ATest.class")

    val a = createProjectSubFile("target/a.txt")
    val aaa = createProjectSubFile("target/aaa/a.txt")

    importProjectAsync(
      """
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>

        <build>
        <outputDirectory>customOutput</outputDirectory>
        <testOutputDirectory>customTestOutput</testOutputDirectory>
        </build>
        """.trimIndent())

    val fileIndex = ProjectRootManager.getInstance(project).getFileIndex()

    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        assert(fileIndex.isInContent(classA))
        assert(fileIndex.isInContent(testClass))
        assert(fileIndex.isInContent(a))
        assert(fileIndex.isInContent(aaa))
        assert(!fileIndex.isInContent(realClassA))
        assert(!fileIndex.isInContent(realTestClass))
      }
    }
  }
}
