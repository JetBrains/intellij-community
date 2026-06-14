// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertModuleModuleDeps
import com.intellij.maven.testFramework.fixtures.assertModules
import com.intellij.maven.testFramework.fixtures.createProjectSubFile
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.refreshFiles
import com.intellij.maven.testFramework.fixtures.setPomContent
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

open @TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class DependenciesSubstitutionTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  @Test
  fun `simple library substitution`() = runBlocking {
    val value = Registry.get("external.system.substitute.library.dependencies")
    try {
      value.setValue(true)
      val p1Pom = maven.createProjectSubFile("p1/pom.xml")
      maven.setPomContent(p1Pom, "<groupId>test</groupId>" +
                           "<artifactId>p1</artifactId>" +
                           "<packaging>jar</packaging>" +
                           "<version>1.0</version>")
      val p2Pom = maven.createProjectSubFile("p2/pom.xml")
      maven.setPomContent(p2Pom, "<groupId>test</groupId>" +
                           "<artifactId>p2</artifactId>" +
                           "<packaging>jar</packaging>" +
                           "<version>1</version>" +
                           "<dependencies>" +
                           "  <dependency>" +
                           "    <groupId>test</groupId>" +
                           "    <artifactId>p1</artifactId>" +
                           "    <version>1.0</version>" +
                           "  </dependency>" +
                           "</dependencies>")
      maven.refreshFiles(listOf(p1Pom, p2Pom))
      maven.importProjectAsync(p1Pom)
      maven.importProjectAsync(p2Pom)
      maven.assertModules("p1", "p2")
      maven.assertModuleModuleDeps("p2", "p1")
    }
    finally {
      value.resetToDefault()
    }
  }
}