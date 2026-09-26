// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertModuleModuleDeps
import com.intellij.maven.testFramework.fixtures.assertModules
import com.intellij.maven.testFramework.fixtures.assumeMaven3
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.importProjectsAsync
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenWorkspaceImportingTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  

  @Test
  fun testImportingProjectWithDynamicWorkspace() = runBlocking {
    maven.assumeMaven3()
    val firstRootWithChild = maven.createModulePom("parentone",
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
    val child = maven.createModulePom("parentone/child",
                                $$"""
                             <parent>
                               <groupId>test</groupId>
                               <artifactId>parentone</artifactId>
                               <version>${version_param}</version>                 
                             </parent>
                              <artifactId>child</artifactId>
                             """.trimIndent())
    val secondRoot = maven.createModulePom("second",
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

    maven.importProjectsAsync(firstRootWithChild, secondRoot)
    maven.assertModules("parentone", "child", "second")
    maven.assertModuleModuleDeps("second", "child")
  }
}
