// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.openapi.util.registry.Registry
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class MavenShadePluginConfiguratorTest : MavenMultiVersionImportingTestCase() {
  @Test
  fun `test maven shade plugin uber jar dependency`() = runBlocking {
    assumeTrue(isWorkspaceImport)

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())

    createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>test</groupId>
          <artifactId>m2</artifactId>
          <version>1</version>
        </dependency>
      </dependencies>
      """.trimIndent())

    val m2 = createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      <build>
        <plugins>
          <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-shade-plugin</artifactId>
          </plugin>                  
        </plugins>    
      </build>  
      """.trimIndent())

    importProjectAsync()

    val uberJarPath = m2.parent.path + "/target/m2-1.jar"
    assertModuleLibDep("m1", "Maven Shade: test:m2:1",
                       "jar://$uberJarPath!/")

    Registry.get("maven.shade.plugin.generate.uber.jar").setValue("true", testRootDisposable)
    updateAllProjects()
    assertTrue(Files.exists(Path.of(uberJarPath)));
  }
}