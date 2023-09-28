/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.openapi.application.WriteAction
import com.intellij.testFramework.UsefulTestCase
import kotlinx.coroutines.runBlocking
import org.intellij.lang.annotations.Language
import org.jetbrains.idea.maven.MavenCustomRepositoryHelper
import org.jetbrains.idea.maven.project.MavenProject
import org.junit.Test
import java.io.IOException

class InvalidProjectImportingTest : MavenMultiVersionImportingTestCase() {
  override fun runInDispatchThread() = false

  @Test
  fun testResetDependenciesWhenProjectContainsErrors() = runBlocking {
    //Registry.get("maven.server.debug").setValue(true);
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>jar</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                       </modules>
                       """.trimIndent())

    createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>somegroup</groupId>
          <artifactId>artifact</artifactId>
          <version>1.0</version>
        </dependency>
      </dependencies>
      """.trimIndent())

    importProjectWithErrors()
    assertModules("project", "m1")
    assertModuleLibDeps("m1", "Maven: somegroup:artifact:1.0")


    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>jar</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                       </modules>
                       """.trimIndent())
    createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>somegroup</groupId>
          <artifactId>artifact</artifactId>
          <version>2.0</version>
        </dependency>
      </dependencies>
      """.trimIndent())

    importProjectWithErrors()
    assertModules("project", "m1")
    assertModuleLibDeps("m1", "Maven: somegroup:artifact:2.0")
  }

  @Test
  fun testShouldNotResetDependenciesWhenProjectContainsUnrecoverableErrors() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>jar</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                       </modules>
                       """.trimIndent())

    createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>somegroup</groupId>
          <artifactId>artifact</artifactId>
          <version>1.0</version>
        </dependency>
      </dependencies>
      """.trimIndent())

    importProjectWithErrors()
    assertModules("project", "m1")
    assertModuleLibDeps("m1", "Maven: somegroup:artifact:1.0")


    createProjectPom("""
                       <groupId>test</groupId>
                       <packaging>jar</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                       </modules>
                       """.trimIndent())
    createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
        <dependency>
          <groupId>somegroup</groupId>
          <artifactId>artifact</artifactId>
          <version>2.0  </dependency>
      </dependencies>
      """.trimIndent())

    importProjectWithErrors()
    assertModules("project", "m1")
    assertModuleLibDeps("m1", "Maven: somegroup:artifact:1.0")
  }

  @Test
  @Throws(IOException::class)
  fun testUnknownProblemWithEmptyFile() {
    createProjectPom("")
    WriteAction.runAndWait<IOException> { myProjectPom.setBinaryContent(ByteArray(0)) }

    importProjectWithErrors()

    assertModules("project")

    val root = rootProjects[0]
    assertProblems(root, "'pom.xml' has syntax errors")
  }

  @Test
  fun testUndefinedPropertyInHeader() = runBlocking {
    importProjectWithErrors("""
                              <groupId>test</groupId>
                              <artifactId>${'$'}{undefined}</artifactId>
                              <version>1</version>
                              """.trimIndent())

    assertModules("project")
    val root = rootProjects[0]
    val problem = if (isMaven4
    ) "'artifactId' with value '\${undefined}' does not match a valid coordinate id pattern."
    else "'artifactId' with value '\${undefined}' does not match a valid id pattern."
    assertProblems(root, problem)
  }

  @Test
  fun testRecursiveInterpolation() = runBlocking {
    importProjectWithErrors("""
                              <groupId>test</groupId>
                              <artifactId>project</artifactId>
                              <version>${'$'}{version}</version>
                              <dependencies>
                                <dependency>
                                  <groupId>group</groupId>
                                  <artifactId>artifact</artifactId>
                                  <version>1</version>
                                 </dependency>
                              </dependencies>
                              """.trimIndent())

    assertModules("project")

    val root = rootProjects[0]
    val problems = root.getProblems()
    assertFalse(problems.isEmpty())
    assertModuleLibDeps("project", "Maven: group:artifact:1")
  }

  @Test
  fun testUnresolvedParent() = runBlocking {
    importProjectWithErrors("""
                              <groupId>test</groupId>
                              <artifactId>project</artifactId>
                              <version>1</version>
                              <parent>
                                <groupId>test</groupId>
                                <artifactId>parent</artifactId>
                                <version>1</version>
                              </parent>
                              """.trimIndent())

    assertModules("project")

    val root = rootProjects[0]
    val problems = root.getProblems()
    UsefulTestCase.assertSize(1, problems)
    assertTrue(problems[0]!!.description!!.contains("Could not find artifact test:parent:pom:1"))
  }

  @Test
  fun testUnresolvedParentForInvalidProject() = runBlocking {
    // not of the 'pom' type
    importProjectWithErrors("""
                              <groupId>test</groupId>
                              <artifactId>project</artifactId>
                              <version>1</version>
                              <parent>
                                <groupId>test</groupId>
                                <artifactId>parent</artifactId>
                                <version>1</version>
                              </parent>
                              <modules>
                                <module>foo</module>
                              </modules>
                              """.trimIndent())

    val root = rootProjects[0]
    val problems = root.getProblems()
    UsefulTestCase.assertSize(2, problems)
    assertTrue(problems[0]!!.description, problems[0]!!.description!!.contains("Could not find artifact test:parent:pom:1"))
    assertTrue(problems[1]!!.description, problems[1]!!.description == "Module 'foo' not found")
  }

  @Test
  @Throws(IOException::class)
  fun testMissingModules() {
    importProjectWithErrors("""
                              <groupId>test</groupId>
                              <artifactId>project</artifactId>
                              <version>1</version>
                              <packaging>pom</packaging>
                              <modules>
                                <module>foo</module>
                              </modules>
                              """.trimIndent())
    resolvePlugins()

    assertModules("project")

    val root = rootProjects[0]
    assertProblems(root, "Module 'foo' not found")
  }

  @Test
  fun testInvalidProjectModel() = runBlocking {
    // invalid packaging
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>jar</packaging>
                       <modules>
                         <module>foo</module>
                       </modules>
                       """.trimIndent())

    createModulePom("foo", """
      <groupId>test</groupId>
      <artifactId>foo</artifactId>
      <version>1</version>
      """.trimIndent())
    importProjectWithErrors()
    assertModules("project", "foo")

    val root = rootProjects[0]
    assertProblems(root, "'packaging' with value 'jar' is invalid. Aggregator projects require 'pom' as packaging.")
  }

  @Test
  fun testInvalidModuleModel() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>foo</module>
                       </modules>
                       """.trimIndent())

    createModulePom("foo", """
      <groupId>test</groupId>
      <artifactId>foo</artifactId>
      <version>1
      """.trimIndent()) //  invalid tag

    importProjectWithErrors()
    //resolvePlugins();
    assertModules("project", "foo")

    val root = rootProjects[0]
    assertProblems(root)

    assertProblems(getModules(root)[0], "'pom.xml' has syntax errors")
  }

  @Test
  fun testSeveratInvalidModulesAndWithSameName() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>foo</module>
                         <module>bar1</module>
                         <module>bar2</module>
                         <module>bar3</module>
                       </modules>
                       """.trimIndent())

    createModulePom("foo", """
      <groupId>test</groupId>
      <artifactId>foo</artifactId>
      <version>1
      """.trimIndent()) //  invalid tag

    createModulePom("bar1", """
      <groupId>test</groupId>
      <artifactId>bar</artifactId>
      <version>1
      """.trimIndent()) //  invalid tag

    createModulePom("bar2", """
      <groupId>test</groupId>
      <artifactId>bar</artifactId>
      <version>1
      """.trimIndent()) //  invalid tag

    createModulePom("bar3", """
      <groupId>org.test</groupId>
      <artifactId>bar</artifactId>
      <version>1
      """.trimIndent()) //  invalid tag

    importProjectWithErrors()
    assertModules("project", "foo", "bar (1)", "bar (2)", "bar (3) (org.test)")
  }

  @Test
  fun testInvalidProjectWithModules() = runBlocking {
    // invalid tag
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1<modules>
                         <module>foo</module>
                       </modules>
                       """.trimIndent())

    createModulePom("foo", """
      <groupId>test</groupId>
      <artifactId>foo</artifactId>
      <version>1</version>
      """.trimIndent())

    importProjectWithErrors()

    assertModules("project", "foo")
  }

  @Test
  fun testNonPOMProjectWithModules() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <modules>
                         <module>foo</module>
                       </modules>
                       """.trimIndent())

    createModulePom("foo", """
      <groupId>test</groupId>
      <artifactId>foo</artifactId>
      <version>1</version>
      """.trimIndent())

    importProjectWithErrors()

    assertModules("project", "foo")
  }

  @Test
  fun testDoNotFailIfRepositoryHasEmptyLayout() = runBlocking {
    importProjectWithErrors("""
                              <groupId>test</groupId>
                              <artifactId>project</artifactId>
                              <version>1</version>
                              <repositories>
                               <repository>
                                 <id>foo1</id>
                                 <url>bar1</url>
                                 <layout/>
                               </repository>
                              </repositories>
                              <pluginRepositories>
                               <pluginRepository>
                                 <id>foo2</id>
                                 <url>bar2</url>
                                 <layout/>
                               </pluginRepository>
                              </pluginRepositories>
                              """.trimIndent())
    resolvePlugins()

    val root = rootProjects[0]
    assertProblems(root)
  }

  @Test
  fun testDoNotFailIfDistributionRepositoryHasEmptyValues() = runBlocking {
    importProjectWithErrors("""
                              <groupId>test</groupId>
                              <artifactId>project</artifactId>
                              <version>1</version>
                              <distributionManagement>
                                <repository>
                                 <id/>
                                 <url/>
                                 <layout/>
                                </repository>
                              </distributionManagement>
                              """.trimIndent())
    resolvePlugins()

    val root = rootProjects[0]
    assertProblems(root)
  }

  @Test
  fun testUnresolvedDependencies() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())

    createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>xxx</groupId>
          <artifactId>xxx</artifactId>
          <version>1</version>
        </dependency>
        <dependency>
          <groupId>yyy</groupId>
          <artifactId>yyy</artifactId>
          <version>2</version>
        </dependency>
      </dependencies>
      """.trimIndent())

    createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>zzz</groupId>
          <artifactId>zzz</artifactId>
          <version>3</version>
        </dependency>
      </dependencies>
      """.trimIndent())

    importProjectWithErrors()
    resolvePlugins()

    val root = rootProjects[0]

    assertProblems(root)
    assertProblems(getModules(root)[0],
                   "Unresolved dependency: 'xxx:xxx:jar:1'",
                   "Unresolved dependency: 'yyy:yyy:jar:2'")
    assertProblems(getModules(root)[1],
                   "Unresolved dependency: 'zzz:zzz:jar:3'")
  }

  @Test
  fun testUnresolvedPomTypeDependency() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId>xxx</groupId>
                           <artifactId>yyy</artifactId>
                           <version>4.0</version>
                           <type>pom</type>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    importProjectWithErrors()
    resolvePlugins()

    assertModuleLibDeps("project")

    val root = rootProjects[0]
    assertProblems(root, "Unresolved dependency: 'xxx:yyy:pom:4.0'")
  }

  @Test
  fun testDoesNotReportInterModuleDependenciesAsUnresolved() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())

    createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>test</groupId>
          <artifactId>m2</artifactId>
          <version>1</version>
        </dependency>
      </dependencies>
      """.trimIndent())

    createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      """.trimIndent())

    importProjectWithErrors()
    resolvePlugins()

    val root = rootProjects[0]
    assertProblems(root)
    assertProblems(getModules(root)[0])
    assertProblems(getModules(root)[1])
  }

  @Test
  fun testCircularDependencies() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                         <module>m3</module>
                       </modules>
                       """.trimIndent())

    createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>test</groupId>
          <artifactId>m2</artifactId>
          <version>1</version>
        </dependency>
      </dependencies>
      """.trimIndent())

    createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>test</groupId>
          <artifactId>m3</artifactId>
          <version>1</version>
        </dependency>
      </dependencies>
      """.trimIndent())

    createModulePom("m3", """
      <groupId>test</groupId>
      <artifactId>m3</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>test</groupId>
          <artifactId>m1</artifactId>
          <version>1</version>
        </dependency>
      </dependencies>
      """.trimIndent())

    importProjectWithErrors()

    val root = rootProjects[0]
    assertProblems(root)
    assertProblems(getModules(root)[0])
    assertProblems(getModules(root)[1])
    assertProblems(getModules(root)[2])
  }

  @Test
  fun testUnresolvedExtensionsAfterResolve() = runBlocking {
    importProjectWithErrors("""
                              <groupId>test</groupId>
                              <artifactId>project</artifactId>
                              <version>1</version>
                              <build>
                               <extensions>
                                 <extension>
                                   <groupId>xxx</groupId>
                                   <artifactId>yyy</artifactId>
                                   <version>1</version>
                                  </extension>
                                </extensions>
                              </build>
                              """.trimIndent())

    resolveDependenciesAndImport()
    val root = rootProjects[0]
    val problems = root.getProblems()
    UsefulTestCase.assertSize(1, problems)
    assertTrue(problems[0]!!.description!!.contains("Could not find artifact xxx:yyy:jar:1"))
  }

  @Test
  fun testDoesNotReportExtensionsThatWereNotTriedToBeResolved() = runBlocking {
    // for some reasons this plugins is not rtied to be resolved by embedder.
    // we shouldn't report it as unresolved.
    importProjectWithErrors("""
                              <groupId>test</groupId>
                              <artifactId>project</artifactId>
                              <version>1</version>
                              <build>
                                <extensions>
                                 <extension>
                                    <groupId>org.apache.maven.wagon</groupId>
                                    <artifactId>wagon-ssh-external</artifactId>
                                    <version>1.0-alpha-6</version>
                                  </extension>
                                </extensions>
                              </build>
                              """.trimIndent())
    resolvePlugins()

    assertProblems(rootProjects[0])

    resolveDependenciesAndImport()
    assertProblems(rootProjects[0])
  }

  @Test
  fun testUnresolvedBuildExtensionsInModules() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())

    createModulePom("m1",
                    """
                      <groupId>test</groupId>
                      <artifactId>m1</artifactId>
                      <version>1</version>
                      <build>
                       <extensions>
                         <extension>
                           <groupId>xxx</groupId>
                           <artifactId>xxx</artifactId>
                           <version>1</version>
                          </extension>
                        </extensions>
                      </build>
                      """.trimIndent())

    createModulePom("m2",
                    """
                      <groupId>test</groupId>
                      <artifactId>m2</artifactId>
                      <version>1</version>
                      <build>
                       <extensions>
                         <extension>
                           <groupId>yyy</groupId>
                           <artifactId>yyy</artifactId>
                           <version>1</version>
                          </extension>
                         <extension>
                           <groupId>zzz</groupId>
                           <artifactId>zzz</artifactId>
                           <version>1</version>
                          </extension>
                        </extensions>
                      </build>
                      """.trimIndent())

    importProjectWithErrors()

    val root = rootProjects[0]

    assertProblems(root)

    var problems = getModules(root)[0].getProblems()
    UsefulTestCase.assertSize(1, problems)
    assertTrue(problems[0]!!.description, problems[0]!!.description!!.contains("Could not find artifact xxx:xxx:jar:1"))


    problems = getModules(root)[1].getProblems()
    UsefulTestCase.assertSize(1, problems)
    assertTrue(problems[0]!!.description, problems[0]!!.description!!.contains("Could not find artifact yyy:yyy:jar:1"))
  }

  @Test
  fun testUnresolvedPlugins() = runBlocking {
    importProjectWithErrors("""
                              <groupId>test</groupId>
                              <artifactId>project</artifactId>
                              <version>1</version>
                              <build>
                               <plugins>
                                 <plugin>
                                   <groupId>xxx</groupId>
                                   <artifactId>yyy</artifactId>
                                   <version>1</version>
                                  </plugin>
                                </plugins>
                              </build>
                              """.trimIndent())
    resolvePlugins()

    val root = rootProjects[0]
    assertProblems(root, "Unresolved plugin: 'xxx:yyy:1'")
  }

  @Test
  @Throws(Exception::class)
  fun testDoNotReportResolvedPlugins() {
    val helper = MavenCustomRepositoryHelper(myDir, "plugins")

    repositoryPath = helper.getTestDataPath("plugins")

    importProjectWithErrors("""
                              <groupId>test</groupId>
                              <artifactId>project</artifactId>
                              <version>1</version>
                              <build>
                               <plugins>
                                 <plugin>
                                   <groupId>org.apache.maven.plugins</groupId>
                                   <artifactId>maven-compiler-plugin</artifactId>
                                   <version>2.0.2</version>
                                  </plugin>
                                </plugins>
                              </build>
                              """.trimIndent())

    resolvePlugins()

    assertProblems(rootProjects[0])
  }

  @Test
  fun testUnresolvedPluginsAsExtensions() = runBlocking {
    importProjectWithErrors("""
                              <groupId>test</groupId>
                              <artifactId>project</artifactId>
                              <version>1</version>
                              <build>
                               <plugins>
                                 <plugin>
                                   <groupId>xxx</groupId>
                                   <artifactId>yyy</artifactId>
                                   <version>1</version>
                                   <extensions>true</extensions>
                                  </plugin>
                                </plugins>
                              </build>
                              """.trimIndent())
    resolvePlugins()

    assertModules("project")

    val root = rootProjects[0]
    val problems = root.getProblems()
    UsefulTestCase.assertSize(2, problems)
    assertTrue(problems[0]!!.description, problems[0]!!.description!!.contains("Could not find artifact xxx:yyy:jar:1"))
    assertTrue(problems[1]!!.description, problems[1]!!.description!!.contains("Unresolved plugin: 'xxx:yyy:1'"))
  }

  @Test
  @Throws(Exception::class)
  fun testInvalidSettingsXml() {
    updateSettingsXml("<localRepo<<")

    importProjectWithErrors("""
                              <groupId>test</groupId>
                              <artifactId>project</artifactId>
                              <version>1</version>
                              """.trimIndent())
    assertModules("project")

    val root = rootProjects[0]
    assertProblems(root, "'settings.xml' has syntax errors")
  }

  @Test
  fun testInvalidProfilesXml() = runBlocking {
    createProfilesXml("<prof<<")

    importProjectWithErrors("""
                              <groupId>test</groupId>
                              <artifactId>project</artifactId>
                              <version>1</version>
                              """.trimIndent())
    assertModules("project")

    val root = rootProjects[0]
    assertProblems(root, "'profiles.xml' has syntax errors")
  }

  private fun importProjectWithErrors(@Language(value = "XML", prefix = "<project>", suffix = "</project>") s: String) {
    createProjectPom(s)
    importProjectWithErrors()
  }

  private val rootProjects: List<MavenProject>
    get() = projectsTree.rootProjects

  private fun getModules(p: MavenProject): List<MavenProject> {
    return projectsTree.getModules(p)
  }

  companion object {
    private fun assertProblems(project: MavenProject, vararg expectedProblems: String) {
      val actualProblems: MutableList<String?> = ArrayList()
      for (each in project.getProblems()) {
        actualProblems.add(each.description)
      }
      assertOrderedElementsAreEqual(actualProblems, *expectedProblems)
    }

    private fun assertContainsProblems(project: MavenProject, vararg expectedProblems: String) {
      val actualProblems: MutableList<String?> = ArrayList()
      for (each in project.getProblems()) {
        actualProblems.add(each.description)
      }
      UsefulTestCase.assertContainsElements(actualProblems, *expectedProblems)
    }
  }
}
