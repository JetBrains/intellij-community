// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import kotlinx.coroutines.runBlocking
import org.junit.Test

class MavenWorkspaceImportingTest : MavenMultiVersionImportingTestCase() {

  @Test
  fun testImportingProjectWithDynamicWorkspace() = runBlocking {
    assumeMaven3()
    val firstRootWithChild = createModulePom("parentone",
                                             $$"""
                             <groupId>test</groupId>
                             <artifactId>parentone</artifactId>
                             <version>${version_param}</version>
                             <properties>
                                 <version_param>1.0</version_param>
                             </properties>
                             <packaging>pom</packaging>
                             <modules>
                                 <module>child</module>
                             </modules>
                             """.trimIndent())
    val child = createModulePom("parentone/child",
                                $$"""
                             <parent>
                               <groupId>test</groupId>
                               <artifactId>parentone</artifactId>
                               <version>${version_param}</version>                 
                             </parent>
                              <artifactId>child</artifactId>
                             """.trimIndent())
    val secondRoot = createModulePom("second",
                                     """
                             <groupId>test</groupId>
                             <artifactId>second</artifactId>
                             <version>1</version>
                             <dependencies>
                                 <dependency>
                                     <groupId>test</groupId>
                                     <artifactId>child</artifactId>
                                     <version>1.0</version>
                                 </dependency>
                             </dependencies>
                             """.trimIndent())

    importProjectsAsync(firstRootWithChild, secondRoot)
    assertModules("parentone", "child", "second")
    assertModuleModuleDeps("second", "child")
  }
}
