// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.toolchains

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.openapi.util.registry.Registry
import kotlinx.coroutines.runBlocking
import org.junit.Test


internal class ToolchainRequirementReaderTest : MavenMultiVersionImportingTestCase() {

  override fun setUp() {
    super.setUp()
    createProjectSubFile(".mvn/maven.config",
                         "-t\n" +
                         ".mvn/my_toolchains.xml")
    createProjectSubFile(".mvn/my_toolchains.xml", "<toolchains/>")
  }

  @Test
  fun testReadToolchainFromSelectJdkToolchain() = runBlocking {
    val finder = ToolchainFinder()

    importProjectAsync("""
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

    val mavenProject = projectsManager.rootProjects[0]!!

    val expectedRequirement = ToolchainRequirement.Builder(ToolchainRequirement.JDK_TYPE)
      .set("version", "99")
      .set("someParam", "blablabla")
      .build()

    val allToolchainRequirements = finder.allToolchainRequirements(mavenProject)
    assertSameElements(allToolchainRequirements, expectedRequirement)
    assertEquals("Main toolchain does not match", expectedRequirement, finder.searchToolchainRequirementForMain(mavenProject))
    assertEquals("Test toolchain does not match", expectedRequirement, finder.searchToolchainRequirementForTest(mavenProject))
    assertEquals("Compiler execution toolchain does not match",
                 expectedRequirement,
                 finder.searchToolchainRequirementForExecution(mavenProject, "test-execution"))
  }

  @Test
  fun testReadToolchainFromToolchainGoal() = runBlocking {
    val finder = ToolchainFinder()
    //Registry.get("maven.server.debug").setValue("true")

    importProjectAsync("""
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

    val mavenProject = projectsManager.rootProjects[0]!!

    val expectedRequirement = ToolchainRequirement.Builder(ToolchainRequirement.JDK_TYPE)
      .set("version", "99")
      .set("someParam", "blablabla")
      .build()

    val allToolchainRequirements = finder.allToolchainRequirements(mavenProject)
    assertSameElements(allToolchainRequirements, expectedRequirement)
    assertEquals("Main toolchain does not match", expectedRequirement, finder.searchToolchainRequirementForMain(mavenProject))
    assertEquals("Test toolchain does not match", expectedRequirement, finder.searchToolchainRequirementForTest(mavenProject))
    assertEquals("Compiler execution toolchain does not match",
                 expectedRequirement,
                 finder.searchToolchainRequirementForExecution(mavenProject, "test-execution"))
  }

  @Test
  fun testReadToolchainFromToolchainExecutionShouldHavePriority() = runBlocking {
    val finder = ToolchainFinder()

    importProjectAsync("""
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

    val mavenProject = projectsManager.rootProjects[0]!!

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
    assertEquals("Main toolchain does not match", expectedRequirement, finder.searchToolchainRequirementForMain(mavenProject))
    assertEquals("Test toolchain does not match", expectedRequirement, finder.searchToolchainRequirementForTest(mavenProject))
    assertEquals("Compiler execution toolchain does not match",
                 compilerRequirement,
                 finder.searchToolchainRequirementForExecution(mavenProject, "some-execution"))
  }

}

fun toolchainRequirement(version: String): ToolchainRequirement {
  return ToolchainRequirement.Builder(ToolchainRequirement.JDK_TYPE).set("version", version).build()
}