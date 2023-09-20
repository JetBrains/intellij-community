package org.jetbrains.idea.maven.dom

import com.intellij.maven.testFramework.MavenDomTestCase
import kotlinx.coroutines.runBlocking
import org.junit.Test

class MavenDontCheckDependencyInManagementSectionTest : MavenDomTestCase() {
  @Test
  fun testHighlighting() = runBlocking {
    importProjectAsync(
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

    createProjectPom(
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

    checkHighlighting()
  }
}
