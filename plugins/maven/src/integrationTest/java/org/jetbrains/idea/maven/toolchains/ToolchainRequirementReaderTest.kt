// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.toolchains

import com.intellij.maven.testFramework.fixtures.assertUnorderedElementsAreEqual
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.createProjectSubFile
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.moduleTag
import com.intellij.maven.testFramework.fixtures.modulesTag
import com.intellij.maven.testFramework.fixtures.projectsTree
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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
                  <runtimeName>test runtime</runtimeName>
                  <runtimeVersion>99.0.1</runtimeVersion>
                  <env>JAVA_HOME,TEST_HOME</env>
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
      .set("runtime.name", "test runtime")
      .set("runtime.version", "99.0.1")
      .set("env", "JAVA_HOME,TEST_HOME")
      .useImporterJdkIfMatches(true)
      .discoverJdks(true)
      .build()

    val allToolchainRequirements = finder.allToolchainRequirements(mavenProject)
    assertUnorderedElementsAreEqual(allToolchainRequirements, expectedRequirement)
    assertEquals(expectedRequirement, finder.searchToolchainRequirementForMain(mavenProject), "Main toolchain does not match")
    assertEquals(expectedRequirement, finder.searchToolchainRequirementForTest(mavenProject), "Test toolchain does not match")
    assertEquals(expectedRequirement,
                 finder.searchToolchainRequirementForExecution(mavenProject, "test-execution"),
                 "Compiler execution toolchain does not match")
  }

  @Test
  fun testIgnoreUnsupportedParametersFromSelectJdkToolchain() = runBlocking {
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
                  <useJdk>Never</useJdk>
                  <discoverToolchains>true</discoverToolchains>
                  <comparator>lts,current,env,version,vendor</comparator>
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
      .discoverJdks(true)
      .build()

    assertUnorderedElementsAreEqual(finder.allToolchainRequirements(mavenProject), expectedRequirement)
    assertEquals(expectedRequirement, finder.searchToolchainRequirementForMain(mavenProject), "Main toolchain does not match")
    assertEquals(expectedRequirement, finder.searchToolchainRequirementForTest(mavenProject), "Test toolchain does not match")
  }

  @Test
  fun testReadSelectJdkToolchainRequirementFromPluginConfiguration() = runBlocking {
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
            <configuration>
              <version>99</version>
              <useJdk>Never</useJdk>
            </configuration>
            <executions>
              <execution>
                <goals>
                  <goal>select-jdk-toolchain</goal>
                </goals>
              </execution>
            </executions>
          </plugin>
        </plugins>
      </build>
""")

    val mavenProject = maven.projectsManager.rootProjects[0]!!

    val expectedRequirement = ToolchainRequirement.Builder(ToolchainRequirement.JDK_TYPE)
      .set("version", "99")
      .discoverJdks(true)
      .build()

    assertUnorderedElementsAreEqual(finder.allToolchainRequirements(mavenProject), expectedRequirement)
    assertEquals(expectedRequirement, finder.searchToolchainRequirementForMain(mavenProject), "Main toolchain does not match")
    assertEquals(expectedRequirement, finder.searchToolchainRequirementForTest(mavenProject), "Test toolchain does not match")
  }

  @Test
  fun testEmptySelectJdkToolchainParameterFallsBackToUserProperty() = runBlocking {
    val finder = ToolchainFinder()

    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>test</artifactId>
      <version>1</version>
      <properties>
        <toolchain.jdk.version>[99,100)</toolchain.jdk.version>
      </properties>
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
                  <version></version>
                </configuration>
              </execution>
            </executions>
          </plugin>
        </plugins>
      </build>
""")

    val mavenProject = maven.projectsManager.rootProjects[0]!!

    val expectedRequirement = ToolchainRequirement.Builder(ToolchainRequirement.JDK_TYPE)
      .set("version", "[99,100)")
      .useImporterJdkIfMatches(true)
      .discoverJdks(true)
      .build()

    assertUnorderedElementsAreEqual(finder.allToolchainRequirements(mavenProject), expectedRequirement)
    assertEquals(expectedRequirement, finder.searchToolchainRequirementForMain(mavenProject), "Main toolchain does not match")
    assertEquals(expectedRequirement, finder.searchToolchainRequirementForTest(mavenProject), "Test toolchain does not match")
  }

  @Test
  fun testIgnoreToolchainGoalConfigurationForSelectJdkToolchain() = runBlocking {
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
            <configuration>
              <toolchains>
                <jdk>
                  <version>99</version>
                </jdk>
              </toolchains>
            </configuration>
            <executions>
              <execution>
                <goals>
                  <goal>select-jdk-toolchain</goal>
                </goals>
              </execution>
            </executions>
          </plugin>
        </plugins>
      </build>
""")

    val mavenProject = maven.projectsManager.rootProjects[0]!!

    assertTrue(finder.allToolchainRequirements(mavenProject).isEmpty())
    assertNull(finder.searchToolchainRequirementForMain(mavenProject), "Main toolchain does not match")
    assertNull(finder.searchToolchainRequirementForTest(mavenProject), "Test toolchain does not match")
  }

  @Test
  fun testReadSelectJdkToolchainRequirementFromUserProperties() = runBlocking {
    val finder = ToolchainFinder()

    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>test</artifactId>
      <version>1</version>
      <properties>
        <toolchain.jdk.version>[99,100)</toolchain.jdk.version>
        <toolchain.jdk.vendor>test vendor</toolchain.jdk.vendor>
        <toolchain.jdk.runtime.name>test runtime</toolchain.jdk.runtime.name>
        <toolchain.jdk.runtime.version>99.0.1</toolchain.jdk.runtime.version>
        <toolchain.jdk.env>JAVA_HOME,TEST_HOME</toolchain.jdk.env>
      </properties>
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
              </execution>
            </executions>
          </plugin>
        </plugins>
      </build>
""")

    val mavenProject = maven.projectsManager.rootProjects[0]!!

    val expectedRequirement = ToolchainRequirement.Builder(ToolchainRequirement.JDK_TYPE)
      .set("version", "[99,100)")
      .set("vendor", "test vendor")
      .set("runtime.name", "test runtime")
      .set("runtime.version", "99.0.1")
      .set("env", "JAVA_HOME,TEST_HOME")
      .useImporterJdkIfMatches(true)
      .discoverJdks(true)
      .build()

    assertUnorderedElementsAreEqual(finder.allToolchainRequirements(mavenProject), expectedRequirement)
    assertEquals(expectedRequirement, finder.searchToolchainRequirementForMain(mavenProject), "Main toolchain does not match")
    assertEquals(expectedRequirement, finder.searchToolchainRequirementForTest(mavenProject), "Test toolchain does not match")
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
    assertUnorderedElementsAreEqual(allToolchainRequirements, expectedRequirement)
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
    assertUnorderedElementsAreEqual(allToolchainRequirements, expectedRequirement, compilerRequirement)
    assertEquals(expectedRequirement, finder.searchToolchainRequirementForMain(mavenProject), "Main toolchain does not match")
    assertEquals(expectedRequirement, finder.searchToolchainRequirementForTest(mavenProject), "Test toolchain does not match")
    assertEquals(compilerRequirement,
                 finder.searchToolchainRequirementForExecution(mavenProject, "some-execution"),
                 "Compiler execution toolchain does not match")
  }

  /** IDEA-394354: a child POM disables the inherited execution, so the child needs no toolchain. */
  @Test
  fun testIgnoreSelectJdkToolchainExecutionDisabledByPhase() = runBlocking {
    val finder = ToolchainFinder()

    maven.createProjectPom("""
      <groupId>test</groupId>
      <artifactId>parent</artifactId>
      <version>1</version>
      <packaging>pom</packaging>
      <${maven.modulesTag}>
        <${maven.moduleTag}>none-phase</${maven.moduleTag}>
        <${maven.moduleTag}>empty-phase</${maven.moduleTag}>
        <${maven.moduleTag}>uppercase-none-phase</${maven.moduleTag}>
      </${maven.modulesTag}>
      <build>
        <plugins>
          <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-toolchains-plugin</artifactId>
            <executions>
              <execution>
                <id>select-jdk</id>
                <goals>
                  <goal>select-jdk-toolchain</goal>
                </goals>
                <configuration>
                  <version>99</version>
                </configuration>
              </execution>
            </executions>
          </plugin>
        </plugins>
      </build>
""")

    val nonePhasePom = maven.createModulePom("none-phase", """
      <artifactId>none-phase</artifactId>
      <parent>
        <groupId>test</groupId>
        <artifactId>parent</artifactId>
        <version>1</version>
      </parent>
      <build>
        <plugins>
          <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-toolchains-plugin</artifactId>
            <executions>
              <execution>
                <id>select-jdk</id>
                <phase>none</phase>
              </execution>
            </executions>
          </plugin>
        </plugins>
      </build>
""")

    val emptyPhasePom = maven.createModulePom("empty-phase", """
      <artifactId>empty-phase</artifactId>
      <parent>
        <groupId>test</groupId>
        <artifactId>parent</artifactId>
        <version>1</version>
      </parent>
      <build>
        <plugins>
          <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-toolchains-plugin</artifactId>
            <executions>
              <execution>
                <id>select-jdk</id>
                <phase/>
              </execution>
            </executions>
          </plugin>
        </plugins>
      </build>
""")

    val uppercaseNonePhasePom = maven.createModulePom("uppercase-none-phase", """
      <artifactId>uppercase-none-phase</artifactId>
      <parent>
        <groupId>test</groupId>
        <artifactId>parent</artifactId>
        <version>1</version>
      </parent>
      <build>
        <plugins>
          <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-toolchains-plugin</artifactId>
            <executions>
              <execution>
                <id>select-jdk</id>
                <phase>NONE</phase>
              </execution>
            </executions>
          </plugin>
        </plugins>
      </build>
""")

    maven.importProjectAsync()

    val expectedRequirement = ToolchainRequirement.Builder(ToolchainRequirement.JDK_TYPE)
      .set("version", "99")
      .useImporterJdkIfMatches(true)
      .discoverJdks(true)
      .build()

    val parentProject = maven.projectsTree.findProject(maven.projectPom)!!
    assertUnorderedElementsAreEqual(finder.allToolchainRequirements(parentProject), expectedRequirement)

    for (pom in listOf(nonePhasePom, emptyPhasePom, uppercaseNonePhasePom)) {
      val childProject = maven.projectsTree.findProject(pom)!!
      val name = childProject.mavenId.artifactId
      assertEquals(emptySet<ToolchainRequirement>(),
                   finder.allToolchainRequirements(childProject),
                   "$name keeps a toolchain requirement")
      assertNull(finder.searchToolchainRequirementForMain(childProject), "$name keeps a main toolchain")
      assertNull(finder.searchToolchainRequirementForTest(childProject), "$name keeps a test toolchain")
    }
  }

}

fun toolchainRequirement(version: String): ToolchainRequirement {
  return ToolchainRequirement.Builder(ToolchainRequirement.JDK_TYPE).set("version", version).build()
}
