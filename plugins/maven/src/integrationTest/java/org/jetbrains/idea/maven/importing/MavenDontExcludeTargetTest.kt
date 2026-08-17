// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.createProjectSubFile
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenDontExcludeTargetTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  
  fun testDontExcludeTargetTest() = runBlocking {
    MavenProjectsManager.getInstance(maven.project).importingSettings.isExcludeTargetFolder = false

    val classA = maven.createProjectSubFile("target/classes/A.class")
    val testClass = maven.createProjectSubFile("target/test-classes/ATest.class")

    val a = maven.createProjectSubFile("target/a.txt")
    val aaa = maven.createProjectSubFile("target/aaa/a.txt")

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    val fileIndex = ProjectRootManager.getInstance(maven.project).getFileIndex()

    withContext(Dispatchers.EDT) {
      assert(!fileIndex.isInContent(classA))
      assert(!fileIndex.isInContent(testClass))
      assert(fileIndex.isInContent(a))
      assert(fileIndex.isInContent(aaa))
    }
  }

  @Test
  fun testDontExcludeTargetTest2() = runBlocking {
    MavenProjectsManager.getInstance(maven.project).importingSettings.isExcludeTargetFolder = false

    val realClassA = maven.createProjectSubFile("customOutput/A.class")
    val realTestClass = maven.createProjectSubFile("customTestOutput/ATest.class")

    val classA = maven.createProjectSubFile("target/classes/A.class")
    val testClass = maven.createProjectSubFile("target/test-classes/ATest.class")

    val a = maven.createProjectSubFile("target/a.txt")
    val aaa = maven.createProjectSubFile("target/aaa/a.txt")

    maven.importProjectAsync(
      """
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>

        <build>
        <outputDirectory>customOutput</outputDirectory>
        <testOutputDirectory>customTestOutput</testOutputDirectory>
        </build>
        """.trimIndent())

    val fileIndex = ProjectRootManager.getInstance(maven.project).getFileIndex()

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
