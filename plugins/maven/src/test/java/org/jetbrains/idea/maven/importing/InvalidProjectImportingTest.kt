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

import com.intellij.build.SyncViewManager
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.BuildIssueEvent
import com.intellij.maven.testFramework.fixtures.MavenCustomRepositoryHelper
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertContain
import com.intellij.maven.testFramework.fixtures.assertModuleLibDeps
import com.intellij.maven.testFramework.fixtures.assertModules
import com.intellij.maven.testFramework.fixtures.assertOrderedElementsAreEqual
import com.intellij.maven.testFramework.fixtures.assumeMaven3
import com.intellij.maven.testFramework.fixtures.assumeMaven4
import com.intellij.maven.testFramework.fixtures.assumeModel_4_0_0
import com.intellij.maven.testFramework.fixtures.assumeModel_4_1_0
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.doImportProjectsAsync
import com.intellij.maven.testFramework.fixtures.forMaven3
import com.intellij.maven.testFramework.fixtures.forMaven4
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.isMaven4
import com.intellij.maven.testFramework.fixtures.isModel410
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.mavenVersionIsOrMoreThan
import com.intellij.maven.testFramework.fixtures.moduleTag
import com.intellij.maven.testFramework.fixtures.modulesTag
import com.intellij.maven.testFramework.fixtures.projectsTree
import com.intellij.maven.testFramework.fixtures.testRootDisposable
import com.intellij.maven.testFramework.fixtures.updateAllProjects
import com.intellij.maven.testFramework.fixtures.updateModulePom
import com.intellij.maven.testFramework.fixtures.updateProjectPom
import com.intellij.maven.testFramework.fixtures.updateSettingsXml
import com.intellij.openapi.application.edtWriteAction
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.UsefulTestCase.assertSize
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.execution.SyncBundle
import org.jetbrains.idea.maven.fixtures.runWithoutStaticSync
import org.jetbrains.idea.maven.project.MavenProject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class InvalidProjectImportingTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion,
    skipPluginResolution = false,
  )
  

  @Test
  fun testSubprojectsWithOldModel() = runBlocking {
    maven.runWithoutStaticSync()
    maven.assumeMaven4()
    maven.assumeModel_4_0_0("we test convertion from 4.0.0 to 4.1.0 here")
    maven.createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      """.trimIndent())

    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <subprojects>
                           <subproject>m1</subproject>
                       </subprojects>
                       """.trimIndent())
    val events = ArrayList<BuildEvent>()
    val myTestSyncViewManager = object : SyncViewManager(maven.project) {
      override fun onEvent(buildId: Any, event: BuildEvent) {
        events.add(event)
      }
    }

    maven.project.replaceService(SyncViewManager::class.java, myTestSyncViewManager, maven.testRootDisposable)
    maven.importProjectAsync()

    val issues = events.filterIsInstance<BuildIssueEvent>().filter { it.description!=null && it.description!!.contains(" 'subprojects' unexpected subprojects element")}
    assertSize(1, issues)
    assertEquals(SyncBundle.message("maven.sync.incorrect.model.version"), issues[0].issue.title)

  }

  @Test
  fun testSystemDependencyWithoutPath() = runBlocking {
    maven.runWithoutStaticSync()
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId>junit</groupId>
                           <artifactId>junit</artifactId>
                           <version>4.0</version>
                           <scope>system</scope>
                         </dependency>
                       </dependencies>
                       """.trimIndent())
    maven.doImportProjectsAsync(listOf(maven.projectPom), false)

    maven.assertModules("project")
    maven.forMaven3 {
      //IDEA-357072
      maven.assertModuleLibDeps("project") // dependency was not added due to reported pom model problem.
    }

    maven.forMaven4 {
      val expected = arrayOf(
        "'dependencies.dependency.systemPath' for groupId='junit', artifactId='junit', type='jar' is missing.",
        "'dependencies.dependency.scope' for groupId='junit', artifactId='junit', type='jar' declares usage of deprecated 'system' scope",
      )
      assertProblems(maven.projectsManager.findProject(maven.projectPom)!!, *expected)
    }
  }

  @Test
  fun testResetDependenciesWhenProjectContainsErrors() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                       </modules>
                       """.trimIndent())

    maven.createModulePom("m1", """
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

    maven.importProjectAsync()
    maven.assertModules("project", "m1")
    maven.assertModuleLibDeps("m1", "Maven: somegroup:artifact:1.0")


    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                       </modules>
                       """.trimIndent())
    maven.updateModulePom("m1", """
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

    maven.updateAllProjects()
    maven.assertModules("project", "m1")
    maven.assertModuleLibDeps("m1", "Maven: somegroup:artifact:2.0")
  }

  @Test
  fun testShouldNotResetDependenciesWhenProjectContainsUnrecoverableErrors() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                       </modules>
                       """.trimIndent())

    maven.createModulePom("m1", """
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

    maven.importProjectAsync()
    maven.assertModules("project", "m1")
    maven.assertModuleLibDeps("m1", "Maven: somegroup:artifact:1.0")


    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                       </modules>
                       """.trimIndent())
    maven.updateModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
        <dependency>
          <groupId>somegroup</groupId>
          <artifactId>artifact</artifactId>
          <version>2.0  </dependency>
      </dependencies>
      """.trimIndent())

    maven.updateAllProjects()
    maven.assertModules("project", "m1")
    maven.assertModuleLibDeps("m1", "Maven: somegroup:artifact:1.0")
  }

  @Test
  fun testUnknownProblemWithEmptyFile() = runBlocking {
    maven.createProjectPom("")
    edtWriteAction { maven.projectPom.setBinaryContent(ByteArray(0)) }

    maven.importProjectAsync()

    maven.assertModules("project")

    val root = rootProjects[0]
    assertProblems(root, "'pom.xml' has syntax errors")
  }

  @Test
  fun testUndefinedPropertyInHeader() = runBlocking {
    maven.importProjectAsync("""
                              <groupId>test</groupId>
                              <artifactId>${'$'}{undefined}</artifactId>
                              <version>1</version>
                              """.trimIndent())

    maven.assertModules("project")
    val root = rootProjects[0]
    val expectedProblems = if (maven.isModel410())
      arrayOf(
        "'artifactId' contains an expression but should be a constant.",
        "1 problem was     - [FATAL] 'artifactId' contains an expression but should be a constant. @ line 7, column 1"
      )
    else if (maven.isMaven4)
      arrayOf(
        "Invalid Collect Request: null -> [] < [central-mirror (https://cache-redirector.jetbrains.com/repo1.maven.org/maven2, default, releases)]",
      )
    else
      arrayOf("'artifactId' with value '\${undefined}' does not match a valid id pattern.")

    assertProblems(root, *expectedProblems)
  }

  @Test
  fun testRecursiveInterpolation() = runBlocking {
    maven.importProjectAsync("""
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

    maven.assertModules("project")

    val root = rootProjects[0]
    val problems = root.problems
    assertFalse(problems.isEmpty())
    maven.forMaven3 {
      maven.assertModuleLibDeps("project", "Maven: group:artifact:1")
    }
  }

  @Test
  fun testUnresolvedParent() = runBlocking {
    maven.importProjectAsync("""
                              <groupId>test</groupId>
                              <artifactId>project</artifactId>
                              <version>1</version>
                              <parent>
                                <groupId>test</groupId>
                                <artifactId>parent</artifactId>
                                <version>1</version>
                              </parent>
                              """.trimIndent())

    maven.assertModules("project")

    val root = rootProjects[0]
    val problems = root.problems
    UsefulTestCase.assertSize(1, problems)
    val description = if (maven.mavenVersionIsOrMoreThan("3.9.0"))
      "Could not find artifact test:parent:pom:1"
    else
      "Non-resolvable parent POM for test:project:1"
    assertTrue(problems[0]!!.description!!.contains(description), problems[0]!!.description)
  }

  @Test
  fun testUnresolvedParentForInvalidProject() = runBlocking {
    maven.projectsManager.generalSettings.isAlwaysUpdateSnapshots = true
    // not of the 'pom' type
    maven.importProjectAsync("""
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
    val problems = root.problems
    maven.forMaven3 {
      assertSize(2, problems)
      val description = if (maven.mavenVersionIsOrMoreThan("3.9.0"))
        "Could not find artifact test:parent:pom:1"
      else
        "Non-resolvable parent POM for test:project:1"
      assertTrue(problems[0]!!.description!!.contains(description), problems[0]!!.description)
      assertTrue(problems[1]!!.description == "Module 'foo' not found", problems[1]!!.description)
    }
    maven.forMaven4 {
      assertContain(problems.map { it.description }, "Module 'foo' not found")
    }
  }

  @Test
  fun testMissingModules() = runBlocking {
    maven.importProjectAsync("""
                              <groupId>test</groupId>
                              <artifactId>project</artifactId>
                              <version>1</version>
                              <packaging>pom</packaging>
                              <modules>
                                <module>foo</module>
                              </modules>
                              """.trimIndent())

    maven.assertModules("project")

    val root = rootProjects[0]
    val problem = root.problems.firstOrNull { it.description!!.contains("Module 'foo' not found") }
    assertNotNull(problem, "Expected: Module 'foo' not found")
  }


  @Test
  fun testOldModuleTagWithNewModel() = runBlocking {
    // invalid packaging
    maven.assumeModel_4_1_0("applicable only for new 4.1.0 model")
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>foo</module>
                       </modules>
                       """.trimIndent())

    maven.createModulePom("foo", """
      <groupId>test</groupId>
      <artifactId>foo</artifactId>
      <version>1</version>
      """.trimIndent())
    maven.importProjectAsync()
    maven.assertModules("project", "foo")

    val root = rootProjects[0]
    assertProblems(root, "'modules' deprecated modules element, use subprojects instead")
  }


  @Test
  fun testInvalidProjectModel() = runBlocking {
    maven.assumeModel_4_0_0("IDEA-379706")
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>jar</packaging>
                       <${maven.modulesTag}>
                         <${maven.moduleTag}>foo</${maven.moduleTag}>
                       </${maven.modulesTag}>
                       """.trimIndent())

    maven.createModulePom("foo", """
      <groupId>test</groupId>
      <artifactId>foo</artifactId>
      <version>1</version>
      """.trimIndent())
    maven.importProjectAsync()
    maven.assertModules("project", "foo")

    val root = rootProjects[0]
    assertProblems(root, "'packaging' with value 'jar' is invalid. Aggregator projects require 'pom' as packaging.")
  }

  @Test
  fun testInvalidModuleModel() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <${maven.modulesTag}>
                         <${maven.moduleTag}>foo</${maven.moduleTag}>
                       </${maven.modulesTag}>
                       """.trimIndent())

    maven.createModulePom("foo", """
      <groupId>test</groupId>
      <artifactId>foo</artifactId>
      <version>1
      """.trimIndent()) //  invalid tag

    maven.importProjectAsync()
    maven.assertModules("project", "foo")

    val root = rootProjects[0]
    val mavenProject = getModules(root)[0]
    maven.forMaven3 {
      val problem = mavenProject.problems[0].description!!
      assertTrue(problem.contains("Non-parseable POM"))
    }
    maven.forMaven4 {
      assertProblems(mavenProject, "'pom.xml' has syntax errors")
    }
  }

  @Test
  fun testSeveratInvalidModulesAndWithSameName() = runBlocking {
    maven.createProjectPom("""
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

    maven.createModulePom("foo", """
      <groupId>test</groupId>
      <artifactId>foo</artifactId>
      <version>1
      """.trimIndent()) //  invalid tag

    maven.createModulePom("bar1", """
      <groupId>test</groupId>
      <artifactId>bar</artifactId>
      <version>1
      """.trimIndent()) //  invalid tag

    maven.createModulePom("bar2", """
      <groupId>test</groupId>
      <artifactId>bar</artifactId>
      <version>1
      """.trimIndent()) //  invalid tag

    maven.createModulePom("bar3", """
      <groupId>org.test</groupId>
      <artifactId>bar</artifactId>
      <version>1
      """.trimIndent()) //  invalid tag

    maven.importProjectAsync()
    maven.assertModules("project", "foo", "bar (1)", "bar (2)", "bar (3) (org.test)")
  }

  @Test
  fun testInvalidProjectWithModules() = runBlocking {
    // invalid tag
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1<modules>
                         <module>foo</module>
                       </modules>
                       """.trimIndent())

    maven.createModulePom("foo", """
      <groupId>test</groupId>
      <artifactId>foo</artifactId>
      <version>1</version>
      """.trimIndent())

    maven.importProjectAsync()

    maven.assertModules("project", "foo")
  }

  @Test
  fun testNonPOMProjectWithModules() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <modules>
                         <module>foo</module>
                       </modules>
                       """.trimIndent())

    maven.createModulePom("foo", """
      <groupId>test</groupId>
      <artifactId>foo</artifactId>
      <version>1</version>
      """.trimIndent())

    maven.importProjectAsync()

    maven.assertModules("project", "foo")
  }

  @Test
  fun testDoNotFailIfRepositoryHasEmptyLayout() = runBlocking {
    maven.importProjectAsync("""
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

    val root = rootProjects[0]
    assertProblems(root)
  }

  @Test
  fun testDoNotFailIfDistributionRepositoryHasEmptyValues() = runBlocking {
    maven.assumeMaven3()
    maven.importProjectAsync("""
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

    val root = rootProjects[0]
    assertProblems(root)
  }

  @Test
  fun testUnresolvedDependencies() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <${maven.modulesTag}>
                         <${maven.moduleTag}>m1</${maven.moduleTag}>
                         <${maven.moduleTag}>m2</${maven.moduleTag}>
                       </${maven.modulesTag}>
                       """.trimIndent())

    maven.createModulePom("m1", """
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

    maven.createModulePom("m2", """
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

    maven.importProjectAsync()

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
    maven.createProjectPom("""
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

    maven.importProjectAsync()

    maven.assertModuleLibDeps("project")

    val root = rootProjects[0]
    assertProblems(root, "Unresolved dependency: 'xxx:yyy:pom:4.0'")
  }

  @Test
  fun testDoesNotReportInterModuleDependenciesAsUnresolved() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <${maven.modulesTag}>
                         <${maven.moduleTag}>m1</${maven.moduleTag}>
                         <${maven.moduleTag}>m2</${maven.moduleTag}>
                       </${maven.modulesTag}>
                       """.trimIndent())

    maven.createModulePom("m1", """
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

    maven.createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      """.trimIndent())

    maven.importProjectAsync()

    val root = rootProjects[0]
    assertProblems(root)
    assertProblems(getModules(root)[0])
    assertProblems(getModules(root)[1])
  }

  @Test
  fun testCircularDependencies() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <${maven.modulesTag}>
                         <${maven.moduleTag}>m1</${maven.moduleTag}>
                         <${maven.moduleTag}>m2</${maven.moduleTag}>
                         <${maven.moduleTag}>m3</${maven.moduleTag}>
                       </${maven.modulesTag}>
                       """.trimIndent())

    maven.createModulePom("m1", """
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

    maven.createModulePom("m2", """
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

    maven.createModulePom("m3", """
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

    maven.importProjectAsync()

    val root = rootProjects[0]
    assertProblems(root)
    assertProblems(getModules(root)[0])
    assertProblems(getModules(root)[1])
    assertProblems(getModules(root)[2])
  }

  @Test
  fun testUnresolvedExtensionsAfterResolve() = runBlocking {
    maven.importProjectAsync("""
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

    val root = rootProjects[0]
    val problems = root.problems
    UsefulTestCase.assertSize(1, problems)
    maven.forMaven3 {
      val description = if (maven.mavenVersionIsOrMoreThan("3.9.8"))
        "Unresolveable build extension: Plugin xxx:yyy:1 or one of its dependencies could not be resolved"
      else
        "Could not find artifact xxx:yyy:jar:1"
      assertTrue(problems[0].description!!.contains(description))
    }

    maven.forMaven4 {
      assertTrue(problems.isNotEmpty())
      assertTrue(
        problems[0].description!!.contains("Could not find artifact xxx:yyy:jar:1") ||
        problems[0].description!!.contains("xxx:yyy:jar:1 was not found")
      )
    }

  }

  @Test
  fun testDoesNotReportExtensionsThatWereNotTriedToBeResolved() = runBlocking {
    // for some reasons this plugins is not rtied to be resolved by embedder.
    // we shouldn't report it as unresolved.
    maven.importProjectAsync("""
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

    assertProblems(rootProjects[0])

    maven.updateAllProjects()

    assertProblems(rootProjects[0])
  }

  @Test
  fun testUnresolvedBuildExtensionsInModules() = runBlocking {
    maven.assumeMaven3()
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <${maven.modulesTag}>
                         <${maven.moduleTag}>m1</${maven.moduleTag}>
                         <${maven.moduleTag}>m2</${maven.moduleTag}>
                       </${maven.modulesTag}>
                       """.trimIndent())

    maven.createModulePom("m1",
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

    maven.createModulePom("m2",
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

    maven.importProjectAsync()

    val root = rootProjects[0]

    assertProblems(root)


    maven.forMaven3 {
      var problems = getModules(root)[0].problems
      assertSize(1, problems)
      val description = if (maven.mavenVersionIsOrMoreThan("3.9.8"))
        "Unresolveable build extension: Plugin xxx:xxx:1 or one of its dependencies could not be resolved"
      else
        "Could not find artifact xxx:xxx:jar:1"
      assertTrue(problems[0].description!!.contains(description), problems[0].description)

      problems = getModules(root)[1].problems
      assertSize(1, problems)
      val description2 = if (maven.mavenVersionIsOrMoreThan("3.9.8"))
        "Unresolveable build extension: Plugin yyy:yyy:1 or one of its dependencies could not be resolved"
      else
        "Could not find artifact yyy:yyy:jar:1"
      assertTrue(problems[0].description!!.contains(description2), problems[0].description)
    }

    maven.forMaven4 {
      var problems = getModules(root)[0].problems
      assertTrue(
        problems[0].description!!.contains("Plugin xxx:xxx:1 or one of its dependencies could not be resolved")
        || problems[0].description!!.contains("Could not find artifact xxx:xxx:jar:1") ||
        problems[0].description!!.contains("xxx:xxx:jar:1 was not found")
      )
      problems = getModules(root)[1].problems
      assertTrue(
        problems[0].description!!.contains("Plugin yyy:yyy:1 or one of its dependencies could not be resolved")
        || problems[0].description!!.contains("Could not find artifact yyy:yyy:jar:1") ||
        problems[0].description!!.contains("yyy:yyy:jar:1 was not found")
      )
    }
  }

  @Test
  fun testUnresolvedPlugins() = runBlocking {
    maven.importProjectAsync("""
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

    val root = rootProjects[0]
    assertProblems(root, "Unresolved plugin: 'xxx:yyy:1'")
  }

  @Test
  fun testDoNotReportResolvedPlugins() = runBlocking {
    val helper = MavenCustomRepositoryHelper(maven.dir, "plugins")

    maven.repositoryPath = helper.getTestData("plugins")

    maven.importProjectAsync("""
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


    assertProblems(rootProjects[0])
  }

  @Test
  fun testUnresolvedPluginsAsExtensions() = runBlocking {
    val groupId = "junit"
    val artifactId = "yyy"
    val version = "1"
    val coordinates = "$groupId:$artifactId:$version"
    val jarCoordinates = "$groupId:$artifactId:jar:$version"

    maven.importProjectAsync("""
                            <groupId>test</groupId>
                            <artifactId>project</artifactId>
                            <version>1</version>
                            <build>
                             <plugins>
                               <plugin>
                                 <groupId>$groupId</groupId>
                                 <artifactId>$artifactId</artifactId>
                                 <version>$version</version>
                                 <extensions>true</extensions>
                                </plugin>
                              </plugins>
                            </build>
                            """.trimIndent())

    maven.assertModules("project")

    val root = rootProjects[0]
    val problems = root.problems

    maven.forMaven3 {
      assertSize(1, problems)

      val description = if (maven.mavenVersionIsOrMoreThan("3.9.8"))
        "Unresolveable build extension: Plugin $coordinates or one of its dependencies could not be resolved"
      else
        "Could not find artifact $jarCoordinates"

      assertTrue(problems[0].description!!.contains(description), problems[0].description)
    }

    maven.forMaven4 {
      assertSize(1, problems)

      assertTrue(problems[0].description!!.contains("Could not find artifact $jarCoordinates") ||
        problems[0].description!!.contains("$jarCoordinates was not found"), "Expected unresolved dependency error for $jarCoordinates, but got: ${problems[0].description}")
    }
  }

  @Test
  fun testInvalidSettingsXml() = runBlocking {
    maven.updateSettingsXml("<localRepo<<")

    maven.importProjectAsync("""
                              <groupId>test</groupId>
                              <artifactId>project</artifactId>
                              <version>1</version>
                              """.trimIndent())
    maven.assertModules("project")

    val root = rootProjects[0]
    assertProblems(root, "'settings.xml' has syntax errors")
  }

  @Test
  fun testImportingWithEmptyPath() = runBlocking {
    maven.importProjectAsync("""
                              <groupId>test</groupId>
                              <artifactId>project</artifactId>
                              <version>1</version>
                              <packaging>pom</packaging>
                              <modules>
                                  <module></module>
                              </modules>
                              """.trimIndent())
    maven.assertModules("project")
    val rootProject = maven.projectsManager.findProject(maven.projectPom)
    assertNotNull(rootProject, "Project should be found")
    val rootOfRoot = maven.projectsManager.findRootProject(rootProject!!)
    assertNotNull(rootOfRoot, "Root of root should be null")
  }

  @Test
  fun testImportingWithSelfInclusionInclusion() = runBlocking {
    maven.importProjectAsync("""
                              <groupId>test</groupId>
                              <artifactId>project</artifactId>
                              <version>1</version>
                              <packaging>pom</packaging>
                              <modules>
                                  <module>./pom.xml</module>
                              </modules>
                              """.trimIndent())
    maven.assertModules("project")
  }

  private val rootProjects: List<MavenProject>
    get() = maven.projectsTree.rootProjects

  private fun getModules(p: MavenProject): List<MavenProject> {
    return maven.projectsTree.getModules(p)
  }

  private fun assertProblems(project: MavenProject, vararg expectedProblems: String) {
    val actualProblems: MutableList<String?> = ArrayList()
    for (each in project.problems) {
      actualProblems.add(each.description?.trim()?.lines()?.joinToString(""))
    }
    assertOrderedElementsAreEqual(actualProblems, *expectedProblems)
  }
}
