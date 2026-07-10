// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.compiler

import com.intellij.maven.testFramework.fixtures.MavenDomTestFixtureIndices
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertModules
import com.intellij.maven.testFramework.fixtures.assumeVersionAtLeast
import com.intellij.maven.testFramework.fixtures.createProjectSubFile
import com.intellij.maven.testFramework.fixtures.getSourceLanguageLevelForModule
import com.intellij.maven.testFramework.fixtures.getTargetLanguageLevelForModule
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenDomFixture
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.fixtures.checkHighlighting
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MultiReleaseHighlightingTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenDomFixture(
    mavenVersion = mavenVersion, modelVersion = modelVersion,
    indices = MavenDomTestFixtureIndices("local1", listOf("local2")),
  )

  @Test
  fun testDoNotHighlightVersionRanges() = runBlocking {
    maven.assumeVersionAtLeast("3.9.0")
    maven.createProjectSubFile("src/main/java/org/example/A.java", """
      package org.example;
      class A {}
      """.trimIndent())
    val moduleInfo = maven.createProjectSubFile("src/main/java-additional/module-info.java", """
      module project {
        exports org.example;
      }""".trimIndent())

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
      """.trimIndent())

    maven.assertModules("project", "project.main", "project.additionalSourceRoot", "project.test")

    assertEquals(LanguageLevel.JDK_1_8, maven.getSourceLanguageLevelForModule("project.main"))
    assertEquals(LanguageLevel.JDK_1_8, maven.getTargetLanguageLevelForModule("project.main"))

    assertEquals(LanguageLevel.JDK_1_9, maven.getSourceLanguageLevelForModule("project.additionalSourceRoot"))
    assertEquals(LanguageLevel.JDK_1_9, maven.getTargetLanguageLevelForModule("project.additionalSourceRoot"))

    maven.checkHighlighting(moduleInfo)
  }
}
