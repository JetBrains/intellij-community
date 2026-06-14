// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.compiler

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertModules
import com.intellij.maven.testFramework.fixtures.assumeVersionAtLeast
import com.intellij.maven.testFramework.fixtures.createProjectSubFile
import com.intellij.maven.testFramework.fixtures.getSourceLanguageLevelForModule
import com.intellij.maven.testFramework.fixtures.getTargetLanguageLevelForModule
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.testRootDisposable
import com.intellij.openapi.util.registry.Registry
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.fixtures.assertExists
import org.jetbrains.idea.maven.fixtures.compileModules
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MultiReleaseOutputTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  @BeforeEach
  fun setUp() {
    maven.assumeVersionAtLeast("3.9.0")
    Registry.get("maven.import.separate.main.and.test.modules.when.multiReleaseOutput").setValue("true", maven.testRootDisposable)
  }

  @Test
  fun `test basic`() = runBlocking {
    maven.createProjectSubFile("src/main/java/A.java", "class A {}")
    maven.createProjectSubFile("src/main/java-additional/B.java", "class B extends A {}")

    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <build>
        <plugins>
          <plugin>
              <groupId>org.apache.maven.plugins</groupId>
              <artifactId>maven-compiler-plugin</artifactId>
              <executions>
                <execution>
                  <id>additionalSourceRoot</id>
                  <configuration>
                    <multiReleaseOutput>true</multiReleaseOutput>
                    <compileSourceRoots>
                      <root>${'$'}{project.basedir}/src/main/java-additional</root>
                    </compileSourceRoots>
                  </configuration>
                </execution>
              </executions>
          </plugin>
        </plugins>
      </build>
      """.trimIndent()
    )

    maven.assertModules("project", "project.main", "project.additionalSourceRoot", "project.test")
    maven.compileModules("project", "project.main", "project.additionalSourceRoot", "project.test")

    maven.assertExists("target/classes/A.class")
    maven.assertExists("target/classes/B.class")
  }

  @Test
  fun `test language level`() = runBlocking {
    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <properties>
        <maven.compiler.release>8</maven.compiler.release>  
      </properties>
      <build>
        <plugins>
          <plugin>
              <groupId>org.apache.maven.plugins</groupId>
              <artifactId>maven-compiler-plugin</artifactId>
              <executions>
                <execution>
                  <id>additionalSourceRoot</id>
                  <configuration>
                    <release>9</release>
                    <multiReleaseOutput>true</multiReleaseOutput>
                    <compileSourceRoots>
                      <root>${'$'}{project.basedir}/src/main/java-additional</root>
                    </compileSourceRoots>
                  </configuration>
                </execution>
              </executions>
          </plugin>
        </plugins>
      </build>
      """.trimIndent()
    )

    maven.assertModules("project", "project.main", "project.additionalSourceRoot", "project.test")

    assertEquals(LanguageLevel.JDK_1_8, maven.getSourceLanguageLevelForModule("project.main"))
    assertEquals(LanguageLevel.JDK_1_8, maven.getTargetLanguageLevelForModule("project.main"))

    assertEquals(LanguageLevel.JDK_1_9, maven.getSourceLanguageLevelForModule("project.additionalSourceRoot"))
    assertEquals(LanguageLevel.JDK_1_9, maven.getTargetLanguageLevelForModule("project.additionalSourceRoot"))
  }


/*
 todo: commented until IDEA-367746 is fixed

  @Test
  fun `test java module compilation`() = runBlocking {
    createProjectSubFile("src/main/java/module-info.java", """
      module project {
        requires static jakarta.servlet;
      }""".trimIndent())
    createProjectSubFile("src/main/java/A.java", "class A {}")
    createProjectSubFile("src/main/java-additional/B.java", "class B extends A {}")

    importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <properties>
        <maven.compiler.release>9</maven.compiler.release>
      </properties>
      <build>
        <plugins>
          <plugin>
              <groupId>org.apache.maven.plugins</groupId>
              <artifactId>maven-compiler-plugin</artifactId>
              <executions>
                <execution>
                  <id>additionalSourceRoot</id>
                  <configuration>
                    <release>17</release>
                    <multiReleaseOutput>true</multiReleaseOutput>
                    <compileSourceRoots>
                      <root>${'$'}{project.basedir}/src/main/java-additional</root>
                    </compileSourceRoots>
                    <compilerArgs>
                      <arg>--patch-module</arg>
                      <arg>project=${'$'}{project.basedir}/src/main/java-additional</arg>
                    </compilerArgs>
                  </configuration>
                </execution>
              </executions>
          </plugin>
        </plugins>
      </build>
      <dependencies>
        <dependency>
          <groupId>jakarta.servlet</groupId>
          <artifactId>jakarta.servlet-api</artifactId>
          <version>5.0.0</version>
        </dependency>
      </dependencies>
      """.trimIndent()
    )

    assertModules("project", "project.main", "project.additionalSourceRoot", "project.test")
    compileModules("project", "project.main", "project.additionalSourceRoot", "project.test")

    assertExists("target/classes/A.class")
    assertExists("target/classes/B.class")
  }
*/
}