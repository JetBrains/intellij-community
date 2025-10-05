// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.compiler

import com.intellij.pom.java.LanguageLevel
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.dom.MavenDomWithIndicesTestCase
import org.junit.Test

class MultiReleaseHighlightingTest : MavenDomWithIndicesTestCase() {
  @Test
  fun testDoNotHighlightVersionRanges() = runBlocking {
    assumeVersionAtLeast("3.9.0")
    createProjectSubFile("src/main/java/org/example/A.java", """
      package org.example;
      class A {}
      """.trimIndent())
    val moduleInfo = createProjectSubFile("src/main/java-additional/module-info.java", """
      module project {
        exports org.example;
      }""".trimIndent())

    importProjectAsync("""
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

    assertModules("project", "project.main", "project.additionalSourceRoot", "project.test")

    assertEquals(LanguageLevel.JDK_1_8, getSourceLanguageLevelForModule("project.main"))
    assertEquals(LanguageLevel.JDK_1_8, getTargetLanguageLevelForModule("project.main"))

    assertEquals(LanguageLevel.JDK_1_9, getSourceLanguageLevelForModule("project.additionalSourceRoot"))
    assertEquals(LanguageLevel.JDK_1_9, getTargetLanguageLevelForModule("project.additionalSourceRoot"))

    checkHighlighting(moduleInfo)
  }
}