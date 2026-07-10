// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertModuleModuleDeps
import com.intellij.maven.testFramework.fixtures.assertModules
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.testFramework.UsefulTestCase.assertEmpty
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class JtopImportingTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion,
    skipPluginResolution = false,
  )
  

  @Test
  fun testJtop() = runBlocking {
    maven.createProjectPom("""
                       <groupId>de.bmarwell.jtop</groupId>
                       <artifactId>jtop</artifactId>
                       <version>1.0-SNAPSHOT</version>
                       <packaging>pom</packaging>
                       <modules>
                          <module>app</module>
                          <module>library</module>
                        </modules>
                       """.trimIndent())

    maven.createModulePom("library", """
      <parent>
        <groupId>de.bmarwell.jtop</groupId>
        <artifactId>jtop</artifactId>
        <version>1.0-SNAPSHOT</version>
      </parent>

      <groupId>de.bmarwell.jtop</groupId>
      <artifactId>jtop-library</artifactId>
      <version>1.0-SNAPSHOT</version>
      <packaging>pom</packaging>
      <name>jtop :: library</name>

      <modules>
        <module>api</module>
      </modules>
""")

    maven.createModulePom("library/api", """
        <parent>
    <groupId>de.bmarwell.jtop</groupId>
    <artifactId>jtop-library</artifactId>
    <version>1.0-SNAPSHOT</version>
  </parent>

  <groupId>de.bmarwell.jtop</groupId>
  <artifactId>jtop-library-api</artifactId>
  <version>1.0-SNAPSHOT</version>
  <name>jtop :: library :: api</name>
""")

    val appFile = maven.createModulePom("app", """
        <parent>
    <groupId>de.bmarwell.jtop</groupId>
    <artifactId>jtop</artifactId>
    <version>1.0-SNAPSHOT</version>
  </parent>

  <groupId>de.bmarwell.jtop</groupId>
  <artifactId>jtop-app</artifactId>
  <version>1.0-SNAPSHOT</version>
  <name>jtop :: app</name>
  
   <dependencies>
    <dependency>
      <groupId>de.bmarwell.jtop</groupId>
      <artifactId>jtop-library-api</artifactId>
      <version>1.0-SNAPSHOT</version>
    </dependency>
    </dependencies>
""")

    maven.importProjectAsync()
    maven.assertModules("jtop-library", "jtop", "jtop-app", "jtop-library-api")
    val app = maven.projectsManager.findProject(appFile)
    assertEmpty(app!!.problems)
    maven.assertModuleModuleDeps("jtop-app", "jtop-library-api")
  }

}