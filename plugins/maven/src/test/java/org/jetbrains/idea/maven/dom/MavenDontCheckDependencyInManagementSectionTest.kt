package org.jetbrains.idea.maven.dom

import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.fixtures.MavenVersionArguments
import org.jetbrains.idea.maven.fixtures.checkHighlighting
import org.jetbrains.idea.maven.fixtures.createProjectPom
import org.jetbrains.idea.maven.fixtures.importProjectAsync
import org.jetbrains.idea.maven.fixtures.mavenDomFixture
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenDontCheckDependencyInManagementSectionTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenDomFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  @Test
  fun testHighlighting() = runBlocking {
    maven.importProjectAsync(
      """
        <groupId>test</groupId>
        <artifactId>m1</artifactId>
        <version>1</version>

          <dependencies>
            <dependency>
              <groupId>xxxx</groupId>
              <artifactId>yyyy</artifactId>
              <version>zzzz</version>
            </dependency>
          </dependencies>

          <dependencyManagement>
            <dependencies>
              <dependency>
                <groupId>xxxx</groupId>
                <artifactId>yyyy</artifactId>
                <version>zzzz</version>
              </dependency>
            </dependencies>
          </dependencyManagement>

          <build>
            <plugins>
              <plugin>
                <groupId>xxxx</groupId>
                <artifactId>yyyy</artifactId>
                <version>zzzz</version>
              </plugin>
            </plugins>

            <pluginManagement>
              <plugins>
                <plugin>
                  <groupId>xxxx</groupId>
                  <artifactId>yyyy</artifactId>
                  <version>zzzz</version>
                </plugin>
              </plugins>
            </pluginManagement>
          </build>
        """.trimIndent())

    maven.createProjectPom(
      """
        <groupId>test</groupId>
        <artifactId>m1</artifactId>
        <version>1</version>

          <dependencies>
            <dependency>
              <groupId><error descr="Dependency 'xxxx:yyyy:zzzz' not found">xxxx</error></groupId>
              <artifactId><error descr="Dependency 'xxxx:yyyy:zzzz' not found">yyyy</error></artifactId>
              <version><error descr="Dependency 'xxxx:yyyy:zzzz' not found">zzzz</error></version>
            </dependency>
          </dependencies>

          <dependencyManagement>
            <dependencies>
              <dependency>
                <groupId>xxxx</groupId>
                <artifactId>yyyy</artifactId>
                <version>zzzz</version>
              </dependency>
            </dependencies>
          </dependencyManagement>

          <build>
            <plugins>
              <plugin>
                <groupId><error descr="Plugin 'xxxx:yyyy:zzzz' not found">xxxx</error></groupId>
                <artifactId><error descr="Plugin 'xxxx:yyyy:zzzz' not found">yyyy</error></artifactId>
                <version><error descr="Plugin 'xxxx:yyyy:zzzz' not found">zzzz</error></version>
              </plugin>
            </plugins>

            <pluginManagement>
              <plugins>
                <plugin>
                  <groupId>xxxx</groupId>
                  <artifactId>yyyy</artifactId>
                  <version>zzzz</version>
                </plugin>
              </plugins>
            </pluginManagement>
          </build>
        """.trimIndent())

    maven.checkHighlighting()
  }
}
