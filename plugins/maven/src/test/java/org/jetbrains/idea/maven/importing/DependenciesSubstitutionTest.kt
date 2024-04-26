// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.openapi.util.registry.Registry
import kotlinx.coroutines.runBlocking
import org.junit.Test

open class DependenciesSubstitutionTest : MavenMultiVersionImportingTestCase() {
  @Test
  fun `simple library substitution`() = runBlocking {
    val value = Registry.get("external.system.substitute.library.dependencies")
    try {
      value.setValue(true)
      val p1Pom = createProjectSubFile("p1/pom.xml")
      setPomContent(p1Pom, "<groupId>test</groupId>" +
                           "<artifactId>p1</artifactId>" +
                           "<packaging>jar</packaging>" +
                           "<version>1.0</version>")
      val p2Pom = createProjectSubFile("p2/pom.xml")
      setPomContent(p2Pom, "<groupId>test</groupId>" +
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
      importProjectAsync(p1Pom)
      importProjectAsync(p2Pom)
      assertModules("p1", "p2")
      assertModuleModuleDeps("p2", "p1")
    }
    finally {
      value.resetToDefault()
    }
  }
}