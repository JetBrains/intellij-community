// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.MavenDomTestCase
import com.intellij.openapi.application.writeAction
import com.intellij.testFramework.UsefulTestCase
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.dom.references.MavenPsiElementWrapper
import org.jetbrains.idea.maven.model.MavenConstants
import org.junit.Test
import java.nio.charset.StandardCharsets

class MavenConfigImportingTest : MavenDomTestCase() {
    
  @Test
  fun testResolveJvmConfigProperty() = runBlocking {
    createProjectSubFile(MavenConstants.JVM_CONFIG_RELATIVE_PATH, "-Dver=1")
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>${'$'}{ver}</version>
                    """.trimIndent())

    val mavenProject = projectsManager.findProject(getModule("project"))
    assertEquals("1", mavenProject!!.mavenId.version)
  }

  @Test
  fun testResolveMavenConfigProperty() = runBlocking {
    createProjectSubFile(MavenConstants.MAVEN_CONFIG_RELATIVE_PATH, "-Dver=1")
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>${'$'}{ver}</version>
                    """.trimIndent())

    val mavenProject = projectsManager.findProject(getModule("project"))
    assertEquals("1", mavenProject!!.mavenId.version)
  }

  @Test
  fun testResolvePropertyPriority() = runBlocking {
    createProjectSubFile(MavenConstants.JVM_CONFIG_RELATIVE_PATH, "-Dver=ignore")
    createProjectSubFile(MavenConstants.MAVEN_CONFIG_RELATIVE_PATH, "-Dver=1")
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>${'$'}{ver}</version>
                    <properties>
                      <ver>ignore</ver></properties>
                      """.trimIndent())

    val mavenProject = projectsManager.findProject(getModule("project"))
    assertEquals("1", mavenProject!!.mavenId.version)
  }

  @Test
  fun testResolveConfigPropertiesInModules() = runBlocking {
    assumeVersionMoreThan("3.3.1")
    createProjectSubFile(MavenConstants.MAVEN_CONFIG_RELATIVE_PATH, "-Dver=1 -DmoduleName=m1")

    createModulePom("m1", """
      <artifactId>${'$'}{moduleName}</artifactId>
      <version>${'$'}{ver}</version>
      <parent>
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>${'$'}{ver}</version>
      </parent>
      """.trimIndent())

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>${'$'}{ver}</version>
                    <packaging>pom</packaging>
                    <modules>
                      <module>${'$'}{moduleName}</module></modules>
                      """.trimIndent())

    val mavenProject = projectsManager.findProject(getModule("project"))
    assertEquals("1", mavenProject!!.mavenId.version)

    val module = projectsManager.findProject(getModule(mn("project", "m1")))
    assertNotNull(module)

    assertEquals("m1", module!!.mavenId.artifactId)
    assertEquals("1", module.mavenId.version)
  }

  @Test
  fun testMavenConfigCompletion() = runBlocking {
    createProjectSubFile(MavenConstants.MAVEN_CONFIG_RELATIVE_PATH, "-Dconfig.version=1")
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>${'$'}{config.<caret></version>
                       """.trimIndent())

    assertCompletionVariants(projectPom, "config.version")
  }

  @Test
  fun testMavenConfigReferenceResolving() = runBlocking {
    createProjectSubFile(MavenConstants.MAVEN_CONFIG_RELATIVE_PATH, "-Dconfig.version=1")
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>${'$'}{config.version}</version>
                    """.trimIndent())

    val resolvedReference = resolveReference(projectPom, "config.version", 0)
    assertNotNull(resolvedReference)

    UsefulTestCase.assertInstanceOf(resolvedReference, MavenPsiElementWrapper::class.java)
    assertEquals("1", (resolvedReference as MavenPsiElementWrapper?)!!.name)
  }

  @Test
  fun testReimportOnConfigChange() = runBlocking {
    val configFile = createProjectSubFile(MavenConstants.MAVEN_CONFIG_RELATIVE_PATH, "-Dver=1")
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>${'$'}{ver}</version>
                    """.trimIndent())

    var mavenProject = projectsManager.findProject(getModule("project"))
    assertEquals("1", mavenProject!!.mavenId.version)

    writeAction {
      val content = "-Dver=2".toByteArray(StandardCharsets.UTF_8)
      configFile.setBinaryContent(content, -1, configFile.getTimeStamp() + 1)
    }
    updateAllProjects()

    mavenProject = projectsManager.findProject(getModule("project"))
    assertEquals("2", mavenProject!!.mavenId.version)
  }
}
