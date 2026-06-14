/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.project.importing

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertUnorderedElementsAreEqual
import com.intellij.maven.testFramework.fixtures.assertUnorderedPathsAreEqual
import com.intellij.maven.testFramework.fixtures.assumeModel_4_0_0
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createProfilesXmlOldStyle
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.importProjectWithProfiles
import com.intellij.maven.testFramework.fixtures.importProjectsAsync
import com.intellij.maven.testFramework.fixtures.mavenGeneralSettings
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.pathFromBasedir
import com.intellij.maven.testFramework.fixtures.projectPath
import com.intellij.maven.testFramework.fixtures.testRootDisposable
import com.intellij.maven.testFramework.fixtures.updateAllProjects
import com.intellij.maven.testFramework.fixtures.updateModulePom
import com.intellij.maven.testFramework.fixtures.updateProjectPom
import com.intellij.maven.testFramework.fixtures.updateSettingsXml
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.UsefulTestCase.assertEmpty
import com.intellij.testFramework.UsefulTestCase.assertSameElements
import com.intellij.testFramework.UsefulTestCase.assertSize
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.fixtures.MyLoggingListener
import org.jetbrains.idea.maven.fixtures.deleteProject
import org.jetbrains.idea.maven.fixtures.log
import org.jetbrains.idea.maven.fixtures.mavenEmbedderWrappers
import org.jetbrains.idea.maven.fixtures.rawProgressReporter
import org.jetbrains.idea.maven.fixtures.resolve
import org.jetbrains.idea.maven.fixtures.tree
import org.jetbrains.idea.maven.fixtures.update
import org.jetbrains.idea.maven.fixtures.updateAll
import org.jetbrains.idea.maven.fixtures.updateTimestamps
import org.jetbrains.idea.maven.fixtures.waitForImportWithinTimeout
import org.jetbrains.idea.maven.model.MavenExplicitProfiles
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsTree
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import java.util.Arrays
import java.util.Set

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenProjectsTreeReadingTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  @Test
  fun testTwoRootProjects() = runBlocking {
    val m1 = maven.createModulePom("m1",
                             """
                             <groupId>test</groupId>
                             <artifactId>m1</artifactId>
                             <version>1</version>
                             """.trimIndent())
    val m2 = maven.createModulePom("m2",
                             """
                             <groupId>test</groupId>
                             <artifactId>m2</artifactId>
                             <version>1</version>
                             """.trimIndent())
    maven.updateAll(m1, m2)
    val roots = maven.tree.rootProjects
    assertEquals(2, roots.size)
    assertEquals(m1, roots[0].file)
    assertEquals(m2, roots[1].file)
  }

  @Test
  fun testModulesWithWhiteSpaces() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>
                         m  </module>
                       </modules>
                       """.trimIndent())
    val m = maven.createModulePom("m",
                            """
                            <groupId>test</groupId>
                            <artifactId>m</artifactId>
                            <version>1</version>
                            """.trimIndent())
    maven.updateAll(maven.projectPom)
    val roots = maven.tree.rootProjects
    assertEquals(1, roots.size)
    assertEquals(maven.projectPom, roots[0].file)
    assertEquals(1, maven.tree.getModules(roots[0]).size)
    assertEquals(m, maven.tree.getModules(roots[0])[0].file)
  }

  @Test
  fun testDoNotImportChildAsRootProject() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m</module>
                       </modules>
                       """.trimIndent())
    val m = maven.createModulePom("m",
                            """
                            <groupId>test</groupId>
                            <artifactId>m</artifactId>
                            <version>1</version>
                            """.trimIndent())
    maven.updateAll(maven.projectPom, m)
    val roots = maven.tree.rootProjects
    assertEquals(1, roots.size)
    assertEquals(maven.projectPom, roots[0].file)
    assertEquals(1, maven.tree.getModules(roots[0]).size)
    assertEquals(m, maven.tree.getModules(roots[0])[0].file)
  }

  @Test
  fun testDoNotImportSameRootProjectTwice() = runBlocking {
    val listener = MyLoggingListener()
    maven.project.messageBus.connect(maven.testRootDisposable).subscribe(MavenProjectsTree.Listener.TOPIC, listener)
    val m1 = maven.createModulePom("m1",
                             """
                             <groupId>test</groupId>
                             <artifactId>m1</artifactId>
                             <version>1</version>
                             """.trimIndent())
    val m2 = maven.createModulePom("m2",
                             """
                             <groupId>test</groupId>
                             <artifactId>m2</artifactId>
                             <version>1</version>
                             """.trimIndent())
    maven.updateAll(m1, m2, m1)
    val roots = maven.tree.rootProjects
    assertEquals(2, roots.size)
    assertEquals(m1, roots[0].file)
    assertEquals(m2, roots[1].file)
    assertEquals(log().add("updated", "m1", "m2").add("deleted"), listener.log)
  }

  @Test
  fun testRereadingChildIfParentWasReadAfterIt() = runBlocking {
    maven.assumeModel_4_0_0("4.1.0 model does not allow such case: - [FATAL] 'artifactId' contains an expression but should be a constant")
    val m2 = maven.createModulePom("m2",
                             """
                             <groupId>test</groupId>
                             <artifactId>${'$'}{childId}</artifactId>
                             <version>1</version>
                             <parent>
                               <groupId>test</groupId>
                               <artifactId>m1</artifactId>
                               <version>1</version>
                             </parent>
                             """.trimIndent())
    maven.importProjectsAsync(m2)
    assertSize(1, maven.tree.rootProjects)

    val m1 = maven.createModulePom("m1",
                             """
                             <groupId>test</groupId>
                             <artifactId>m1</artifactId>
                             <version>1</version>
                             <properties>
                              <childId>m2</childId>
                             </properties>
                             """.trimIndent())

    maven.waitForImportWithinTimeout {
      maven.projectsManager.addManagedFiles(listOf(m1))
    }

    val roots = maven.tree.rootProjects
    assertEquals(2, roots.size)
    assertEquals(m1, roots[0].file)
    assertEquals(m2, roots[1].file)
    assertEquals("m1", roots[0].mavenId.artifactId)
    assertEquals("m2", roots[1].mavenId.artifactId)
  }

  @Test
  fun testSameProjectAsModuleOfSeveralProjects() = runBlocking {
    val p1 = maven.createModulePom("project1",
                             """
                             <groupId>test</groupId>
                             <artifactId>project1</artifactId>
                             <version>1</version>
                             <packaging>pom</packaging>
                             <modules>
                               <module>../module</module>
                             </modules>
                             """.trimIndent())
    val p2 = maven.createModulePom("project2",
                             """
                             <groupId>test</groupId>
                             <artifactId>project2</artifactId>
                             <version>1</version>
                             <packaging>pom</packaging>
                             <modules>
                               <module>../module</module>
                             </modules>
                             """.trimIndent())
    val m = maven.createModulePom("module",
                            """
                            <groupId>test</groupId>
                            <artifactId>module</artifactId>
                            <version>1</version>
                            """.trimIndent())
    maven.updateAll(p1, p2)
    val roots = maven.tree.rootProjects
    assertEquals(2, roots.size)
    assertEquals(p1, roots[0].file)
    assertEquals(p2, roots[1].file)
    assertEquals(1, maven.tree.getModules(roots[0]).size)
    assertEquals(m, maven.tree.getModules(roots[0])[0].file)
    assertEquals(0, maven.tree.getModules(roots[1]).size)
  }

  @Test
  fun testSameProjectAsModuleOfSeveralProjectsInHierarchy() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>module1</module>
                         <module>module1/module2</module>
                       </modules>
                       """.trimIndent())
    val m1 = maven.createModulePom("module1",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>module1</artifactId>
                                       <version>1</version>
                                       <packaging>pom</packaging>
                                       <modules>
                                         <module>module2</module>
                                       </modules>
                                       """.trimIndent())
    val m2 = maven.createModulePom("module1/module2",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>module2</artifactId>
                                       <version>1</version>
                                       """.trimIndent())
    maven.updateAll(maven.projectPom)
    val roots = maven.tree.rootProjects
    assertEquals(1, roots.size)
    val allModules = collectAllModulesRecursively(
      maven.tree, roots[0])
    assertEquals(2, allModules.size)
    UsefulTestCase.assertSameElements(Set.of(m1, m2), allModules.map({ m: MavenProject -> m.file }))
  }

  @Test
  fun testRemovingChildProjectFromRootProjects() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m</module>
                       </modules>
                       """.trimIndent())
    val m = maven.createModulePom("m",
                            """
                                      <groupId>test</groupId>
                                      <artifactId>m</artifactId>
                                      <version>1</version>
                                      """.trimIndent())

    // all projects are processed in the specified order
    // if we have imported a child project as a root one,
    // we have to correct ourselves and to remove it from roots.
    maven.updateAll(m, maven.projectPom)
    val roots = maven.tree.rootProjects
    assertEquals(1, roots.size)
    assertEquals(maven.projectPom, roots[0].file)
    assertEquals(1, maven.tree.getModules(roots[0]).size)
    assertEquals(m, maven.tree.getModules(roots[0])[0].file)
  }

  @Test
  fun testSendingNotificationsWhenAggregationChanged() = runBlocking {
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
    val m1 = maven.createModulePom("m1",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m1</artifactId>
                                       <version>1</version>
                                       """.trimIndent())
    val m2 = maven.createModulePom("m2",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m2</artifactId>
                                       <version>1</version>
                                       """.trimIndent())
    maven.updateAll(maven.projectPom, m1, m2)
    var roots = maven.tree.rootProjects
    assertEquals(1, roots.size)
    assertEquals(2, maven.tree.getModules(roots[0]).size)
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m1</module>
                       </modules>
                       """.trimIndent())
    maven.updateAll(maven.projectPom, m1, m2)
    roots = maven.tree.rootProjects
    assertEquals(2, roots.size)
    assertEquals(1, maven.tree.getModules(roots[0]).size)
  }

  @Test
  fun testUpdatingWholeModel() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m</module>
                       </modules>
                       """.trimIndent())
    val m = maven.createModulePom("m",
                            """
                                      <groupId>test</groupId>
                                      <artifactId>m</artifactId>
                                      <version>1</version>
                                      """.trimIndent())
    maven.updateAll(maven.projectPom)
    var roots = maven.tree.rootProjects
    val parentNode = roots[0]
    val childNode = maven.tree.getModules(roots[0])[0]
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project1</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m</module>
                       </modules>
                       """.trimIndent())
    maven.updateModulePom("m", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      """.trimIndent())
    maven.updateAll(maven.projectPom)
    roots = maven.tree.rootProjects
    assertEquals(1, roots.size)
    assertEquals(1, maven.tree.getModules(roots[0]).size)
    val parentNode1 = roots[0]
    val childNode1 = maven.tree.getModules(roots[0])[0]
    assertSame(parentNode, parentNode1)
    assertSame(childNode, childNode1)
    assertEquals("project1", parentNode1.mavenId.artifactId)
    assertEquals("m1", childNode1.mavenId.artifactId)
  }

  @Test
  fun testForceUpdatingWholeModel() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m</module>
                       </modules>
                       """.trimIndent())
    val m = maven.createModulePom("m",
                            """
                                      <groupId>test</groupId>
                                      <artifactId>m</artifactId>
                                      <version>1</version>
                                      """.trimIndent())
    val l = MyLoggingListener()
    maven.project.messageBus.connect(maven.testRootDisposable).subscribe(MavenProjectsTree.Listener.TOPIC, l)
    maven.updateAll(maven.projectPom)
    assertEquals(log().add("updated", "project", "m").add("deleted"), l.log)
    l.log.clear()
    maven.tree.updateAll(listOf(maven.projectPom), false, maven.mavenGeneralSettings, MavenExplicitProfiles.NONE, maven.mavenEmbedderWrappers, maven.rawProgressReporter)
    assertEquals(log(), l.log)
    l.log.clear()
    maven.tree.updateAll(listOf(maven.projectPom), true, maven.mavenGeneralSettings, MavenExplicitProfiles.NONE, maven.mavenEmbedderWrappers, maven.rawProgressReporter)
    assertEquals(log().add("updated", "project", "m").add("deleted"), l.log)
  }

  @Test
  fun testForceUpdatingSingleProject() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m</module>
                       </modules>
                       """.trimIndent())
    val m = maven.createModulePom("m",
                            """
                                      <groupId>test</groupId>
                                      <artifactId>m</artifactId>
                                      <version>1</version>
                                      """.trimIndent())
    val l = MyLoggingListener()
    maven.project.messageBus.connect(maven.testRootDisposable).subscribe(MavenProjectsTree.Listener.TOPIC, l)
    maven.update(maven.projectPom)
    assertEquals(log().add("updated", "project", "m").add("deleted"), l.log)
    l.log.clear()
    maven.tree.update(listOf(maven.projectPom), false, maven.mavenGeneralSettings, MavenExplicitProfiles.NONE, maven.mavenEmbedderWrappers, maven.rawProgressReporter)
    assertEquals(log(), l.log)
    l.log.clear()
    maven.tree.update(listOf(maven.projectPom), true, maven.mavenGeneralSettings, MavenExplicitProfiles.NONE, maven.mavenEmbedderWrappers, maven.rawProgressReporter)
    assertEquals(log().add("updated", "project").add("deleted"), l.log)
    l.log.clear()
  }

  @Test
  fun testUpdatingModelWithNewProfiles() = runBlocking {
    maven.assumeModel_4_0_0("IDEA-379195")
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <profiles>
                         <profile>
                           <id>one</id>
                           <modules>
                             <module>m1</module>
                           </modules>
                         </profile>
                         <profile>
                           <id>two</id>
                           <modules>
                             <module>m2</module>
                           </modules>
                         </profile>
                       </profiles>
                       """.trimIndent())
    val m1 = maven.createModulePom("m1",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m1</artifactId>
                                       <version>1</version>
                                       """.trimIndent())
    val m2 = maven.createModulePom("m2",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m2</artifactId>
                                       <version>1</version>
                                       """.trimIndent())
    maven.importProjectWithProfiles("one")
    var roots = maven.tree.rootProjects
    assertEquals(1, roots.size, "should be one root project")
    assertEquals(maven.projectPom, roots[0].file, "should be one root project and it should be ${maven.projectPom}")
    assertEquals(1, maven.tree.getModules(roots[0]).size, "this project should have one subproject")
    assertEquals(m1, maven.tree.getModules(roots[0])[0].file, "this project should have one subproject, and this subproject should be m1")
    maven.projectsManager.explicitProfiles = MavenExplicitProfiles(listOf("two"))
    maven.updateAllProjects()
    roots = maven.tree.rootProjects
    assertEquals(1, roots.size, "after reimport should be one root project")
    assertEquals(maven.projectPom, roots[0].file, "after reimport should be one root project and it should be ${maven.projectPom}")
    assertEquals(1, maven.tree.getModules(roots[0]).size, "after reimport this project should have one subproject")
    assertEquals(m2,
                 maven.tree.getModules(roots[0])[0].file,
                 "after reimport this project should have one subproject, and this subproject should be m2")
  }

  @Test
  fun testUpdatingParticularProject() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m</module>
                       </modules>
                       """.trimIndent())
    val m = maven.createModulePom("m",
                            """
                                      <groupId>test</groupId>
                                      <artifactId>m</artifactId>
                                      <version>1</version>
                                      """.trimIndent())
    maven.updateAll(maven.projectPom)
    maven.updateModulePom("m", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      """.trimIndent())
    maven.update(m)
    val n = maven.tree.findProject(m)
    assertEquals("m1", n!!.mavenId.artifactId)
  }

  @Test
  fun testUpdatingInheritance() = runBlocking {
    maven.assumeModel_4_0_0("4.1.0 model does not allow such case: - [FATAL] 'artifactId' contains an expression but should be a constant")
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <properties>
                         <childName>child</childName>
                       </properties>
                       """.trimIndent())
    val child = maven.createModulePom("child",
                                """
                                          <groupId>test</groupId>
                                          <artifactId>${'$'}{childName}</artifactId>
                                          <version>1</version>
                                          <parent>
                                            <groupId>test</groupId>
                                            <artifactId>parent</artifactId>
                                            <version>1</version>
                                          </parent>
                                          """.trimIndent())
    maven.importProjectsAsync(maven.projectPom, child)
    assertEquals("child", maven.tree.findProject(child)!!.mavenId.artifactId)
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <properties>
                         <childName>child2</childName>
                       </properties>
                       """.trimIndent())
    maven.updateAllProjects()
    assertEquals("child2", maven.tree.findProject(child)!!.mavenId.artifactId)
  }

  @Test
  fun testUpdatingInheritanceHierarhically() = runBlocking {
    maven.assumeModel_4_0_0("4.1.0 model does not allow such case: - [FATAL] 'artifactId' contains an expression but should be a constant")
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <properties>
                         <subChildName>subChild</subChildName>
                       </properties>
                       """.trimIndent())
    val child = maven.createModulePom("child",
                                """
                                          <groupId>test</groupId>
                                          <artifactId>child</artifactId>
                                          <version>1</version>
                                          <packaging>pom</packaging>
                                          <parent>
                                            <groupId>test</groupId>
                                            <artifactId>parent</artifactId>
                                            <version>1</version>
                                          </parent>
                                          """.trimIndent())
    val subChild = maven.createModulePom("subChild",
                                   """
                                             <groupId>test</groupId>
                                             <artifactId>${'$'}{subChildName}</artifactId>
                                             <version>1</version>
                                             <parent>
                                               <groupId>test</groupId>
                                               <artifactId>child</artifactId>
                                               <version>1</version>
                                             </parent>
                                             """.trimIndent())
    maven.importProjectsAsync(maven.projectPom, child, subChild)
    assertEquals("subChild", maven.tree.findProject(subChild)!!.mavenId.artifactId)
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <properties>
                         <subChildName>subChild2</subChildName>
                       </properties>
                       """.trimIndent())
    maven.updateAllProjects()
    assertEquals("subChild2", maven.tree.findProject(subChild)!!.mavenId.artifactId)
  }

  @Test
  fun testSendingNotificationAfterProjectIsAddedInToHierarchy() = runBlocking {
    val listener = MyLoggingListener()
    maven.project.messageBus.connect(maven.testRootDisposable).subscribe(MavenProjectsTree.Listener.TOPIC, listener)
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>m1</artifactId>
                       <version>1</version>
                       """.trimIndent())
    maven.updateAll(maven.projectPom)
    assertEquals(log().add("updated", "m1").add("deleted"), listener.log)
  }

  @Test
  fun testSendingNotificationsWhenResolveFailed() = runBlocking {
    val p = maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name
                       """.trimIndent())
    maven.updateAll(maven.projectPom)
    val listener = MyLoggingListener()
    maven.project.messageBus.connect(maven.testRootDisposable).subscribe(MavenProjectsTree.Listener.TOPIC, listener)
    val mavenProject = maven.tree.findProject(maven.projectPom)!!
    maven.resolve(maven.project, mavenProject, maven.mavenGeneralSettings)
    assertEquals(log().add("resolved", "project"), listener.log)
    maven.projectsManager.state.originalFiles = listOf(p.path)
    maven.updateAllProjects()
    assertFalse(mavenProject.problems.isEmpty())
  }

  @Test
  fun testAddingInheritanceParent() = runBlocking {
    maven.assumeModel_4_0_0("4.1.0 model does not allow such case: - [FATAL] 'artifactId' contains an expression but should be a constant")
    val child = maven.createModulePom("child",
                                """
                                          <groupId>test</groupId>
                                          <artifactId>${'$'}{childName}</artifactId>
                                          <version>1</version>
                                          <parent>
                                            <groupId>test</groupId>
                                            <artifactId>parent</artifactId>
                                            <version>1</version>
                                          </parent>
                                          """.trimIndent())
    maven.importProjectsAsync(child)
    assertEquals("\${childName}", maven.tree.findProject(child)!!.mavenId.artifactId)
    val parent = maven.createModulePom("parent",
                                 """
                                           <groupId>test</groupId>
                                           <artifactId>parent</artifactId>
                                           <version>1</version>
                                           <properties>
                                             <childName>child</childName>
                                           </properties>
                                           """.trimIndent())
    maven.waitForImportWithinTimeout {
      maven.projectsManager.addManagedFiles(listOf(parent))
    }
    assertEquals("child", maven.tree.findProject(child)!!.mavenId.artifactId)
  }

  @Test
  fun testAddingInheritanceChild() = runBlocking {
    maven.assumeModel_4_0_0("4.1.0 model does not allow such case: - [FATAL] 'artifactId' contains an expression but should be a constant")
    val parent = maven.createModulePom("parent",
                                 """
                                           <groupId>test</groupId>
                                           <artifactId>parent</artifactId>
                                           <version>1</version>
                                           <properties>
                                             <childName>child</childName>
                                           </properties>
                                           """.trimIndent())
    maven.importProjectsAsync(parent)
    val child = maven.createModulePom("child",
                                """
                                          <groupId>test</groupId>
                                          <artifactId>${'$'}{childName}</artifactId>
                                          <version>1</version>
                                          <parent>
                                            <groupId>test</groupId>
                                            <artifactId>parent</artifactId>
                                            <version>1</version>
                                          </parent>
                                          """.trimIndent())
    maven.waitForImportWithinTimeout {
      maven.projectsManager.addManagedFiles(listOf(child))
    }
    assertEquals("child", maven.tree.findProject(child)!!.mavenId.artifactId)
  }

  @Test
  fun testParentPropertyInterpolation() = runBlocking {
    maven.assumeModel_4_0_0("4.1.0 model does not allow such case: - [FATAL] 'artifactId' contains an expression but should be a constant")
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <properties>
                         <childName>child</childName>
                       </properties>
                       """.trimIndent())
    maven.importProjectAsync()
    val child = maven.createModulePom("child",
                                """
                                          <groupId>test</groupId>
                                          <artifactId>${'$'}{childName}</artifactId>
                                          <version>1</version>
                                          <parent>
                                            <groupId>test</groupId>
                                            <artifactId>parent</artifactId>
                                            <version>1</version>
                                          </parent>
                                          """.trimIndent())
    maven.waitForImportWithinTimeout {
      maven.projectsManager.addManagedFiles(listOf(child))
    }
    assertEquals("child", maven.tree.findProject(child)!!.mavenId.artifactId)
  }

  @Test
  fun testAddingInheritanceChildOnParentUpdate() = runBlocking {
    maven.assumeModel_4_0_0("4.1.0 model does not allow such case: - [FATAL] 'artifactId' contains an expression but should be a constant")
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <properties>
                         <childName>child</childName>
                       </properties>
                       <modules>
                         <module>child</module>
                       </modules>
                       """.trimIndent())
    maven.importProjectsAsync()
    assertEmpty(maven.tree.projects)
    val child = maven.createModulePom("child",
                                """
                                          <groupId>test</groupId>
                                          <artifactId>${'$'}{childName}</artifactId>
                                          <version>1</version>
                                          <parent>
                                            <groupId>test</groupId>
                                            <artifactId>parent</artifactId>
                                            <version>1</version>
                                          </parent>
                                          """.trimIndent())
    maven.importProjectsAsync(child)
    assertEquals("child", maven.tree.findProject(child)!!.mavenId.artifactId)
  }

  @Test
  fun testDoNotReAddInheritanceChildOnParentModulesRemoval() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <modules>
                        <module>child</module>
                       </modules>
                       """.trimIndent())
    val child = maven.createModulePom("child",
                                """
                                          <groupId>test</groupId>
                                          <artifactId>child</artifactId>
                                          <version>1</version>
                                          <parent>
                                            <groupId>test</groupId>
                                            <artifactId>parent</artifactId>
                                            <version>1</version>
                                          </parent>
                                          """.trimIndent())
    maven.updateAll(maven.projectPom)
    var roots = maven.tree.rootProjects
    assertEquals(1, roots.size)
    assertEquals(maven.projectPom, roots[0].file)
    assertEquals(1, maven.tree.getModules(roots[0]).size)
    assertEquals(child, maven.tree.getModules(roots[0])[0].file)
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       """.trimIndent())
    maven.update(maven.projectPom)
    roots = maven.tree.rootProjects
    assertEquals(1, roots.size)
    assertEquals(maven.projectPom, roots[0].file)
    assertEquals(0, maven.tree.getModules(roots[0]).size)
  }

  @Test
  fun testChangingInheritance() = runBlocking {
    maven.assumeModel_4_0_0("4.1.0 model does not allow such case: - [FATAL] 'artifactId' contains an expression but should be a constant")
    val parent1 = maven.createModulePom("parent1",
                                  """
                                            <groupId>test</groupId>
                                            <artifactId>parent1</artifactId>
                                            <version>1</version>
                                            <properties>
                                              <childName>child1</childName>
                                            </properties>
                                            """.trimIndent())
    val parent2 = maven.createModulePom("parent2",
                                  """
                                            <groupId>test</groupId>
                                            <artifactId>parent2</artifactId>
                                            <version>1</version>
                                            <properties>
                                              <childName>child2</childName>
                                            </properties>
                                            """.trimIndent())
    val child = maven.createModulePom("child",
                                """
                                          <groupId>test</groupId>
                                          <artifactId>${'$'}{childName}</artifactId>
                                          <version>1</version>
                                          <parent>
                                            <groupId>test</groupId>
                                            <artifactId>parent1</artifactId>
                                            <version>1</version>
                                          </parent>
                                          """.trimIndent())
    maven.importProjectsAsync(parent1, parent2, child)
    assertEquals("child1", maven.tree.findProject(child)!!.mavenId.artifactId)
    maven.updateModulePom("child", """
      <groupId>test</groupId>
      <artifactId>${'$'}{childName}</artifactId>
      <version>1</version>
      <parent>
        <groupId>test</groupId>
        <artifactId>parent2</artifactId>
        <version>1</version>
      </parent>
      """.trimIndent())
    maven.updateAllProjects()
    assertEquals("child2", maven.tree.findProject(child)!!.mavenId.artifactId)
  }

  @Test
  fun testChangingInheritanceParentId() = runBlocking {
    maven.assumeModel_4_0_0("4.1.0 model does not allow such case: - [FATAL] 'artifactId' contains an expression but should be a constant")
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <properties>
                         <childName>child</childName>
                       </properties>
                       """.trimIndent())
    val child = maven.createModulePom("child",
                                """
                                          <groupId>test</groupId>
                                          <artifactId>${'$'}{childName}</artifactId>
                                          <version>1</version>
                                          <parent>
                                            <groupId>test</groupId>
                                            <artifactId>parent</artifactId>
                                            <version>1</version>
                                          </parent>
                                          """.trimIndent())
    maven.importProjectsAsync(maven.projectPom, child)
    assertEquals("child", maven.tree.findProject(child)!!.mavenId.artifactId)
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent2</artifactId>
                       <version>1</version>
                       <properties>
                         <childName>child</childName>
                       </properties>
                       """.trimIndent())
    maven.updateAllProjects()
    assertEquals("\${childName}", maven.tree.findProject(child)!!.mavenId.artifactId)
  }

  @Test
  fun testHandlingSelfInheritance() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <parent>
                         <groupId>test</groupId>
                         <artifactId>parent</artifactId>
                         <version>1</version>
                       </parent>
                       """.trimIndent())
    maven.updateAll(maven.projectPom) // shouldn't hang
    maven.updateTimestamps(maven.projectPom)
    maven.update(maven.projectPom) // shouldn't hang
    maven.updateTimestamps(maven.projectPom)
    maven.updateAll(maven.projectPom) // shouldn't hang
  }

  @Test
  fun testHandlingRecursiveInheritance() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <parent>
                         <groupId>test</groupId>
                         <artifactId>child</artifactId>
                         <version>1</version>
                       </parent>
                       <modules>
                         <module>child</module>
                       </properties>
                       """.trimIndent())
    val child = maven.createModulePom("child",
                                """
                                          <groupId>test</groupId>
                                          <artifactId>child</artifactId>
                                          <version>1</version>
                                          <parent>
                                            <groupId>test</groupId>
                                            <artifactId>parent</artifactId>
                                            <version>1</version>
                                          </parent>
                                          """.trimIndent())
    maven.updateAll(maven.projectPom, child) // shouldn't hang
    maven.updateTimestamps(maven.projectPom, child)
    maven.update(maven.projectPom) // shouldn't hang
    maven.updateTimestamps(maven.projectPom, child)
    maven.update(child) // shouldn't hang
    maven.updateTimestamps(maven.projectPom, child)
    maven.updateAll(maven.projectPom, child) // shouldn't hang
  }

  @Test
  fun testDeletingInheritanceParent() = runBlocking {
    maven.assumeModel_4_0_0("4.1.0 model does not allow such case: - [FATAL] 'artifactId' contains an expression but should be a constant")
    val parent = maven.createModulePom("parent",
                                 """
                                           <groupId>test</groupId>
                                           <artifactId>parent</artifactId>
                                           <version>1</version>
                                           <properties>
                                             <childName>child</childName>
                                           </properties>
                                           """.trimIndent())
    val child = maven.createModulePom("child",
                                """
                                          <groupId>test</groupId>
                                          <artifactId>${'$'}{childName}</artifactId>
                                          <version>1</version>
                                          <parent>
                                            <groupId>test</groupId>
                                            <artifactId>parent</artifactId>
                                            <version>1</version>
                                          </parent>
                                          """.trimIndent())
    maven.importProjectsAsync(parent, child)
    assertEquals("child", maven.tree.findProject(child)!!.mavenId.artifactId)
    VfsTestUtil.deleteFile(parent)
    maven.updateAllProjects()
    assertEquals("\${childName}", maven.tree.findProject(child)!!.mavenId.artifactId)
  }

  @Test
  fun testDeletingInheritanceChild() = runBlocking {
    maven.assumeModel_4_0_0("4.1.0 model does not allow such case: - [FATAL] 'artifactId' contains an expression but should be a constant")
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <properties>
                         <subChildName>subChild</subChildName>
                       </properties>
                       """.trimIndent())
    val child = maven.createModulePom("child",
                                """
                                          <groupId>test</groupId>
                                          <artifactId>child</artifactId>
                                          <version>1</version>
                                          <packaging>pom</packaging>
                                          <parent>
                                            <groupId>test</groupId>
                                            <artifactId>parent</artifactId>
                                            <version>1</version>
                                          </parent>
                                          """.trimIndent())
    val subChild = maven.createModulePom("subChild",
                                   """
                                             <groupId>test</groupId>
                                             <artifactId>${'$'}{subChildName}</artifactId>
                                             <version>1</version>
                                             <parent>
                                               <groupId>test</groupId>
                                               <artifactId>child</artifactId>
                                               <version>1</version>
                                             </parent>
                                             """.trimIndent())
    maven.importProjectsAsync(maven.projectPom, child, subChild)
    assertEquals("subChild", maven.tree.findProject(subChild)!!.mavenId.artifactId)
    maven.deleteProject(child)
    assertEquals("\${subChildName}", maven.tree.findProject(subChild)!!.mavenId.artifactId)
  }


  @Test
  fun testRecursiveInheritanceAndAggregation() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <parent>
                         <groupId>test</groupId>
                         <artifactId>child</artifactId>
                         <version>1</version>
                       </parent>
                       <modules>
                        <module>child</module>
                       </modules>
                       """.trimIndent())
    val child = maven.createModulePom("child",
                                """
                                          <groupId>test</groupId>
                                          <artifactId>child</artifactId>
                                          <version>1</version>
                                          """.trimIndent())
    maven.updateAll(maven.projectPom) // should not recurse
    maven.updateTimestamps(maven.projectPom, child)
    maven.updateAll(child) // should not recurse
  }

  @Test
  fun testUpdatingAddsModules() = runBlocking {
    maven.assumeModel_4_0_0("4.1.0 model does not allow such case: - [FATAL] 'artifactId' contains an expression but should be a constant")
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       """.trimIndent())
    val m = maven.createModulePom("m",
                            """
                                      <groupId>test</groupId>
                                      <artifactId>m</artifactId>
                                      <version>1</version>
                                      """.trimIndent())
    maven.updateAll(maven.projectPom)
    var roots = maven.tree.rootProjects
    assertEquals(1, roots.size)
    assertEquals(maven.projectPom, roots[0].file)
    assertEquals(0, maven.tree.getModules(roots[0]).size)
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m</module>
                       </modules>
                       """.trimIndent())
    maven.update(maven.projectPom)
    roots = maven.tree.rootProjects
    assertEquals(1, roots.size)
    assertEquals(maven.projectPom, roots[0].file)
    assertEquals(1, maven.tree.getModules(roots[0]).size)
    assertEquals(m, maven.tree.getModules(roots[0])[0].file)
  }

  @Test
  fun testUpdatingUpdatesModulesIfProjectIsChanged() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m</module>
                       </modules>
                       """.trimIndent())
    val m = maven.createModulePom("m",
                            """
                                      <groupId>test</groupId>
                                      <artifactId>m</artifactId>
                                      <version>1</version>
                                      """.trimIndent())
    maven.updateAll(maven.projectPom)
    assertEquals("m", maven.tree.findProject(m)!!.mavenId.artifactId)
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <name>foo</name>
                       <modules>
                         <module>m</module>
                       </modules>
                       """.trimIndent())
    maven.updateModulePom("m", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      """.trimIndent())
    maven.update(maven.projectPom)
    assertEquals("m2", maven.tree.findProject(m)!!.mavenId.artifactId)
  }

  @Test
  fun testUpdatingDoesNotUpdateModulesIfProjectIsNotChanged() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m</module>
                       </modules>
                       """.trimIndent())
    val m = maven.createModulePom("m",
                            """
                                      <groupId>test</groupId>
                                      <artifactId>m</artifactId>
                                      <version>1</version>
                                      """.trimIndent())
    maven.updateAll(maven.projectPom)
    assertEquals("m", maven.tree.findProject(m)!!.mavenId.artifactId)
    maven.createModulePom("m", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      """.trimIndent())
    maven.update(maven.projectPom)

    // did not change
    assertEquals("m", maven.tree.findProject(m)!!.mavenId.artifactId)
  }

  @Test
  fun testAddingProjectAsModuleToExistingOne() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m</module>
                       </modules>
                       """.trimIndent())
    maven.updateAll(maven.projectPom)
    var roots = maven.tree.rootProjects
    assertEquals(1, roots.size)
    assertEquals(0, maven.tree.getModules(roots[0]).size)
    val m = maven.createModulePom("m",
                            """
                                      <groupId>test</groupId>
                                      <artifactId>m</artifactId>
                                      <version>1</version>
                                      """.trimIndent())
    maven.update(m)
    roots = maven.tree.rootProjects
    assertEquals(1, roots.size)
    assertEquals(1, maven.tree.getModules(roots[0]).size)
    assertEquals(m, maven.tree.getModules(roots[0])[0].file)
  }

  @Test
  fun testAddingProjectAsAggregatorForExistingOne() = runBlocking {
    val m = maven.createModulePom("m",
                            """
                                      <groupId>test</groupId>
                                      <artifactId>m</artifactId>
                                      <version>1</version>
                                      """.trimIndent())
    maven.updateAll(m)
    var roots = maven.tree.rootProjects
    assertEquals(1, roots.size)
    assertEquals(m, roots[0].file)
    assertEquals(0, maven.tree.getModules(roots[0]).size)
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m</module>
                       </modules>
                       """.trimIndent())
    maven.update(maven.projectPom)
    roots = maven.tree.rootProjects
    assertEquals(1, roots.size)
    assertEquals(maven.projectPom, roots[0].file)
    assertEquals(1, maven.tree.getModules(roots[0]).size)
    assertEquals(m, maven.tree.getModules(roots[0])[0].file)
  }

  @Test
  fun testAddingProjectWithModules() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       """.trimIndent())
    maven.updateAll(maven.projectPom)
    var roots = maven.tree.rootProjects
    assertEquals(1, roots.size)
    assertEquals(0, maven.tree.getModules(roots[0]).size)
    val m1 = maven.createModulePom("m1",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m1</artifactId>
                                       <version>1</version>
                                       <packaging>pom</packaging>
                                       <modules>
                                         <module>m2</module>
                                       </modules>
                                       """.trimIndent())
    val m2 = maven.createModulePom("m1/m2",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m2</artifactId>
                                       <version>1</version>
                                       """.trimIndent())
    maven.update(m1)
    roots = maven.tree.rootProjects
    assertEquals(2, roots.size)
    assertEquals(maven.projectPom, roots[0].file)
    assertEquals(m1, roots[1].file)
    assertEquals(1, maven.tree.getModules(roots[1]).size)
    assertEquals(m2, maven.tree.getModules(roots[1])[0].file)
  }

  @Test
  fun testUpdatingAddsModulesFromRootProjects() = runBlocking {
    maven.assumeModel_4_0_0("IDEA-379195")
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       """.trimIndent())
    val m = maven.createModulePom("m",
                            """
                                      <groupId>test</groupId>
                                      <artifactId>m</artifactId>
                                      <version>1</version>
                                      """.trimIndent())
    maven.updateAll(maven.projectPom, m)
    var roots = maven.tree.rootProjects
    assertEquals(2, roots.size)
    assertEquals(maven.projectPom, roots[0].file)
    assertEquals(m, roots[1].file)
    assertEquals("m", roots[1].mavenId.artifactId)
    assertEquals(0, maven.tree.getModules(roots[0]).size)
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m</module>
                       </modules>
                       """.trimIndent())
    maven.update(maven.projectPom)
    roots = maven.tree.rootProjects
    assertEquals(1, roots.size)
    assertEquals(maven.projectPom, roots[0].file)
    assertEquals(1, maven.tree.getModules(roots[0]).size)
    assertEquals(m, maven.tree.getModules(roots[0])[0].file)
  }

  @Test
  fun testMovingModuleToRootsWhenAggregationChanged() = runBlocking {
    maven.assumeModel_4_0_0("4.1.0 model does not allow such case: - [FATAL] 'artifactId' contains an expression but should be a constant")
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m</module>
                       </modules>
                       """.trimIndent())
    val m = maven.createModulePom("m",
                            """
                                      <groupId>test</groupId>
                                      <artifactId>m</artifactId>
                                      <version>1</version>
                                      """.trimIndent())
    maven.updateAll(maven.projectPom, m)
    var roots = maven.tree.rootProjects
    assertEquals(1, roots.size)
    assertEquals(1, maven.tree.getModules(roots[0]).size)
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       """.trimIndent())
    maven.updateAll(maven.projectPom, m)
    roots = maven.tree.rootProjects
    assertEquals(2, roots.size)
    assertTrue(maven.tree.getModules(roots[0]).isEmpty())
    assertTrue(maven.tree.getModules(roots[1]).isEmpty())
  }

  @Test
  fun testDeletingProject() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m</module>
                       </modules>
                       """.trimIndent())
    val m = maven.createModulePom("m",
                            """
                                      <groupId>test</groupId>
                                      <artifactId>m</artifactId>
                                      <version>1</version>
                                      """.trimIndent())
    maven.updateAll(maven.projectPom)
    var roots = maven.tree.rootProjects
    assertEquals(1, roots.size)
    assertEquals(1, maven.tree.getModules(roots[0]).size)
    maven.deleteProject(m)
    roots = maven.tree.rootProjects
    assertEquals(1, roots.size)
    assertEquals(0, maven.tree.getModules(roots[0]).size)
  }

  @Test
  fun testDeletingProjectWithModules() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m1</module>
                       </modules>
                       """.trimIndent())
    val m1 = maven.createModulePom("m1",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m1</artifactId>
                                       <version>1</version>
                                       <packaging>pom</packaging>
                                       <modules>
                                         <module>m2</module>
                                       </modules>
                                       """.trimIndent())
    maven.createModulePom("m1/m2",
                    """
                      <groupId>test</groupId>
                      <artifactId>m2</artifactId>
                      <version>1</version>
                      """.trimIndent())
    maven.updateAll(maven.projectPom)
    var roots = maven.tree.rootProjects
    assertEquals(1, roots.size)
    assertEquals(1, maven.tree.getModules(roots[0]).size)
    assertEquals(1, maven.tree.getModules(maven.tree.getModules(roots[0])[0]).size)
    maven.deleteProject(m1)
    roots = maven.tree.rootProjects
    assertEquals(1, roots.size)
    assertEquals(maven.projectPom, roots[0].file)
    assertEquals(0, maven.tree.getModules(roots[0]).size)
  }

  @Test
  fun testSendingNotificationsWhenProjectDeleted() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m1</module>
                       </modules>
                       """.trimIndent())
    val m1 = maven.createModulePom("m1",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m1</artifactId>
                                       <version>1</version>
                                       <packaging>pom</packaging>
                                       <modules>
                                         <module>m2</module>
                                       </modules>
                                       """.trimIndent())
    maven.createModulePom("m1/m2",
                    """
                      <groupId>test</groupId>
                      <artifactId>m2</artifactId>
                      <version>1</version>
                      """.trimIndent())
    maven.updateAll(maven.projectPom)
    val listener = MyLoggingListener()
    maven.project.messageBus.connect(maven.testRootDisposable).subscribe(MavenProjectsTree.Listener.TOPIC, listener)
    maven.deleteProject(m1)
    assertEquals(log().add("updated").add("deleted", "m2", "m1"), listener.log)
  }

  @Test
  fun testReconnectModuleOfDeletedProjectIfModuleIsManaged() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m1</module>
                       </modules>
                       """.trimIndent())
    val m1 = maven.createModulePom("m1",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m1</artifactId>
                                       <version>1</version>
                                       <packaging>pom</packaging>
                                       <modules>
                                         <module>m2</module>
                                       </modules>
                                       """.trimIndent())
    val m2 = maven.createModulePom("m1/m2",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m2</artifactId>
                                       <version>1</version>
                                       """.trimIndent())
    maven.updateAll(maven.projectPom, m2)
    var roots = maven.tree.rootProjects
    assertEquals(1, roots.size)
    assertEquals(1, maven.tree.getModules(roots[0]).size)
    assertEquals(1, maven.tree.getModules(maven.tree.getModules(roots[0])[0]).size)
    maven.deleteProject(m1)
    maven.updateAll(maven.projectPom, m2)
    roots = maven.tree.rootProjects
    assertEquals(1, roots.size)
    assertEquals(1, maven.tree.getModules(roots[0]).size)
    assertEquals(1, maven.tree.getModules(maven.tree.getModules(roots[0])[0]).size)
  }

  @Test
  fun testAddingProjectsOnUpdateAllWhenManagedFilesChanged() = runBlocking {
    val m1 = maven.createModulePom("m1",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m1</artifactId>
                                       <version>1</version>
                                       """.trimIndent())
    val m2 = maven.createModulePom("m2",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m2</artifactId>
                                       <version>1</version>
                                       """.trimIndent())
    val m3 = maven.createModulePom("m3",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m3</artifactId>
                                       <version>1</version>
                                       """.trimIndent())
    maven.updateAll(m1, m2)
    assertEquals(2, maven.tree.rootProjects.size)
    maven.updateAll(m1, m2, m3)
    assertEquals(3, maven.tree.rootProjects.size)
  }

  @Test
  fun testDeletingProjectsOnUpdateAllWhenManagedFilesChanged() = runBlocking {
    val m1 = maven.createModulePom("m1",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m1</artifactId>
                                       <version>1</version>
                                       """.trimIndent())
    val m2 = maven.createModulePom("m2",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m2</artifactId>
                                       <version>1</version>
                                       """.trimIndent())
    val m3 = maven.createModulePom("m3",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m3</artifactId>
                                       <version>1</version>
                                       """.trimIndent())
    maven.updateAll(m1, m2, m3)
    assertEquals(3, maven.tree.rootProjects.size)
    maven.updateAll(m1, m2)
    assertEquals(2, maven.tree.rootProjects.size)
  }

  @Test
  fun testSendingNotificationsWhenAddingOrDeletingManagedFiles() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())
    val m1 = maven.createModulePom("m1",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m1</artifactId>
                                       <version>1</version>
                                       """.trimIndent())
    val m2 = maven.createModulePom("m2",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m2</artifactId>
                                       <version>1</version>
                                       """.trimIndent())
    val l = MyLoggingListener()
    maven.project.messageBus.connect(maven.testRootDisposable).subscribe(MavenProjectsTree.Listener.TOPIC, l)
    maven.tree.updateAll(listOf(maven.projectPom), false, maven.mavenGeneralSettings, MavenExplicitProfiles.NONE, maven.mavenEmbedderWrappers, maven.rawProgressReporter)
    assertEquals(log().add("updated", "parent", "m1", "m2").add("deleted"), l.log)
    l.log.clear()
    maven.tree.updateAll(emptyList(), false, maven.mavenGeneralSettings, MavenExplicitProfiles.NONE, maven.mavenEmbedderWrappers, maven.rawProgressReporter)
    assertEquals(log().add("updated").add("deleted", "m1", "m2", "parent"), l.log)
  }

  @Test
  fun testUpdatingModelWhenActiveProfilesChange() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <profiles>
                         <profile>
                           <id>one</id>
                           <properties>
                             <prop>value1</prop>
                           </properties>
                         </profile>
                         <profile>
                           <id>two</id>
                           <properties>
                             <prop>value2</prop>
                           </properties>
                         </profile>
                       </profiles>
                       <modules>
                         <module>m</module>
                       </modules>
                       <build>
                         <sourceDirectory>${'$'}{prop}</sourceDirectory>
                       </build>
                       """.trimIndent())
    maven.createModulePom("m",
                    """
                      <groupId>test</groupId>
                      <artifactId>m</artifactId>
                      <version>1</version>
                      <parent>
                        <groupId>test</groupId>
                        <artifactId>project</artifactId>
                        <version>1</version>
                      </parent>
                      <build>
                        <sourceDirectory>${'$'}{prop}</sourceDirectory>
                      </build>
                      """.trimIndent())
    maven.importProjectWithProfiles("one")
    val roots = maven.tree.rootProjects
    val parentNode = roots[0]
    val childNode = maven.tree.getModules(roots[0])[0]
    assertUnorderedPathsAreEqual(parentNode.sources, Arrays.asList(FileUtil.toSystemDependentName(
      "${maven.projectPath}/value1")))
    assertUnorderedPathsAreEqual(childNode.sources, Arrays.asList(FileUtil.toSystemDependentName(
      "${maven.projectPath}/m/value1")))

    maven.projectsManager.explicitProfiles = MavenExplicitProfiles(listOf("two"))
    maven.updateAllProjects()
    assertUnorderedPathsAreEqual(parentNode.sources, Arrays.asList(FileUtil.toSystemDependentName(
      "${maven.projectPath}/value2")))
    assertUnorderedPathsAreEqual(childNode.sources, Arrays.asList(FileUtil.toSystemDependentName(
      "${maven.projectPath}/m/value2")))
  }

  @Test
  fun testDoNotUpdateModelWhenAggregatorProfilesXmlChange() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m</module>
                       </modules>
                       """.trimIndent())
    maven.createModulePom("m",
                    """
                      <groupId>test</groupId>
                      <artifactId>m</artifactId>
                      <version>1</version>
                      <build>
                        <sourceDirectory>${'$'}{prop}</sourceDirectory>
                      </build>
                      """.trimIndent())
    maven.createProfilesXmlOldStyle("""
                                <profile>
                                 <id>one</id>
                                  <activation>
                                    <activeByDefault>true</activeByDefault>
                                  </activation>
                                  <properties>
                                    <prop>value1</prop>
                                  </properties>
                                </profile>
                                """.trimIndent())
    maven.updateAll(maven.projectPom)
    maven.createProfilesXmlOldStyle("""
                                <profile>
                                  <id>one</id>
                                  <activation>
                                    <activeByDefault>true</activeByDefault>
                                  </activation>
                                  <properties>
                                 <prop>value2</prop>
                                  </properties>
                                </profile>
                                """.trimIndent())
    maven.updateAll(maven.projectPom)
    val obsoleteFiles = maven.tree.rootProjectsFiles
    assertEquals(listOf(maven.projectPom), obsoleteFiles)
  }

  @Test
  fun testSaveLoad() = runBlocking {
    //todo: move to resolver test
    // stripping down plugins
    // stripping down Xpp3Dom fields
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       <build>
                         <plugins>
                           <plugin>
                             <groupId>org.apache.maven.plugins</groupId>
                             <artifactId>maven-compiler-plugin</artifactId>
                             <configuration>
                               <source>1.4</source>
                             </configuration>
                           </plugin>
                         </plugins>
                       </build>
                       <reports>
                         <someTag/>
                       </reports>
                       """.trimIndent())
    val m1 = maven.createModulePom("m1",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m1</artifactId>
                                       <version>1</version>
                                       """.trimIndent())
    val m2 = maven.createModulePom("m2",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m2</artifactId>
                                       <version>1</version>
                                       """.trimIndent())
    maven.updateAll(maven.projectPom)
    val parentProject = maven.tree.findProject(maven.projectPom)
    maven.resolve(maven.project, parentProject!!, maven.mavenGeneralSettings)
    val f = maven.dir.resolve("tree.dat")
    maven.tree.save(f)
    val read = MavenProjectsTree(maven.project)
    read.read(f)
    val roots = read!!.rootProjects
    assertEquals(1, roots.size)
    val rootProject = roots[0]
    assertEquals(maven.projectPom, rootProject.file)
    assertEquals(2, read.getModules(rootProject).size)
    assertEquals(m1, read.getModules(rootProject)[0].file)
    assertEquals(m2, read.getModules(rootProject)[1].file)
    assertNull(read.findAggregator(rootProject))
    assertEquals(rootProject, read.findAggregator(read.findProject(m1)!!))
    assertEquals(rootProject, read.findAggregator(read.findProject(m2)!!))
  }

  @Test
  fun testCollectingProfilesFromSettingsXml() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <profiles>
                         <profile>
                           <id>one</id>
                         </profile>
                       </profiles>
                       """.trimIndent())
    maven.updateSettingsXml("""
                        <profiles>
                          <profile>
                            <id>three</id>
                          </profile>
                        </profiles>
                        """.trimIndent())
    maven.updateAll(maven.projectPom)
    assertUnorderedElementsAreEqual(maven.tree.availableProfiles, "one", "three")
  }

  @Test
  fun testCollectingProfilesFromSettingsXmlAfterResolve() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <profiles>
                         <profile>
                           <id>one</id>
                         </profile>
                       </profiles>
                       """.trimIndent())
    maven.updateSettingsXml("""
                        <profiles>
                          <profile>
                            <id>three</id>
                          </profile>
                        </profiles>
                        """.trimIndent())
    maven.updateAll(maven.projectPom)
    maven.resolve(maven.project, maven.tree.rootProjects[0], maven.mavenGeneralSettings)
    assertUnorderedElementsAreEqual(maven.tree.availableProfiles, "one", "three")
  }

  @Test
  fun testCollectingProfilesFromParentsAfterResolve() = runBlocking {
    maven.projectsManager.initForTests()
    val parent1 = maven.createModulePom("parent1",
                                  """
                      <groupId>test</groupId>
                      <artifactId>parent1</artifactId>
                      <version>1</version>
                      <packaging>pom</packaging>
                      <profiles>
                        <profile>
                          <id>parent1Profile</id>
                        </profile>
                      </profiles>
                      """.trimIndent())

    val parent2 = maven.createModulePom("parent2",
                                  """
                      <groupId>test</groupId>
                      <artifactId>parent2</artifactId>
                      <version>1</version>
                      <packaging>pom</packaging>
                      <parent>
                       <groupId>test</groupId>
                       <artifactId>parent1</artifactId>
                       <version>1</version>
                       <relativePath>../parent1/pom.xml</relativePath>
                      </parent>
                      <profiles>
                        <profile>
                          <id>parent2Profile</id>
                        </profile>
                      </profiles>
                      """.trimIndent())

    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <parent>
                        <groupId>test</groupId>
                        <artifactId>parent2</artifactId>
                        <version>1</version>
                        <relativePath>parent2/pom.xml</relativePath>
                       </parent>
                       <profiles>
                         <profile>
                           <id>projectProfile</id>
                         </profile>
                       </profiles>
                       """.trimIndent())

    maven.updateSettingsXml("""
                        <profiles>
                          <profile>
                            <id>settings</id>
                          </profile>
                        </profiles>
                        """.trimIndent())

    maven.importProjectAsync()
    maven.projectsManager.explicitProfiles = MavenExplicitProfiles(listOf("projectProfile",
                                                                    "parent1Profile",
                                                                    "parent2Profile",
                                                                    "settings",
                                                                    "xxx"))
    maven.updateAllProjects()
    val mavenProject = maven.tree.findProject(maven.projectPom)!!
    assertUnorderedElementsAreEqual(
      mavenProject.activatedProfilesIds.enabledProfiles,
      "projectProfile",
      "parent1Profile",
      "parent2Profile",
      "settings")
    maven.updateAllProjects()
    assertUnorderedElementsAreEqual(
      mavenProject.activatedProfilesIds.enabledProfiles,
      "projectProfile",
      "parent1Profile",
      "parent2Profile",
      "settings")
  }

  @Test
  fun testFindRootWithMultiLevelAggregator() = runBlocking {
    val p1 = maven.createModulePom("project1", """
      <groupId>test</groupId>
      <artifactId>project1</artifactId>
      <version>1</version>
      <packaging>pom</packaging>
      <modules>
        <module>../project2</module>
      </modules>
      """
      .trimIndent()
    )
    val p2 = maven.createModulePom("project2", """
      <groupId>test</groupId>
      <artifactId>project2</artifactId>
      <version>1</version>
      <packaging>pom</packaging>
      <modules>
        <module>../module</module>
      </modules>
      """
      .trimIndent()
    )
    val m = maven.createModulePom("module", """
      <groupId>test</groupId>
      <artifactId>module</artifactId>
      <version>1</version>
      """
      .trimIndent()
    )
    maven.updateAll(p1, p2, m)
    val roots = maven.tree.rootProjects
    assertEquals(1, roots.size)
    val p1Project = roots[0]
    assertEquals(p1, p1Project.file)
    assertEquals(p1Project, maven.tree.findRootProject(p1Project))
    assertEquals(1, maven.tree.getModules(p1Project).size)
    val p2Project = maven.tree.getModules(p1Project)[0]
    assertEquals(p2, p2Project.file)
    assertEquals(p1Project, maven.tree.findRootProject(p2Project))
    assertEquals(1, maven.tree.getModules(p2Project).size)
    val mProject = maven.tree.getModules(p2Project)[0]
    assertEquals(m, mProject.file)
    assertEquals(p1Project, maven.tree.findRootProject(mProject))
    assertEquals(0, maven.tree.getModules(mProject).size)
  }

  @Test
  fun testOutputPathsAreBasedOnTargetPathWhenResolving() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <directory>my-target</directory>
                       </build>
                       """.trimIndent())
    maven.importProjectAsync()
    val mavenProject = maven.tree.rootProjects[0]

    PlatformTestUtil.assertPathsEqual(maven.pathFromBasedir("my-target"), mavenProject.buildDirectory)
    PlatformTestUtil.assertPathsEqual(maven.pathFromBasedir("my-target/classes"), mavenProject.outputDirectory)
    PlatformTestUtil.assertPathsEqual(maven.pathFromBasedir("my-target/test-classes"), mavenProject.testOutputDirectory)
  }

  @Test
  fun testReadProjectsWithDynamicVersion() = runBlocking {
    val firstRootWithChild = maven.createModulePom("parentone",
                                             $$"""
                             <groupId>test</groupId>
                             <artifactId>parentone</artifactId>
                             <version>${version_param}</version>
                             <properties>
                                 <version_param>1.0</version_param>
                             </properties>
                             <packaging>pom</packaging>
                             <modules>
                                 <module>child</module>
                             </modules>
                             """.trimIndent())
    val child = maven.createModulePom("parentone/child",
                                $$"""
                             <parent>
                               <groupId>test</groupId>
                               <artifactId>parentone</artifactId>
                               <version>${version_param}</version>                 
                             </parent>
                              <artifactId>child</artifactId>
                             """.trimIndent())
    val m2 = maven.createModulePom("m2",
                             """
                             <groupId>test</groupId>
                             <artifactId>m2</artifactId>
                             <version>1</version>
                             """.trimIndent())

    maven.updateAll(firstRootWithChild, m2)
    val roots = maven.tree.rootProjects
    assertEquals(2, roots.size)
    assertSameElements(
      maven.tree.workspaceMap.availableIds,
      MavenId("test:parentone:1.0"),
      MavenId("test:parentone:RELEASE"),
      MavenId("test:parentone:LATEST"),

      MavenId("test:child:1.0"),
      MavenId("test:child:RELEASE"),
      MavenId("test:child:LATEST"),

      MavenId("test:m2:1"),
      MavenId("test:m2:RELEASE"),
      MavenId("test:m2:LATEST"),
    )
  }

  companion object {
    private fun collectAllModulesRecursively(tree: MavenProjectsTree, aggregator: MavenProject): List<MavenProject> {
      val directModules = ArrayList(tree.getModules(aggregator))
      val allModules = ArrayList(directModules)
      for (directModule in directModules) {
        allModules.addAll(collectAllModulesRecursively(tree, directModule))
      }
      return allModules
    }
  }
}
