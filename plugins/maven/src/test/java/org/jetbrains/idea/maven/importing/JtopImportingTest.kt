// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import kotlinx.coroutines.runBlocking
import org.junit.Test

class JtopImportingTest : MavenMultiVersionImportingTestCase() {

  @Test
  fun testJtop() = runBlocking {
    createProjectPom("""
                       <groupId>de.bmarwell.jtop</groupId>
                       <artifactId>jtop</artifactId>
                       <version>1.0-SNAPSHOT</version>
                       <packaging>pom</packaging>
                       <modules>
                          <module>app</module>
                          <module>library</module>
                        </modules>
                       """.trimIndent())

    createModulePom("library", """
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

    createModulePom("library/api", """
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

    val appFile = createModulePom("app", """
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

    importProjectAsync()
    assertModules("jtop-library", "jtop", "jtop-app", "jtop-library-api")
    val app = projectsManager.findProject(appFile)
    assertEmpty(app!!.problems)
    assertModuleModuleDeps("jtop-app", "jtop-library-api")
  }

}