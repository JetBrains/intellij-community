// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom

import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import com.intellij.maven.testFramework.fixtures.MavenDomTestFixture
import com.intellij.maven.testFramework.fixtures.MavenDomTestFixtureIndices
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import org.jetbrains.idea.maven.fixtures.assertCompletionVariants
import org.jetbrains.idea.maven.fixtures.checkHighlighting
import com.intellij.maven.testFramework.fixtures.configureProjectPom
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.createProjectSubFile
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenDomFixture
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenSurefirePluginTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenDomFixture(
    mavenVersion = mavenVersion, modelVersion = modelVersion,
    initialPom = MavenDomTestFixture.DEFAULT_POM,
    skipPluginResolution = false,
    indices = MavenDomTestFixtureIndices("plugins", listOf("local1")),
  )

  @Test
  fun testCompletion() = runBlocking {
    maven.configureProjectPom(
      """
          <groupId>simpleMaven</groupId>
          <artifactId>simpleMaven</artifactId>
          <packaging>jar</packaging>
          <version>1.0</version>

          <build>
            <plugins>
              <plugin>
                <artifactId>maven-surefire-plugin</artifactId>
                <configuration>
                  <additionalClasspathElements>
                    <additionalClasspathElement>${'$'}{basedir}/src/<caret></additionalClasspathElement>
                  </additionalClasspathElements>
                </configuration>
              </plugin>
            </plugins>
          </build>
        """.trimIndent())
    maven.importProjectAsync()

    maven.createProjectSubFile("src/main/A.txt", "")
    maven.createProjectSubFile("src/test/A.txt", "")
    maven.createProjectSubFile("src/A.txt", "")

    maven.assertCompletionVariants(maven.projectPom, "main", "test")
  }

  @Test
  fun testCompletionSurefireProperties() = runBlocking {
    maven.configureProjectPom(
      """
          <groupId>simpleMaven</groupId>
          <artifactId>simpleMaven</artifactId>
          <version>1.0</version>

          <build>
            <plugins>
              <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <configuration>
                  <additionalClasspathElements>
                    <additionalClasspathElement>${'$'}{surefire.<caret>}</additionalClasspathElement>
                  </additionalClasspathElements>
                </configuration>
              </plugin>
            </plugins>
          </build>
        """.trimIndent())
    maven.importProjectAsync()

    maven.assertCompletionVariants(maven.projectPom, "surefire.forkNumber", "surefire.threadNumber")
  }

  @Test
  fun testCompletionSurefirePropertiesOutsideConfiguration() = runBlocking {
    maven.configureProjectPom(
      """
          <groupId>simpleMaven</groupId>
          <artifactId>simpleMaven</artifactId>
          <version>1.0</version>

          <properties>
            <aaa>${'$'}{surefire.<caret>}</aaa>
          </properties>

          <build>
            <plugins>
              <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <configuration>
                </configuration>
              </plugin>
            </plugins>
          </build>
        """.trimIndent())
    maven.importProjectAsync()

    maven.assertCompletionVariants(maven.projectPom)
  }

  @Test
  fun testSurefirePropertiesHighlighting() = runBlocking {
    maven.importProjectAsync(
      """
          <groupId>simpleMaven</groupId>
          <artifactId>simpleMaven</artifactId>
          <version>1.0</version>

            <properties>
            <aaa>${'$'}{surefire.forkNumber}</aaa>
          </properties>

          <build>
            <plugins>
              <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.4.0</version>
                <configuration>
                  <additionalClasspathElements>
                    <additionalClasspathElement>${'$'}{surefire.forkNumber}</additionalClasspathElement>
                  </additionalClasspathElements>
                </configuration>

                <executions>
                  <execution>
                    <goals>
                      <goal>test</goal>
                      <goal>${'$'}{surefire.threadNumber}</goal>
                    </goals>
                    <configuration>
                      <debugForkedProcess>${'$'}{surefire.threadNumber}</debugForkedProcess>
                    </configuration>
                  </execution>
                </executions>
              </plugin>
            </plugins>
          </build>
        """.trimIndent())

    maven.createProjectPom(
      """
          <groupId>simpleMaven</groupId>
          <artifactId>simpleMaven</artifactId>
          <version>1.0</version>

            <properties>
            <aaa>${'$'}{<error descr="Cannot resolve symbol 'surefire.forkNumber'">surefire.forkNumber</error>}</aaa>
          </properties>

          <build>
            <plugins>
              <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <configuration>
                  <additionalClasspathElements>
                    <additionalClasspathElement>${'$'}{surefire.forkNumber}</additionalClasspathElement>
                  </additionalClasspathElements>
                </configuration>

                <executions>
                  <execution>
                    <goals>
                      <goal>test</goal>
                      <goal>${'$'}{<error descr="Cannot resolve symbol 'surefire.threadNumber'">surefire.threadNumber</error>}</goal>
                    </goals>
                    <configuration>
                      <debugForkedProcess>${'$'}{surefire.threadNumber}</debugForkedProcess>
                    </configuration>
                  </execution>
                </executions>

              </plugin>
            </plugins>
          </build>
        """.trimIndent())

    maven.checkHighlighting()
  }
}
