// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.toolchains

import com.intellij.testFramework.UsefulTestCase.assertSameElements
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.createProjectSubFile
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource


@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class ToolchainRequirementReaderTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  

  @BeforeEach
  fun setUp() {
    maven.createProjectSubFile(".mvn/maven.config",
                         "-t\n" +
                         ".mvn/my_toolchains.xml")
    maven.createProjectSubFile(".mvn/my_toolchains.xml", "<toolchains/>")
  }

  @Test
  fun testReadToolchainFromSelectJdkToolchain() = runBlocking {
    val finder = ToolchainFinder()

    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>test</artifactId>
      <version>1</version>
      <build>
        <plugins>
          <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-toolchains-plugin</artifactId>
            <executions>
              <execution>
                <goals>
                  <goal>select-jdk-toolchain</goal>
                </goals>
                <configuration>
                  <version>99</version>
                  <someParam>blablabla</someParam>
                </configuration>
              </execution>
            </executions>
          </plugin>
          <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <executions>
              <execution>
                <id>test-execution</id>
              </execution>
            </executions>
          </plugin>
        </plugins>
      </build>
""")

    val mavenProject = maven.projectsManager.rootProjects[0]!!

    val expectedRequirement = ToolchainRequirement.Builder(ToolchainRequirement.JDK_TYPE)
      .set("version", "99")
      .set("someParam", "blablabla")
      .build()

    val allToolchainRequirements = finder.allToolchainRequirements(mavenProject)
    assertSameElements(allToolchainRequirements, expectedRequirement)
    assertEquals(expectedRequirement, finder.searchToolchainRequirementForMain(mavenProject), "Main toolchain does not match")
    assertEquals(expectedRequirement, finder.searchToolchainRequirementForTest(mavenProject), "Test toolchain does not match")
    assertEquals(expectedRequirement,
                 finder.searchToolchainRequirementForExecution(mavenProject, "test-execution"),
                 "Compiler execution toolchain does not match")
  }

  @Test
  fun testReadToolchainFromToolchainGoal() = runBlocking {
    val finder = ToolchainFinder()

    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>test</artifactId>
      <version>1</version>
      <build>
        <plugins>
          <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-toolchains-plugin</artifactId>
              <executions>
                <execution>
                  <goals>
                      <goal>toolchain</goal>
                  </goals>
                </execution>
            </executions>
              <configuration>
                <toolchains>
                  <jdk>
                    <version>99</version>
                     <someParam>blablabla</someParam>
                  </jdk>
                </toolchains>
              </configuration>
          </plugin>
          <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <executions>
              <execution>
                <id>test-execution</id>
              </execution>
            </executions>  
          </plugin>
        </plugins>
      </build>
""")

    val mavenProject = maven.projectsManager.rootProjects[0]!!

    val expectedRequirement = ToolchainRequirement.Builder(ToolchainRequirement.JDK_TYPE)
      .set("version", "99")
      .set("someParam", "blablabla")
      .build()

    val allToolchainRequirements = finder.allToolchainRequirements(mavenProject)
    assertSameElements(allToolchainRequirements, expectedRequirement)
    assertEquals(expectedRequirement, finder.searchToolchainRequirementForMain(mavenProject), "Main toolchain does not match")
    assertEquals(expectedRequirement, finder.searchToolchainRequirementForTest(mavenProject), "Test toolchain does not match")
    assertEquals(expectedRequirement,
                 finder.searchToolchainRequirementForExecution(mavenProject, "test-execution"),
                 "Compiler execution toolchain does not match")
  }

  @Test
  fun testReadToolchainFromToolchainExecutionShouldHavePriority() = runBlocking {
    val finder = ToolchainFinder()

    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>test</artifactId>
      <version>1</version>
      <build>
        <plugins>
          <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-toolchains-plugin</artifactId>
              <executions>
                <execution>
                  <goals>
                      <goal>toolchain</goal>
                  </goals>
                </execution>
            </executions>
              <configuration>
                <toolchains>
                  <jdk>
                    <version>99</version>
                     <someParam>blablabla</someParam>
                  </jdk>
                </toolchains>
              </configuration>
          </plugin>
          <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <executions>
              <execution>
                <id>some-execution</id>
                <configuration>
                    <jdkToolchain>
                        <version>98</version>
                        <purpose>for compiler execution</purpose>
                    </jdkToolchain>
                </configuration>
              </execution>
            </executions>
          </plugin>
        </plugins>
      </build>
""")

    val mavenProject = maven.projectsManager.rootProjects[0]!!

    val expectedRequirement = ToolchainRequirement.Builder(ToolchainRequirement.JDK_TYPE)
      .set("version", "99")
      .set("someParam", "blablabla")
      .build()


    val compilerRequirement = ToolchainRequirement.Builder(ToolchainRequirement.JDK_TYPE)
      .set("version", "98")
      .set("purpose", "for compiler execution")
      .build()

    val allToolchainRequirements = finder.allToolchainRequirements(mavenProject)
    assertSameElements(allToolchainRequirements, expectedRequirement, compilerRequirement)
    assertEquals(expectedRequirement, finder.searchToolchainRequirementForMain(mavenProject), "Main toolchain does not match")
    assertEquals(expectedRequirement, finder.searchToolchainRequirementForTest(mavenProject), "Test toolchain does not match")
    assertEquals(compilerRequirement,
                 finder.searchToolchainRequirementForExecution(mavenProject, "some-execution"),
                 "Compiler execution toolchain does not match")
  }

}

fun toolchainRequirement(version: String): ToolchainRequirement {
  return ToolchainRequirement.Builder(ToolchainRequirement.JDK_TYPE).set("version", version).build()
}