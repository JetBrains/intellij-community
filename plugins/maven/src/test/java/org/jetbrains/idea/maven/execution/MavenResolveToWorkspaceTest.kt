/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.execution

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.roots.ProjectRootManager
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.artifactResolver.common.MavenModuleMap
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.setIgnoredFilesPathForNextImport
import org.jetbrains.idea.maven.project.MavenProjectsManager
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.util.Properties
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import org.junit.jupiter.api.Assertions.assertNull

abstract @TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenResolveToWorkspaceTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  @Test
  fun testIgnoredProject() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <modules>
                         <module>moduleA</module>
                         <module>moduleIgnored</module>
                         <module>moduleB</module>
                       </modules>
                       """.trimIndent())

    val moduleA = maven.createModulePom("moduleA", """
      <groupId>test</groupId>
      <artifactId>moduleA</artifactId>
      <version>1</version>
      """.trimIndent())

    val moduleIgnored = maven.createModulePom("moduleIgnored", """
      <groupId>test</groupId>
      <artifactId>moduleIgnored</artifactId>
      <version>1</version>
      """.trimIndent())

    val moduleB = maven.createModulePom("moduleB", """
      <groupId>test</groupId>
      <artifactId>moduleB</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>test</groupId>
          <artifactId>moduleA</artifactId>
          <version>1</version>
        </dependency>
        <dependency>
          <groupId>test</groupId>
          <artifactId>moduleIgnored</artifactId>
          <version>1</version>
        </dependency>
      </dependencies>
      """.trimIndent()
    )

    maven.setIgnoredFilesPathForNextImport(listOf(moduleIgnored.getPath()))

    maven.importProjectAsync()

    maven.setIgnoredFilesPathForNextImport(listOf(moduleIgnored.getPath()))

    //assertModules("project", "moduleA", "moduleB");
    WriteAction.run<RuntimeException> { ProjectRootManager.getInstance(maven.project).setProjectSdk(com.intellij.testFramework.IdeaTestUtil.getMockJdk17()) }

    val runnerParameters = MavenRunnerParameters(moduleB.getParent().getPath(), null, false, listOf("jetty:run"), emptyMap())
    runnerParameters.isResolveToWorkspace = true

    val runnerSettings = MavenRunner.getInstance(maven.project).settings.clone()
    runnerSettings.setJreName(MavenRunnerSettings.USE_INTERNAL_JAVA)

    val parameters = MavenExternalParameters.createJavaParameters(maven.project,
                                                                  runnerParameters,
                                                                  MavenProjectsManager.getInstance(maven.project).getGeneralSettings(),
                                                                  runnerSettings,
                                                                  null)

    var resolveMapFile: String? = null

    val prefix = "-D" + MavenModuleMap.PATHS_FILE_PROPERTY + "="

    for (param in parameters.vmParametersList.parameters) {
      if (param.startsWith(prefix)) {
        resolveMapFile = param.substring(prefix.length)
        break
      }
    }

    assertNotNull(resolveMapFile)

    val properties = readProperties(resolveMapFile)

    assertEquals(moduleA.getPath(), properties.getProperty("test:moduleA:pom:1"))
    assert(properties.getProperty("test:moduleA:jar:1").endsWith("/moduleA/target/classes"))
    assertNull(properties.getProperty("test:moduleIgnored:pom:1"))
    assertNull(properties.getProperty("test:moduleIgnored:jar:1"))
  }

  companion object {
    private fun readProperties(filePath: String?): Properties {
      BufferedInputStream(FileInputStream(filePath)).use { `is` ->
        val properties = Properties()
        properties.load(`is`)

        for (entry in properties.entries) {
          val value = entry.value as String
          entry.setValue(value.replace('\\', '/'))
        }
        return properties
      }
    }
  }
}
