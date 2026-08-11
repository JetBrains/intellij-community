// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertModuleLibDep
import com.intellij.maven.testFramework.fixtures.assertModuleModuleDeps
import com.intellij.maven.testFramework.fixtures.awaitConfiguration
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.testRootDisposable
import com.intellij.maven.testFramework.fixtures.updateAllProjectsFullSync
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import java.nio.file.Files
import java.nio.file.Path

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenShadePluginConfiguratorTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  @Test
  fun `test maven shade plugin uber jar dependency`() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())

    maven.createModulePom("m1", """
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

    val m2 = maven.createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      <build>
        <plugins>
          <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-shade-plugin</artifactId>
            <executions>
              <execution>
                <phase>package</phase>
                <goals>
                  <goal>shade</goal>
                </goals>
                <configuration>
                  <relocations>
                    <relocation>
                      <pattern>org.example</pattern>
                      <shadedPattern>shaded.org.example</shadedPattern>
                    </relocation>
                  </relocations>
                </configuration>
              </execution>
            </executions>
          </plugin>                  
        </plugins>    
      </build>  
      """.trimIndent())

    maven.importProjectAsync()

    val uberJarPath = m2.parent.path + "/target/m2-1.jar"
    maven.assertModuleModuleDeps("m1", "m2")
    maven.assertModuleLibDep("m1", "Maven Shade: test:m2:1",
                       "jar://$uberJarPath!/")

    Registry.get("maven.shade.plugin.generate.uber.jar").setValue("true", maven.testRootDisposable)
    // incremental sync doesn't support uber jar generation
    maven.updateAllProjectsFullSync()
    maven.awaitConfiguration()
    assertTrue(Files.exists(Path.of(uberJarPath)));
  }
}