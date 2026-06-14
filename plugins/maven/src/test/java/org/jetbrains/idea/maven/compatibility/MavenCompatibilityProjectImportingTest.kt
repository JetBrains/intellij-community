// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.compatibility

import com.intellij.maven.testFramework.fixtures.MavenCustomRepositoryHelper
import com.intellij.maven.testFramework.fixtures.assertModuleLibDep
import com.intellij.maven.testFramework.fixtures.assertModules
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.createProjectSubFile
import com.intellij.maven.testFramework.fixtures.doImportProjectsAsync
import com.intellij.maven.testFramework.fixtures.getModule
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenGeneralSettings
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.mn
import com.intellij.maven.testFramework.fixtures.projectRoot
import com.intellij.maven.testFramework.fixtures.projectsTree
import com.intellij.maven.testFramework.fixtures.refreshFiles
import com.intellij.openapi.module.LanguageLevelUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.text.VersionComparatorUtil
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.model.MavenProjectProblem
import org.jetbrains.idea.maven.server.MavenServerManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import org.junit.jupiter.params.support.ParameterDeclarations
import java.util.stream.Stream

private val MAVEN_VERSIONS = listOf(
  "4.0.0-rc-5",
  "3.9.16",
  "3.8.8",
  "3.6.3",
  "3.5.4",
  "3.2.5",
  "3.1.1",
)

internal class MavenCompatibilityVersions : ArgumentsProvider {
  override fun provideArguments(parameters: ParameterDeclarations?, context: ExtensionContext?): Stream<out Arguments?> {
    return MAVEN_VERSIONS.map { Arguments.of(it) }.stream()
  }
}

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenCompatibilityVersions::class)
class MavenCompatibilityProjectImportingTest(private val myMavenVersion: String) {
  private val maven by mavenImportingFixture(mavenVersion = myMavenVersion, skipPluginResolution = false)

  @BeforeEach
  fun before() {
    val helper = MavenCustomRepositoryHelper(maven.dir, "local1")
    maven.repositoryPath = helper.getTestData("local1")
  }

  private fun assumeVersionMoreThan(version: String) {
    assumeTrue(VersionComparatorUtil.compare(myMavenVersion, version) > 0, "Version should be more than $version")
  }

  private fun assumeVersionAtLeast(version: String) {
    assumeTrue(VersionComparatorUtil.compare(myMavenVersion, version) >= 0, "Version should be at least $version")
  }


  private fun assumeVersionLessOrEqualsThan(version: String) {
    assumeTrue(VersionComparatorUtil.compare(myMavenVersion, version) >= 0, "Version should be less than $version")
  }

  private fun assumeVersionNot(version: String) {
    assumeTrue(VersionComparatorUtil.compare(myMavenVersion, version) != 0, "Version $version skipped")
  }

  @Test
  fun testExceptionsFromMavenExtensionsAreReportedAsProblems() = runBlocking {
    assumeVersionAtLeast("3.1.0")
    val helper = MavenCustomRepositoryHelper(maven.dir, "plugins")
    maven.repositoryPath = helper.getTestData("plugins")
    maven.mavenGeneralSettings.isWorkOffline = true

    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <description>throw!</description>
                       <build>
                         <extensions>
                           <extension>
                             <groupId>intellij.test</groupId>
                             <artifactId>maven-extension</artifactId>
                             <version>1.0</version>
                           </extension>
                         </extensions>
                       </build>
                       """.trimIndent())
    maven.doImportProjectsAsync(listOf(maven.projectPom), false)

    val projects = maven.projectsTree.projects
    assertEquals(1, projects.size)
    val mavenProject = projects[0]
    val extensionProblems = mavenProject.problems.filter { "throw!" == it.description }
    assertEquals(1, extensionProblems.size, extensionProblems.toString())
    val problem = extensionProblems[0]
    assertEquals(MavenProjectProblem.ProblemType.STRUCTURE, problem.type, problem.toString())
    val otherProblems = mavenProject.problems.filter { it !== problem }
    assertTrue(otherProblems.all {
                 it.type == MavenProjectProblem.ProblemType.DEPENDENCY && it.description!!.startsWith("Unresolved plugin")
               }, otherProblems.toString())
  }

  @Test
  fun testSmokeImport() = runBlocking {
    assertCorrectVersion()

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())


    maven.assertModules("project")
  }

  @Test
  fun testSmokeImportWithUnknownExtension() = runBlocking {
    assertCorrectVersion()
    maven.createProjectSubFile(".mvn/extensions.xml", """
      <extensions>
        <extension>
          <groupId>org.example</groupId>
          <artifactId>some-never-existed-extension</artifactId>
          <version>1</version>
        </extension>
      </extensions>
      """.trimIndent())
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())

    maven.createModulePom("m1", """
                       <parent>
                         <groupId>test</groupId>
                         <artifactId>project</artifactId>
                         <version>1</version>
                       </parent>
                       <artifactId>m1</artifactId>
                         """.trimIndent())

    maven.createModulePom("m2", """
                       <parent>
                         <groupId>test</groupId>
                         <artifactId>project</artifactId>
                         <version>1</version>
                       </parent>
                       <artifactId>m2</artifactId>
                         """.trimIndent())

    maven.importProjectAsync()

    maven.assertModules("m2", "m1", "project")
  }


  private suspend fun assertCorrectVersion() {
    assertEquals(myMavenVersion, MavenServerManager.getInstance().getConnector(maven.project, maven.projectRoot.path).mavenDistribution.version)
  }

  @Test
  fun testInterpolateModel() = runBlocking {
    assertCorrectVersion()

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <properties>
                        <junitVersion>4.0</junitVersion>
                      </properties>
                    <dependencies>
                      <dependency>
                        <groupId>junit</groupId>
                        <artifactId>junit</artifactId>
                        <version>${'$'}{junitVersion}</version>
                      </dependency>
                    </dependencies>
                    """.trimIndent())

    maven.assertModules("project")

    maven.assertModuleLibDep("project", "Maven: junit:junit:4.0")
  }

  @Test
  fun testImportProjectProperties() = runBlocking {
    assumeVersionMoreThan("3.0.3")

    assertCorrectVersion()

    maven.createModulePom("module1", """
      <parent>
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      </parent>
      <artifactId>module1</artifactId>
      <dependencies>
        <dependency>
          <groupId>junit</groupId>
          <artifactId>junit</artifactId>
          <version>${'$'}{junitVersion}</version>
        </dependency>
      </dependencies>
      """.trimIndent()
    )

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <properties>
                        <junitVersion>4.0</junitVersion>
                      </properties>
                    <modules>
                    <module>module1</module>
                    </modules>
                    """.trimIndent())

    maven.assertModules("project", maven.mn("project", "module1"))

    maven.assertModuleLibDep(maven.mn("project", "module1"), "Maven: junit:junit:4.0")
  }

  @Test
  fun testImportAddedProjectProperties() = runBlocking {
    assumeVersionMoreThan("3.0.3")
    assumeVersionNot("3.6.0")

    assertCorrectVersion()

    maven.createModulePom("module1", """
      <parent>
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      </parent>
      <artifactId>module1</artifactId>
      <dependencies>
        <dependency>
          <groupId>org.example</groupId>
          <artifactId>intellijmaventest</artifactId>
          <version>${'$'}{libVersion}</version>
        </dependency>
      </dependencies>
      """.trimIndent()
    )

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <properties>
                        <libVersion>1.0</libVersion>
                      </properties>
                    <modules>
                    <module>module1</module>
                    </modules>
                    """.trimIndent())

    maven.assertModules("project", maven.mn("project", "module1"))

    maven.assertModuleLibDep(maven.mn("project", "module1"), "Maven: org.example:intellijmaventest:1.0")

    val module1 = maven.createModulePom("module1", """
      <parent>
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      </parent>
      <artifactId>module1</artifactId>
      <dependencies>
        <dependency>
          <groupId>org.example</groupId>
          <artifactId>intellijmaventest</artifactId>
          <version>${'$'}{libVersion2}</version>
        </dependency>
      </dependencies>
      """.trimIndent()
    )
    maven.refreshFiles(listOf(module1))

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <properties>
                        <libVersion>1.0</libVersion>
                        <libVersion2>2.0</libVersion2>
                      </properties>
                    <modules>
                    <module>module1</module>
                    </modules>
                    """.trimIndent())
    maven.assertModuleLibDep(maven.mn("project", "module1"), "Maven: org.example:intellijmaventest:2.0")
  }

  @Test
  fun testImportSubProjectWithPropertyInParent() = runBlocking {
    assumeVersionMoreThan("3.0.3")

    assertCorrectVersion()

    maven.createModulePom("module1", """
      <parent>
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>${'$'}{revision}</version>
      </parent>
      <artifactId>module1</artifactId>
      """.trimIndent())

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                        <artifactId>project</artifactId>
                        <version>${'$'}{revision}</version>
                        <packaging>pom</packaging>
                        <modules>
                            <module>module1</module>
                        </modules>
                        <properties>
                            <revision>1.0-SNAPSHOT</revision>
                        </properties>
                    """.trimIndent())

    maven.assertModules("project", maven.mn("project", "module1"))
  }

  @Test
  fun testLanguageLevelWhenSourceLanguageLevelIsNotSpecified() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.apache.maven.plugins</groupId>
                          <artifactId>maven-compiler-plugin</artifactId>
                          <configuration>
                          </configuration>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())
    maven.assertModules("project")
    val expectedVersion = if (isMaven4) {
      if (VersionComparatorUtil.compare(myMavenVersion, "4.0.0-alpha-7") >= 0) LanguageLevel.JDK_1_8
      else LanguageLevel.JDK_1_7
    }
    else {
      if (VersionComparatorUtil.compare(myMavenVersion, "3.9.3") >= 0) {
        LanguageLevel.JDK_1_8
      }
      else if (VersionComparatorUtil.compare(myMavenVersion, "3.9.0") >= 0) {
        LanguageLevel.JDK_1_7
      }
      else {
        LanguageLevel.JDK_1_5
      }
    }
    assertEquals(expectedVersion, LanguageLevelUtil.getCustomLanguageLevel(maven.getModule("project")))
  }

  private val isMaven4: Boolean
    get() = StringUtil.compareVersionNumbers(myMavenVersion, "4.0") >= 0
}
