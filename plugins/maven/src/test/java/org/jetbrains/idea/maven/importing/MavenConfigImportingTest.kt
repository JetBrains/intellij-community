// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assumeMaven3
import com.intellij.maven.testFramework.fixtures.assumeVersionMoreThan
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createProjectSubFile
import com.intellij.maven.testFramework.fixtures.getModule
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenDomFixture
import com.intellij.maven.testFramework.fixtures.mn
import com.intellij.maven.testFramework.fixtures.updateAllProjects
import com.intellij.maven.testFramework.fixtures.updateProjectPom
import com.intellij.openapi.application.edtWriteAction
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.dom.references.MavenPsiElementWrapper
import org.jetbrains.idea.maven.fixtures.assertCompletionVariants
import org.jetbrains.idea.maven.fixtures.resolveReference
import org.jetbrains.idea.maven.model.MavenConstants
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import java.nio.charset.StandardCharsets

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenConfigImportingTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenDomFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
    
  @Test
  fun testResolveJvmConfigProperty() = runBlocking {
    maven.createProjectSubFile(MavenConstants.JVM_CONFIG_RELATIVE_PATH, "-Dver=1")
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>${'$'}{ver}</version>
                    """.trimIndent())

    val mavenProject = maven.projectsManager.findProject(maven.getModule("project"))
    assertEquals("1", mavenProject!!.mavenId.version)
  }

  @Test
  fun testResolveMavenConfigProperty() = runBlocking {
    maven.createProjectSubFile(MavenConstants.MAVEN_CONFIG_RELATIVE_PATH, "-Dver=1")
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>${'$'}{ver}</version>
                    """.trimIndent())

    val mavenProject = maven.projectsManager.findProject(maven.getModule("project"))
    assertEquals("1", mavenProject!!.mavenId.version)
  }

  @Test
  fun testResolvePropertyPriority() = runBlocking {
    maven.createProjectSubFile(MavenConstants.JVM_CONFIG_RELATIVE_PATH, "-Dver=ignore")
    maven.createProjectSubFile(MavenConstants.MAVEN_CONFIG_RELATIVE_PATH, "-Dver=1")
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>${'$'}{ver}</version>
                    <properties>
                      <ver>ignore</ver></properties>
                      """.trimIndent())

    val mavenProject = maven.projectsManager.findProject(maven.getModule("project"))
    assertEquals("1", mavenProject!!.mavenId.version)
  }


  @Test
  fun testResolveConfigPropertiesInModules() = runBlocking {
    maven.assumeMaven3()
    maven.assumeVersionMoreThan("3.3.1")
    maven.createProjectSubFile(MavenConstants.MAVEN_CONFIG_RELATIVE_PATH, """
      -Dver=1
      -DmoduleName=m1""".trimIndent())

    maven.createModulePom("m1", """
      <artifactId>${'$'}{moduleName}</artifactId>
      <version>${'$'}{ver}</version>
      <parent>
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>${'$'}{ver}</version>
      </parent>
      """.trimIndent())

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>${'$'}{ver}</version>
                    <packaging>pom</packaging>
                    <modules>
                      <module>${'$'}{moduleName}</module></modules>
                      """.trimIndent())

    val mavenProject = maven.projectsManager.findProject(maven.getModule("project"))
    assertEquals("1", mavenProject!!.mavenId.version)

    val module = maven.projectsManager.findProject(maven.getModule(maven.mn("project", "m1")))
    assertNotNull(module)

    assertEquals("m1", module!!.mavenId.artifactId)
    assertEquals("1", module.mavenId.version)
  }

  @Test
  fun testMavenConfigCompletion() = runBlocking {
    maven.createProjectSubFile(MavenConstants.MAVEN_CONFIG_RELATIVE_PATH, "-Dconfig.version=1")
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>${'$'}{config.<caret></version>
                       """.trimIndent())

    maven.assertCompletionVariants(maven.projectPom, "config.version")
  }

  @Test
  fun testMavenConfigReferenceResolving() = runBlocking {
    maven.createProjectSubFile(MavenConstants.MAVEN_CONFIG_RELATIVE_PATH, "-Dconfig.version=1")
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>${'$'}{config.version}</version>
                    """.trimIndent())

    val resolvedReference = maven.resolveReference(maven.projectPom, "config.version", 0)
    assertNotNull(resolvedReference)

    UsefulTestCase.assertInstanceOf(resolvedReference, MavenPsiElementWrapper::class.java)
    assertEquals("1", (resolvedReference as MavenPsiElementWrapper?)!!.name)
  }

  @Test
  fun testReimportOnConfigChange() = runBlocking {
    val configFile = maven.createProjectSubFile(MavenConstants.MAVEN_CONFIG_RELATIVE_PATH, "-Dver=1")
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>${'$'}{ver}</version>
                    """.trimIndent())

    var mavenProject = maven.projectsManager.findProject(maven.getModule("project"))
    assertEquals("1", mavenProject!!.mavenId.version)

    edtWriteAction {
      val content = "-Dver=2".toByteArray(StandardCharsets.UTF_8)
      configFile.setBinaryContent(content, -1, configFile.getTimeStamp() + 1)
    }
    maven.updateAllProjects()

    mavenProject = maven.projectsManager.findProject(maven.getModule("project"))
    assertEquals("2", mavenProject!!.mavenId.version)
  }
}
