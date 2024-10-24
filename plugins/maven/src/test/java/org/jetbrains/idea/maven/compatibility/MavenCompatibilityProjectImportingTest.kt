// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.compatibility

import com.intellij.maven.testFramework.MavenImportingTestCase
import com.intellij.maven.testFramework.MavenWrapperTestFixture
import com.intellij.openapi.module.LanguageLevelUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.pom.java.LanguageLevel
import com.intellij.util.text.VersionComparatorUtil
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.MavenCustomRepositoryHelper
import org.jetbrains.idea.maven.model.MavenProjectProblem
import org.jetbrains.idea.maven.server.MavenServerManager
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class MavenCompatibilityProjectImportingTest : MavenImportingTestCase() {
  protected var myWrapperTestFixture: MavenWrapperTestFixture? = null

  @Parameterized.Parameter
  @JvmField
  var myMavenVersion: String? = null

  override fun runInDispatchThread(): Boolean = false

  private fun assumeVersionMoreThan(version: String) {
    Assume.assumeTrue("Version should be more than $version", VersionComparatorUtil.compare(myMavenVersion, version) > 0)
  }

  private fun assumeVersionAtLeast(version: String) {
    Assume.assumeTrue("Version should be at least $version", VersionComparatorUtil.compare(myMavenVersion, version) >= 0)
  }


  private fun assumeVersionLessOrEqualsThan(version: String) {
    Assume.assumeTrue("Version should be less than $version", VersionComparatorUtil.compare(myMavenVersion, version) >= 0)
  }

  private fun assumeVersionNot(version: String) {
    Assume.assumeTrue("Version $version skipped", VersionComparatorUtil.compare(myMavenVersion, version) != 0)
  }

  @Before
  fun before() = runBlocking {
    myWrapperTestFixture = MavenWrapperTestFixture(project, myMavenVersion)
    myWrapperTestFixture!!.setUp()


    val helper = MavenCustomRepositoryHelper(dir, "local1")
    val repoPath = helper.getTestDataPath("local1")
    repositoryPath = repoPath
  }

  @After
  fun after() = runBlocking {
    myWrapperTestFixture!!.tearDown()
  }


  @Test
  fun testExceptionsFromMavenExtensionsAreReportedAsProblems() = runBlocking {
    assumeVersionAtLeast("3.1.0")
    val helper = MavenCustomRepositoryHelper(dir, "plugins")
    repositoryPath = helper.getTestDataPath("plugins")
    mavenGeneralSettings.isWorkOffline = true

    createProjectPom("""
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
    doImportProjectsAsync(listOf(projectPom), false)

    val projects = projectsTree.projects
    assertEquals(1, projects.size)
    val mavenProject = projects[0]
    val extensionProblems = mavenProject.problems.filter { "throw!" == it.description }
    assertEquals(extensionProblems.toString(), 1, extensionProblems.size)
    val problem = extensionProblems[0]
    assertEquals(problem.toString(), MavenProjectProblem.ProblemType.STRUCTURE, problem.type)
    val otherProblems = mavenProject.problems.filter { it !== problem }
    assertTrue(otherProblems.toString(),
               otherProblems.all {
                 it.type == MavenProjectProblem.ProblemType.DEPENDENCY && it.description!!.startsWith("Unresolved plugin")
               })
  }

  @Test
  fun testSmokeImport() = runBlocking {
    assertCorrectVersion()

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())


    assertModules("project")
  }

  @Test
  fun testSmokeImportWithUnknownExtension() = runBlocking {
    assertCorrectVersion()
    createProjectSubFile(".mvn/extensions.xml", """
      <extensions>
        <extension>
          <groupId>org.example</groupId>
          <artifactId>some-never-existed-extension</artifactId>
          <version>1</version>
        </extension>
      </extensions>
      """.trimIndent())
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())

    createModulePom("m1", """
                       <parent>
                         <groupId>test</groupId>
                         <artifactId>project</artifactId>
                         <version>1</version>
                       </parent>
                       <artifactId>m1</artifactId>
                         """.trimIndent())

    createModulePom("m2", """
                       <parent>
                         <groupId>test</groupId>
                         <artifactId>project</artifactId>
                         <version>1</version>
                       </parent>
                       <artifactId>m2</artifactId>
                         """.trimIndent())

    importProjectAsync()

    assertModules("m2", "m1", "project")
  }


  private suspend fun assertCorrectVersion() {
    assertEquals(myMavenVersion, MavenServerManager.getInstance().getConnector(project, projectRoot.path).mavenDistribution.version)
  }

  @Test
  fun testInterpolateModel() = runBlocking {
    assertCorrectVersion()

    importProjectAsync("""
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

    assertModules("project")

    assertModuleLibDep("project", "Maven: junit:junit:4.0")
  }

  @Test
  fun testImportProjectProperties() = runBlocking {
    assumeVersionMoreThan("3.0.3")

    assertCorrectVersion()

    createModulePom("module1", """
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

    importProjectAsync("""
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

    assertModules("project", mn("project", "module1"))

    assertModuleLibDep(mn("project", "module1"), "Maven: junit:junit:4.0")
  }

  @Test
  fun testImportAddedProjectProperties() = runBlocking {
    assumeVersionMoreThan("3.0.3")
    assumeVersionNot("3.6.0")

    assertCorrectVersion()

    createModulePom("module1", """
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

    importProjectAsync("""
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

    assertModules("project", mn("project", "module1"))

    assertModuleLibDep(mn("project", "module1"), "Maven: org.example:intellijmaventest:1.0")

    val module1 = createModulePom("module1", """
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
    refreshFiles(listOf(module1))

    importProjectAsync("""
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
    assertModuleLibDep(mn("project", "module1"), "Maven: org.example:intellijmaventest:2.0")
  }

  @Test
  fun testImportSubProjectWithPropertyInParent() = runBlocking {
    assumeVersionMoreThan("3.0.3")

    assertCorrectVersion()

    createModulePom("module1", """
      <parent>
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>${'$'}{revision}</version>
      </parent>
      <artifactId>module1</artifactId>
      """.trimIndent())

    importProjectAsync("""
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

    assertModules("project", mn("project", "module1"))
  }

  @Test
  fun testLanguageLevelWhenSourceLanguageLevelIsNotSpecified() = runBlocking {
    importProjectAsync("""
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
    assertModules("project")
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
    assertEquals(expectedVersion, LanguageLevelUtil.getCustomLanguageLevel(getModule("project")))
  }

  private val isMaven4: Boolean
    get() = StringUtil.compareVersionNumbers(myMavenVersion, "4.0") >= 0

  companion object {
    @JvmStatic
    @get:Parameterized.Parameters(name = "with Maven-{0}")
    val mavenVersions: List<Array<String>>
      get() = listOf(
        arrayOf("4.0.0-beta-5"),
        arrayOf("3.9.9"),
        arrayOf("3.9.8"),
        arrayOf("3.9.7"),
        arrayOf("3.9.6"),
        arrayOf("3.9.5"),
        arrayOf("3.9.4"),
        arrayOf("3.9.3"),
        arrayOf("3.9.2"),
        arrayOf("3.9.1"),
        arrayOf("3.9.0"),
        arrayOf("3.8.8"),
        arrayOf("3.8.7"),
        arrayOf("3.8.6"),
        arrayOf("3.8.5"),
        arrayOf("3.8.4"),
        arrayOf("3.8.3"),
        arrayOf("3.8.2"),
        arrayOf("3.8.1"),
        arrayOf("3.8.1"),
        arrayOf("3.6.3"),
        arrayOf("3.6.2"),
        arrayOf("3.6.1"),
        arrayOf("3.6.0"),
        arrayOf("3.5.4"),
        arrayOf("3.5.3"),
        arrayOf("3.5.2"),
        arrayOf("3.5.0"),
        arrayOf("3.3.9"),
        arrayOf("3.3.3"),
        arrayOf("3.3.1"),
        arrayOf("3.2.5"),
        arrayOf("3.2.3"),
        arrayOf("3.2.2"),
        arrayOf("3.2.1"),
        arrayOf("3.1.1"),
        arrayOf("3.1.0")
      )
  }
}
