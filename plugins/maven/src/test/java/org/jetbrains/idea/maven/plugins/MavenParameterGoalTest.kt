// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.plugins

import com.intellij.testFramework.UsefulTestCase.assertContainsElements
import com.intellij.testFramework.javaCodeInsightFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.jetbrains.idea.maven.server.MavenServerManager
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test

@TestApplication
class MavenParameterGoalTest {
  companion object {
    private val tempDir = tempPathFixture()
    private val project = projectFixture(tempDir, openAfterCreation = true)

    @Suppress("unused") // required by codeInsightFixture
    private val module by project.moduleFixture(tempDir, addPathToSourceRoot = true)

    @JvmStatic
    @AfterAll
    fun afterAll() {
      MavenServerManager.getInstance().closeAllConnectorsAndWait()
    }
  }

  private val fixture by javaCodeInsightFixture(project, tempDir)

  @Test
  fun testCompletion() {
    fixture.configureByText("pom.xml", """
      <?xml version="1.0" encoding="UTF-8"?>
      <project xmlns="http://maven.apache.org/POM/4.0.0"
               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
               xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
        <modelVersion>4.0.0</modelVersion>

        <groupId>simpleMaven</groupId>
        <artifactId>simpleMaven</artifactId>
        <version>1.0</version>

        <build>
          <plugins>
            <plugin>
              <groupId>org.apache.maven.plugins</groupId>
              <artifactId>maven-changelog-plugin</artifactId>
              <configuration>
                <goal><caret></goal>
              </configuration>
            </plugin>
          </plugins>
        </build>

      </project>
      """.trimIndent())

    fixture.completeBasic()

    assertContainsElements(fixture.lookupElementStrings!!, "clean", "compile", "package")
  }
}
