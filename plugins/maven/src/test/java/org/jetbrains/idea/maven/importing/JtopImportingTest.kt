// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import kotlinx.coroutines.runBlocking
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.maven.fixtures.MavenVersionArguments
import org.jetbrains.idea.maven.fixtures.assertModuleModuleDeps
import org.jetbrains.idea.maven.fixtures.assertModules
import org.jetbrains.idea.maven.fixtures.createModulePom
import org.jetbrains.idea.maven.fixtures.createProjectPom
import org.jetbrains.idea.maven.fixtures.importProjectAsync
import org.jetbrains.idea.maven.fixtures.mavenImportingFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import com.intellij.testFramework.UsefulTestCase.assertEmpty

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class JtopImportingTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
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