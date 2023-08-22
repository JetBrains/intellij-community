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

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.idea.maven.model.MavenExplicitProfiles
import org.jetbrains.idea.maven.project.MavenEmbeddersManager
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectChanges
import org.jetbrains.idea.maven.project.MavenProjectsTree
import org.jetbrains.idea.maven.server.NativeMavenProjectHolder
import org.junit.Test
import java.io.IOException
import java.util.*
import java.util.Set
import kotlin.collections.ArrayList
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.listOf
import kotlin.collections.map
import kotlin.collections.mutableListOf

class MavenProjectsTreeReadingTest : MavenProjectsTreeTestCase() {
  @Test
  fun testTwoRootProjects() {
    val m1 = createModulePom("m1",
                             """
                             <groupId>test</groupId>
                             <artifactId>m1</artifactId>
                             <version>1</version>
                             """.trimIndent())
    val m2 = createModulePom("m2",
                             """
                             <groupId>test</groupId>
                             <artifactId>m2</artifactId>
                             <version>1</version>
                             """.trimIndent())
    updateAll(m1, m2)
    val roots = tree.rootProjects
    assertEquals(2, roots.size)
    assertEquals(m1, roots[0].file)
    assertEquals(m2, roots[1].file)
  }

  @Test
  fun testModulesWithWhiteSpaces() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>
                         m  </module>
                       </modules>
                       """.trimIndent())
    val m = createModulePom("m",
                            """
                            <groupId>test</groupId>
                            <artifactId>m</artifactId>
                            <version>1</version>
                            """.trimIndent())
    updateAll(myProjectPom)
    val roots = tree.rootProjects
    assertEquals(1, roots.size)
    assertEquals(myProjectPom, roots[0].file)
    assertEquals(1, tree.getModules(roots[0]).size)
    assertEquals(m, tree.getModules(roots[0])[0].file)
  }

  @Test
  fun testDoNotImportChildAsRootProject() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m</module>
                       </modules>
                       """.trimIndent())
    val m = createModulePom("m",
                            """
                            <groupId>test</groupId>
                            <artifactId>m</artifactId>
                            <version>1</version>
                            """.trimIndent())
    updateAll(myProjectPom, m)
    val roots = tree.rootProjects
    assertEquals(1, roots.size)
    assertEquals(myProjectPom, roots[0].file)
    assertEquals(1, tree.getModules(roots[0]).size)
    assertEquals(m, tree.getModules(roots[0])[0].file)
  }

  @Test
  fun testDoNotImportSameRootProjectTwice() {
    val listener = MyLoggingListener()
    tree.addListener(listener, getTestRootDisposable())
    val m1 = createModulePom("m1",
                             """
                             <groupId>test</groupId>
                             <artifactId>m1</artifactId>
                             <version>1</version>
                             """.trimIndent())
    val m2 = createModulePom("m2",
                             """
                             <groupId>test</groupId>
                             <artifactId>m2</artifactId>
                             <version>1</version>
                             """.trimIndent())
    updateAll(m1, m2, m1)
    val roots = tree.rootProjects
    assertEquals(2, roots.size)
    assertEquals(m1, roots[0].file)
    assertEquals(m2, roots[1].file)
    assertEquals(log().add("updated", "m1", "m2").add("deleted"), listener.log)
  }

  @Test
  fun testRereadingChildIfParentWasReadAfterIt() {
    val listener = MyLoggingListener()
    tree.addListener(listener, getTestRootDisposable())
    val m1 = createModulePom("m1",
                             """
                             <groupId>test</groupId>
                             <artifactId>m1</artifactId>
                             <version>1</version>
                             <properties>
                              <childId>m2</childId>
                             </properties>
                             """.trimIndent())
    val m2 = createModulePom("m2",
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
    updateAll(m2, m1)
    val roots = tree.rootProjects
    assertEquals(2, roots.size)
    assertEquals(m1, roots[0].file)
    assertEquals(m2, roots[1].file)
    assertEquals("m1", roots[0].mavenId.artifactId)
    assertEquals("m2", roots[1].mavenId.artifactId)
    assertEquals(log().add("updated", "m2", "m1").add("deleted"), listener.log)
  }

  @Test
  fun testSameProjectAsModuleOfSeveralProjects() {
    val p1 = createModulePom("project1",
                             """
                             <groupId>test</groupId>
                             <artifactId>project1</artifactId>
                             <version>1</version>
                             <packaging>pom</packaging>
                             <modules>
                               <module>../module</module>
                             </modules>
                             """.trimIndent())
    val p2 = createModulePom("project2",
                             """
                             <groupId>test</groupId>
                             <artifactId>project2</artifactId>
                             <version>1</version>
                             <packaging>pom</packaging>
                             <modules>
                               <module>../module</module>
                             </modules>
                             """.trimIndent())
    val m = createModulePom("module",
                            """
                            <groupId>test</groupId>
                            <artifactId>module</artifactId>
                            <version>1</version>
                            """.trimIndent())
    updateAll(p1, p2)
    val roots = tree.rootProjects
    assertEquals(2, roots.size)
    assertEquals(p1, roots[0].file)
    assertEquals(p2, roots[1].file)
    assertEquals(1, tree.getModules(roots[0]).size)
    assertEquals(m, tree.getModules(roots[0])[0].file)
    assertEquals(0, tree.getModules(roots[1]).size)
  }

  @Test
  fun testSameProjectAsModuleOfSeveralProjectsInHierarchy() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>module1</module>
                         <module>module1/module2</module>
                       </modules>
                       
                       """.trimIndent())
    val m1 = createModulePom("module1",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>module1</artifactId>
                                       <version>1</version>
                                       <packaging>pom</packaging>
                                       <modules>
                                         <module>module2</module>
                                       </modules>
                                       """.trimIndent())
    val m2 = createModulePom("module1/module2",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>module2</artifactId>
                                       <version>1</version>
                                       """.trimIndent())
    updateAll(myProjectPom)
    val roots = tree.rootProjects
    assertEquals(1, roots.size)
    val allModules = collectAllModulesRecursively(
      tree, roots[0])
    assertEquals(2, allModules.size)
    UsefulTestCase.assertSameElements(Set.of(m1, m2), allModules.map({ m: MavenProject -> m.file }))
  }

  @Test
  fun testRemovingChildProjectFromRootProjects() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m</module>
                       </modules>
                       
                       """.trimIndent())
    val m = createModulePom("m",
                            """
                                      <groupId>test</groupId>
                                      <artifactId>m</artifactId>
                                      <version>1</version>
                                      
                                      """.trimIndent())

    // all projects are processed in the specified order
    // if we have imported a child project as a root one,
    // we have to correct ourselves and to remove it from roots.
    updateAll(m, myProjectPom)
    val roots = tree.rootProjects
    assertEquals(1, roots.size)
    assertEquals(myProjectPom, roots[0].file)
    assertEquals(1, tree.getModules(roots[0]).size)
    assertEquals(m, tree.getModules(roots[0])[0].file)
  }

  @Test
  fun testSendingNotificationsWhenAggregationChanged() {
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
    val m1 = createModulePom("m1",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m1</artifactId>
                                       <version>1</version>
                                       """.trimIndent())
    val m2 = createModulePom("m2",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m2</artifactId>
                                       <version>1</version>
                                       """.trimIndent())
    updateAll(myProjectPom, m1, m2)
    var roots = tree.rootProjects
    assertEquals(1, roots.size)
    assertEquals(2, tree.getModules(roots[0]).size)
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m1</module>
                       </modules>
                       
                       """.trimIndent())
    val listener = MyLoggingListener()
    tree.addListener(listener, getTestRootDisposable())
    update(myProjectPom)
    roots = tree.rootProjects
    assertEquals(2, roots.size)
    assertEquals(1, tree.getModules(roots[0]).size)
    assertEquals(log().add("updated", "project", "m2").add("deleted"), listener.log)
  }

  @Test
  fun testUpdatingWholeModel() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m</module>
                       </modules>
                       
                       """.trimIndent())
    val m = createModulePom("m",
                            """
                                      <groupId>test</groupId>
                                      <artifactId>m</artifactId>
                                      <version>1</version>
                                      
                                      """.trimIndent())
    updateAll(myProjectPom)
    var roots = tree.rootProjects
    val parentNode = roots[0]
    val childNode = tree.getModules(roots[0])[0]
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project1</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m</module>
                       </modules>
                       
                       """.trimIndent())
    createModulePom("m", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      
      """.trimIndent())
    updateAll(myProjectPom)
    roots = tree.rootProjects
    assertEquals(1, roots.size)
    assertEquals(1, tree.getModules(roots[0]).size)
    val parentNode1 = roots[0]
    val childNode1 = tree.getModules(roots[0])[0]
    assertSame(parentNode, parentNode1)
    assertSame(childNode, childNode1)
    assertEquals("project1", parentNode1.mavenId.artifactId)
    assertEquals("m1", childNode1.mavenId.artifactId)
  }

  @Test
  fun testForceUpdatingWholeModel() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m</module>
                       </modules>
                       
                       """.trimIndent())
    val m = createModulePom("m",
                            """
                                      <groupId>test</groupId>
                                      <artifactId>m</artifactId>
                                      <version>1</version>
                                      
                                      """.trimIndent())
    val l = MyLoggingListener()
    tree.addListener(l, getTestRootDisposable())
    updateAll(myProjectPom)
    assertEquals(log().add("updated", "project", "m").add("deleted"), l.log)
    l.log.clear()
    tree.updateAll(false, mavenGeneralSettings, mavenProgressIndicator.indicator)
    assertEquals(log(), l.log)
    l.log.clear()
    tree.updateAll(true, mavenGeneralSettings, mavenProgressIndicator.indicator)
    assertEquals(log().add("updated", "project", "m").add("deleted"), l.log)
  }

  @Test
  fun testForceUpdatingSingleProject() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m</module>
                       </modules>
                       
                       """.trimIndent())
    val m = createModulePom("m",
                            """
                                      <groupId>test</groupId>
                                      <artifactId>m</artifactId>
                                      <version>1</version>
                                      
                                      """.trimIndent())
    val l = MyLoggingListener()
    tree.addListener(l, getTestRootDisposable())
    update(myProjectPom)
    assertEquals(log().add("updated", "project", "m").add("deleted"), l.log)
    l.log.clear()
    tree.update(listOf(myProjectPom), false, mavenGeneralSettings, mavenProgressIndicator.indicator)
    assertEquals(log(), l.log)
    l.log.clear()
    tree.update(listOf(myProjectPom), true, mavenGeneralSettings, mavenProgressIndicator.indicator)
    assertEquals(log().add("updated", "project").add("deleted"), l.log)
    l.log.clear()
  }

  @Test
  fun testUpdatingModelWithNewProfiles() {
    createProjectPom("""
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
    val m1 = createModulePom("m1",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m1</artifactId>
                                       <version>1</version>
                                       """.trimIndent())
    val m2 = createModulePom("m2",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m2</artifactId>
                                       <version>1</version>
                                       """.trimIndent())
    updateAll(listOf("one"), myProjectPom)
    var roots = tree.rootProjects
    assertEquals(1, roots.size)
    assertEquals(myProjectPom, roots[0].file)
    assertEquals(1, tree.getModules(roots[0]).size)
    assertEquals(m1, tree.getModules(roots[0])[0].file)
    updateAll(listOf("two"), myProjectPom)
    roots = tree.rootProjects
    assertEquals(1, roots.size)
    assertEquals(myProjectPom, roots[0].file)
    assertEquals(1, tree.getModules(roots[0]).size)
    assertEquals(m2, tree.getModules(roots[0])[0].file)
  }

  @Test
  fun testUpdatingParticularProject() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m</module>
                       </modules>
                       
                       """.trimIndent())
    val m = createModulePom("m",
                            """
                                      <groupId>test</groupId>
                                      <artifactId>m</artifactId>
                                      <version>1</version>
                                      
                                      """.trimIndent())
    updateAll(myProjectPom)
    createModulePom("m", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      
      """.trimIndent())
    update(m)
    val n = tree.findProject(m)
    assertEquals("m1", n!!.mavenId.artifactId)
  }

  @Test
  fun testUpdatingInheritance() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <properties>
                         <childName>child</childName>
                       </properties>
                       
                       """.trimIndent())
    val child = createModulePom("child",
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
    updateAll(myProjectPom, child)
    assertEquals("child", tree.findProject(child)!!.mavenId.artifactId)
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <properties>
                         <childName>child2</childName>
                       </properties>
                       
                       """.trimIndent())
    update(myProjectPom)
    assertEquals("child2", tree.findProject(child)!!.mavenId.artifactId)
  }

  @Test
  fun testUpdatingInheritanceHierarhically() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <properties>
                         <subChildName>subChild</subChildName>
                       </properties>
                       
                       """.trimIndent())
    val child = createModulePom("child",
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
    val subChild = createModulePom("subChild",
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
    updateAll(myProjectPom, child, subChild)
    assertEquals("subChild", tree.findProject(subChild)!!.mavenId.artifactId)
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <properties>
                         <subChildName>subChild2</subChildName>
                       </properties>
                       
                       """.trimIndent())
    update(myProjectPom)
    assertEquals("subChild2", tree.findProject(subChild)!!.mavenId.artifactId)
  }

  @Test
  fun testSendingNotificationAfterProjectIsAddedInToHierarchy() {
    val listener = MyLoggingListener()
    tree.addListener(listener, getTestRootDisposable())
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>m1</artifactId>
                       <version>1</version>
                       
                       """.trimIndent())
    updateAll(myProjectPom)
    assertEquals(log().add("updated", "m1").add("deleted"), listener.log)
  }

  @Test
  @Throws(Exception::class)
  fun testSendingNotificationsWhenResolveFailed() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name
                       """.trimIndent())
    updateAll(myProjectPom)
    val listener = MyLoggingListener()
    tree.addListener(listener, getTestRootDisposable())
    val project = tree.findProject(myProjectPom)
    val embeddersManager = MavenEmbeddersManager(myProject)
    val nativeProject: MutableList<NativeMavenProjectHolder?> = ArrayList()
    try {
      tree.addListener(object : MavenProjectsTree.Listener {
        override fun projectResolved(projectWithChanges: Pair<MavenProject, MavenProjectChanges>,
                                     nativeMavenProject: NativeMavenProjectHolder?) {
          nativeProject.add(nativeMavenProject)
        }
      }, getTestRootDisposable())
      resolve(myProject,
              project!!,
              mavenGeneralSettings,
              embeddersManager,
              NULL_MAVEN_CONSOLE,
              mavenProgressIndicator
      )
    }
    finally {
      embeddersManager.releaseInTests()
    }
    assertEquals(log().add("resolved", "project"), listener.log)
    assertTrue(project!!.hasReadingProblems())
    UsefulTestCase.assertSize(1, nativeProject)
    assertNull(nativeProject[0])
  }

  @Test
  fun testAddingInheritanceParent() {
    val child = createModulePom("child",
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
    updateAll(child)
    assertEquals("\${childName}", tree.findProject(child)!!.mavenId.artifactId)
    val parent = createModulePom("parent",
                                 """
                                           <groupId>test</groupId>
                                           <artifactId>parent</artifactId>
                                           <version>1</version>
                                           <properties>
                                             <childName>child</childName>
                                           </properties>
                                           
                                           """.trimIndent())
    update(parent)
    assertEquals("child", tree.findProject(child)!!.mavenId.artifactId)
  }

  @Test
  fun testAddingInheritanceChild() {
    val parent = createModulePom("parent",
                                 """
                                           <groupId>test</groupId>
                                           <artifactId>parent</artifactId>
                                           <version>1</version>
                                           <properties>
                                             <childName>child</childName>
                                           </properties>
                                           
                                           """.trimIndent())
    updateAll(parent)
    val child = createModulePom("child",
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
    update(child)
    assertEquals("child", tree.findProject(child)!!.mavenId.artifactId)
  }

  @Test
  fun testParentPropertyInterpolation() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <properties>
                         <childName>child</childName>
                       </properties>
                       
                       """.trimIndent())
    update(myProjectPom)
    val child = createModulePom("child",
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
    update(child)
    assertEquals("child", tree.findProject(child)!!.mavenId.artifactId)
  }

  @Test
  fun testAddingInheritanceChildOnParentUpdate() {
    createProjectPom("""
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
    updateAll(myProjectPom)
    val child = createModulePom("child",
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
    update(myProjectPom)
    assertEquals("child", tree.findProject(child)!!.mavenId.artifactId)
  }

  @Test
  fun testDoNotReAddInheritanceChildOnParentModulesRemoval() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <modules>
                        <module>child</module>
                       </modules>
                       
                       """.trimIndent())
    val child = createModulePom("child",
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
    updateAll(myProjectPom)
    var roots = tree.rootProjects
    assertEquals(1, roots.size)
    assertEquals(myProjectPom, roots[0].file)
    assertEquals(1, tree.getModules(roots[0]).size)
    assertEquals(child, tree.getModules(roots[0])[0].file)
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       
                       """.trimIndent())
    update(myProjectPom)
    roots = tree.rootProjects
    assertEquals(1, roots.size)
    assertEquals(myProjectPom, roots[0].file)
    assertEquals(0, tree.getModules(roots[0]).size)
  }

  @Test
  fun testChangingInheritance() {
    val parent1 = createModulePom("parent1",
                                  """
                                            <groupId>test</groupId>
                                            <artifactId>parent1</artifactId>
                                            <version>1</version>
                                            <properties>
                                              <childName>child1</childName>
                                            </properties>
                                            
                                            """.trimIndent())
    val parent2 = createModulePom("parent2",
                                  """
                                            <groupId>test</groupId>
                                            <artifactId>parent2</artifactId>
                                            <version>1</version>
                                            <properties>
                                              <childName>child2</childName>
                                            </properties>
                                            
                                            """.trimIndent())
    val child = createModulePom("child",
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
    updateAll(parent1, parent2, child)
    assertEquals("child1", tree.findProject(child)!!.mavenId.artifactId)
    createModulePom("child", """
      <groupId>test</groupId>
      <artifactId>${'$'}{childName}</artifactId>
      <version>1</version>
      <parent>
        <groupId>test</groupId>
        <artifactId>parent2</artifactId>
        <version>1</version>
      </parent>
      
      """.trimIndent())
    update(child)
    assertEquals("child2", tree.findProject(child)!!.mavenId.artifactId)
  }

  @Test
  fun testChangingInheritanceParentId() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <properties>
                         <childName>child</childName>
                       </properties>
                       
                       """.trimIndent())
    val child = createModulePom("child",
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
    updateAll(myProjectPom, child)
    assertEquals("child", tree.findProject(child)!!.mavenId.artifactId)
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent2</artifactId>
                       <version>1</version>
                       <properties>
                         <childName>child</childName>
                       </properties>
                       
                       """.trimIndent())
    update(myProjectPom)
    assertEquals("\${childName}", tree.findProject(child)!!.mavenId.artifactId)
  }

  @Test
  @Throws(IOException::class)
  fun testHandlingSelfInheritance() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <parent>
                         <groupId>test</groupId>
                         <artifactId>parent</artifactId>
                         <version>1</version>
                       </parent>
                       
                       """.trimIndent())
    updateAll(myProjectPom) // shouldn't hang
    updateTimestamps(myProjectPom)
    update(myProjectPom) // shouldn't hang
    updateTimestamps(myProjectPom)
    updateAll(myProjectPom) // shouldn't hang
  }

  @Test
  @Throws(IOException::class)
  fun testHandlingRecursiveInheritance() {
    createProjectPom("""
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
    val child = createModulePom("child",
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
    updateAll(myProjectPom, child) // shouldn't hang
    updateTimestamps(myProjectPom, child)
    update(myProjectPom) // shouldn't hang
    updateTimestamps(myProjectPom, child)
    update(child) // shouldn't hang
    updateTimestamps(myProjectPom, child)
    updateAll(myProjectPom, child) // shouldn't hang
  }

  @Test
  fun testDeletingInheritanceParent() {
    val parent = createModulePom("parent",
                                 """
                                           <groupId>test</groupId>
                                           <artifactId>parent</artifactId>
                                           <version>1</version>
                                           <properties>
                                             <childName>child</childName>
                                           </properties>
                                           
                                           """.trimIndent())
    val child = createModulePom("child",
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
    updateAll(parent, child)
    assertEquals("child", tree.findProject(child)!!.mavenId.artifactId)
    deleteProject(parent)
    assertEquals("\${childName}", tree.findProject(child)!!.mavenId.artifactId)
  }

  @Test
  fun testDeletingInheritanceChild() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <properties>
                         <subChildName>subChild</subChildName>
                       </properties>
                       
                       """.trimIndent())
    val child = createModulePom("child",
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
    val subChild = createModulePom("subChild",
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
    updateAll(myProjectPom, child, subChild)
    assertEquals("subChild", tree.findProject(subChild)!!.mavenId.artifactId)
    deleteProject(child)
    assertEquals("\${subChildName}", tree.findProject(subChild)!!.mavenId.artifactId)
  }

  @Test
  @Throws(IOException::class)
  fun testRecursiveInheritanceAndAggregation() {
    createProjectPom("""
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
    val child = createModulePom("child",
                                """
                                          <groupId>test</groupId>
                                          <artifactId>child</artifactId>
                                          <version>1</version>
                                          
                                          """.trimIndent())
    updateAll(myProjectPom) // should not recurse
    updateTimestamps(myProjectPom, child)
    updateAll(child) // should not recurse
  }

  @Test
  fun testUpdatingAddsModules() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       
                       """.trimIndent())
    val m = createModulePom("m",
                            """
                                      <groupId>test</groupId>
                                      <artifactId>m</artifactId>
                                      <version>1</version>
                                      
                                      """.trimIndent())
    updateAll(myProjectPom)
    var roots = tree.rootProjects
    assertEquals(1, roots.size)
    assertEquals(myProjectPom, roots[0].file)
    assertEquals(0, tree.getModules(roots[0]).size)
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m</module>
                       </modules>
                       
                       """.trimIndent())
    update(myProjectPom)
    roots = tree.rootProjects
    assertEquals(1, roots.size)
    assertEquals(myProjectPom, roots[0].file)
    assertEquals(1, tree.getModules(roots[0]).size)
    assertEquals(m, tree.getModules(roots[0])[0].file)
  }

  @Test
  fun testUpdatingUpdatesModulesIfProjectIsChanged() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m</module>
                       </modules>
                       
                       """.trimIndent())
    val m = createModulePom("m",
                            """
                                      <groupId>test</groupId>
                                      <artifactId>m</artifactId>
                                      <version>1</version>
                                      
                                      """.trimIndent())
    updateAll(myProjectPom)
    assertEquals("m", tree.findProject(m)!!.mavenId.artifactId)
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <name>foo</name>
                       <modules>
                         <module>m</module>
                       </modules>
                       
                       """.trimIndent())
    createModulePom("m", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      
      """.trimIndent())
    update(myProjectPom)
    assertEquals("m2", tree.findProject(m)!!.mavenId.artifactId)
  }

  @Test
  fun testUpdatingDoesNotUpdateModulesIfProjectIsNotChanged() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m</module>
                       </modules>
                       
                       """.trimIndent())
    val m = createModulePom("m",
                            """
                                      <groupId>test</groupId>
                                      <artifactId>m</artifactId>
                                      <version>1</version>
                                      
                                      """.trimIndent())
    updateAll(myProjectPom)
    assertEquals("m", tree.findProject(m)!!.mavenId.artifactId)
    createModulePom("m", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      
      """.trimIndent())
    update(myProjectPom)

    // did not change
    assertEquals("m", tree.findProject(m)!!.mavenId.artifactId)
  }

  @Test
  fun testAddingProjectAsModuleToExistingOne() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m</module>
                       </modules>
                       
                       """.trimIndent())
    updateAll(myProjectPom)
    var roots = tree.rootProjects
    assertEquals(1, roots.size)
    assertEquals(0, tree.getModules(roots[0]).size)
    val m = createModulePom("m",
                            """
                                      <groupId>test</groupId>
                                      <artifactId>m</artifactId>
                                      <version>1</version>
                                      
                                      """.trimIndent())
    update(m)
    roots = tree.rootProjects
    assertEquals(1, roots.size)
    assertEquals(1, tree.getModules(roots[0]).size)
    assertEquals(m, tree.getModules(roots[0])[0].file)
  }

  @Test
  fun testAddingProjectAsAggregatorForExistingOne() {
    val m = createModulePom("m",
                            """
                                      <groupId>test</groupId>
                                      <artifactId>m</artifactId>
                                      <version>1</version>
                                      
                                      """.trimIndent())
    updateAll(m)
    var roots = tree.rootProjects
    assertEquals(1, roots.size)
    assertEquals(m, roots[0].file)
    assertEquals(0, tree.getModules(roots[0]).size)
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m</module>
                       </modules>
                       
                       """.trimIndent())
    update(myProjectPom)
    roots = tree.rootProjects
    assertEquals(1, roots.size)
    assertEquals(myProjectPom, roots[0].file)
    assertEquals(1, tree.getModules(roots[0]).size)
    assertEquals(m, tree.getModules(roots[0])[0].file)
  }

  @Test
  fun testAddingProjectWithModules() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       
                       """.trimIndent())
    updateAll(myProjectPom)
    var roots = tree.rootProjects
    assertEquals(1, roots.size)
    assertEquals(0, tree.getModules(roots[0]).size)
    val m1 = createModulePom("m1",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m1</artifactId>
                                       <version>1</version>
                                       <packaging>pom</packaging>
                                       <modules>
                                         <module>m2</module>
                                       </modules>
                                       """.trimIndent())
    val m2 = createModulePom("m1/m2",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m2</artifactId>
                                       <version>1</version>
                                       """.trimIndent())
    update(m1)
    roots = tree.rootProjects
    assertEquals(2, roots.size)
    assertEquals(myProjectPom, roots[0].file)
    assertEquals(m1, roots[1].file)
    assertEquals(1, tree.getModules(roots[1]).size)
    assertEquals(m2, tree.getModules(roots[1])[0].file)
  }

  @Test
  fun testUpdatingAddsModulesFromRootProjects() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       
                       """.trimIndent())
    val m = createModulePom("m",
                            """
                                      <groupId>test</groupId>
                                      <artifactId>m</artifactId>
                                      <version>1</version>
                                      
                                      """.trimIndent())
    updateAll(myProjectPom, m)
    var roots = tree.rootProjects
    assertEquals(2, roots.size)
    assertEquals(myProjectPom, roots[0].file)
    assertEquals(m, roots[1].file)
    assertEquals("m", roots[1].mavenId.artifactId)
    assertEquals(0, tree.getModules(roots[0]).size)
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m</module>
                       </modules>
                       
                       """.trimIndent())
    update(myProjectPom)
    roots = tree.rootProjects
    assertEquals(1, roots.size)
    assertEquals(myProjectPom, roots[0].file)
    assertEquals(1, tree.getModules(roots[0]).size)
    assertEquals(m, tree.getModules(roots[0])[0].file)
  }

  @Test
  fun testMovingModuleToRootsWhenAggregationChanged() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m</module>
                       </modules>
                       
                       """.trimIndent())
    val m = createModulePom("m",
                            """
                                      <groupId>test</groupId>
                                      <artifactId>m</artifactId>
                                      <version>1</version>
                                      
                                      """.trimIndent())
    updateAll(myProjectPom, m)
    var roots = tree.rootProjects
    assertEquals(1, roots.size)
    assertEquals(1, tree.getModules(roots[0]).size)
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       
                       """.trimIndent())
    update(myProjectPom)
    roots = tree.rootProjects
    assertEquals(2, roots.size)
    assertTrue(tree.getModules(roots[0]).isEmpty())
    assertTrue(tree.getModules(roots[1]).isEmpty())
  }

  @Test
  fun testDeletingProject() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m</module>
                       </modules>
                       
                       """.trimIndent())
    val m = createModulePom("m",
                            """
                                      <groupId>test</groupId>
                                      <artifactId>m</artifactId>
                                      <version>1</version>
                                      
                                      """.trimIndent())
    updateAll(myProjectPom)
    var roots = tree.rootProjects
    assertEquals(1, roots.size)
    assertEquals(1, tree.getModules(roots[0]).size)
    deleteProject(m)
    roots = tree.rootProjects
    assertEquals(1, roots.size)
    assertEquals(0, tree.getModules(roots[0]).size)
  }

  @Test
  fun testDeletingProjectWithModules() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m1</module>
                       </modules>
                       
                       """.trimIndent())
    val m1 = createModulePom("m1",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m1</artifactId>
                                       <version>1</version>
                                       <packaging>pom</packaging>
                                       <modules>
                                         <module>m2</module>
                                       </modules>
                                       """.trimIndent())
    createModulePom("m1/m2",
                    """
                      <groupId>test</groupId>
                      <artifactId>m2</artifactId>
                      <version>1</version>
                      
                      """.trimIndent())
    updateAll(myProjectPom)
    var roots = tree.rootProjects
    assertEquals(1, roots.size)
    assertEquals(1, tree.getModules(roots[0]).size)
    assertEquals(1, tree.getModules(tree.getModules(roots[0])[0]).size)
    deleteProject(m1)
    roots = tree.rootProjects
    assertEquals(1, roots.size)
    assertEquals(myProjectPom, roots[0].file)
    assertEquals(0, tree.getModules(roots[0]).size)
  }

  @Test
  fun testSendingNotificationsWhenProjectDeleted() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m1</module>
                       </modules>
                       
                       """.trimIndent())
    val m1 = createModulePom("m1",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m1</artifactId>
                                       <version>1</version>
                                       <packaging>pom</packaging>
                                       <modules>
                                         <module>m2</module>
                                       </modules>
                                       """.trimIndent())
    createModulePom("m1/m2",
                    """
                      <groupId>test</groupId>
                      <artifactId>m2</artifactId>
                      <version>1</version>
                      
                      """.trimIndent())
    updateAll(myProjectPom)
    val listener = MyLoggingListener()
    tree.addListener(listener, getTestRootDisposable())
    deleteProject(m1)
    assertEquals(log().add("updated").add("deleted", "m2", "m1"), listener.log)
  }

  @Test
  fun testReconnectModuleOfDeletedProjectIfModuleIsManaged() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m1</module>
                       </modules>
                       
                       """.trimIndent())
    val m1 = createModulePom("m1",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m1</artifactId>
                                       <version>1</version>
                                       <packaging>pom</packaging>
                                       <modules>
                                         <module>m2</module>
                                       </modules>
                                       """.trimIndent())
    val m2 = createModulePom("m1/m2",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m2</artifactId>
                                       <version>1</version>
                                       """.trimIndent())
    updateAll(myProjectPom, m2)
    var roots = tree.rootProjects
    assertEquals(1, roots.size)
    assertEquals(1, tree.getModules(roots[0]).size)
    assertEquals(1, tree.getModules(tree.getModules(roots[0])[0]).size)
    val listener = MyLoggingListener()
    tree.addListener(listener, getTestRootDisposable())
    deleteProject(m1)
    roots = tree.rootProjects
    assertEquals(2, roots.size)
    assertEquals(myProjectPom, roots[0].file)
    assertEquals(0, tree.getModules(roots[0]).size)
    assertEquals(m2, roots[1].file)
    assertEquals(0, tree.getModules(roots[1]).size)
    assertEquals(log().add("updated", "m2").add("deleted", "m1"), listener.log)
  }

  @Test
  fun testAddingProjectsOnUpdateAllWhenManagedFilesChanged() {
    val m1 = createModulePom("m1",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m1</artifactId>
                                       <version>1</version>
                                       """.trimIndent())
    val m2 = createModulePom("m2",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m2</artifactId>
                                       <version>1</version>
                                       """.trimIndent())
    val m3 = createModulePom("m3",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m3</artifactId>
                                       <version>1</version>
                                       """.trimIndent())
    updateAll(m1, m2)
    assertEquals(2, tree.rootProjects.size)
    updateAll(m1, m2, m3)
    assertEquals(3, tree.rootProjects.size)
  }

  @Test
  fun testDeletingProjectsOnUpdateAllWhenManagedFilesChanged() {
    val m1 = createModulePom("m1",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m1</artifactId>
                                       <version>1</version>
                                       """.trimIndent())
    val m2 = createModulePom("m2",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m2</artifactId>
                                       <version>1</version>
                                       """.trimIndent())
    val m3 = createModulePom("m3",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m3</artifactId>
                                       <version>1</version>
                                       """.trimIndent())
    updateAll(m1, m2, m3)
    assertEquals(3, tree.rootProjects.size)
    updateAll(m1, m2)
    assertEquals(2, tree.rootProjects.size)
  }

  @Test
  fun testSendingNotificationsWhenAddingOrDeletingManagedFiles() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       
                       """.trimIndent())
    val m1 = createModulePom("m1",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m1</artifactId>
                                       <version>1</version>
                                       """.trimIndent())
    val m2 = createModulePom("m2",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m2</artifactId>
                                       <version>1</version>
                                       """.trimIndent())
    val l = MyLoggingListener()
    tree.addListener(l, getTestRootDisposable())
    tree.addManagedFilesWithProfiles(listOf(myProjectPom), MavenExplicitProfiles.NONE)
    tree.updateAll(false, mavenGeneralSettings, mavenProgressIndicator.indicator)
    assertEquals(log().add("updated", "parent", "m1", "m2").add("deleted"), l.log)
    l.log.clear()
    tree.removeManagedFiles(Arrays.asList(myProjectPom))
    tree.updateAll(false, mavenGeneralSettings, mavenProgressIndicator.indicator)
    assertEquals(log().add("updated").add("deleted", "m1", "m2", "parent"), l.log)
  }

  @Test
  fun testUpdatingModelWhenActiveProfilesChange() {
    createProjectPom("""
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
    createModulePom("m",
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
    updateAll(mutableListOf<String?>("one"), myProjectPom)
    val roots = tree.rootProjects
    val parentNode = roots[0]
    val childNode = tree.getModules(roots[0])[0]
    assertUnorderedPathsAreEqual(parentNode.sources, Arrays.asList(FileUtil.toSystemDependentName(
      "$projectPath/value1")))
    assertUnorderedPathsAreEqual(childNode.sources, Arrays.asList(FileUtil.toSystemDependentName(
      "$projectPath/m/value1")))
    updateAll(mutableListOf<String?>("two"), myProjectPom)
    assertUnorderedPathsAreEqual(parentNode.sources, Arrays.asList(FileUtil.toSystemDependentName(
      "$projectPath/value2")))
    assertUnorderedPathsAreEqual(childNode.sources, Arrays.asList(FileUtil.toSystemDependentName(
      "$projectPath/m/value2")))
  }

  @Test
  fun testUpdatingModelWhenProfilesXmlChange() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <build>
                         <sourceDirectory>${'$'}{prop}</sourceDirectory>
                       </build>
                       
                       """.trimIndent())
    createProfilesXmlOldStyle("""
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
    updateAll(myProjectPom)
    val roots = tree.rootProjects
    val project = roots[0]
    assertUnorderedPathsAreEqual(project.sources, Arrays.asList(FileUtil.toSystemDependentName(
      "$projectPath/value1")))
    createProfilesXmlOldStyle("""
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
    updateAll(myProjectPom)
    assertUnorderedPathsAreEqual(project.sources, Arrays.asList(FileUtil.toSystemDependentName(
      "$projectPath/value2")))
  }

  @Test
  fun testUpdatingModelWhenParentProfilesXmlChange() {
    val parent = createModulePom("parent",
                                 """
                                           <groupId>test</groupId>
                                           <artifactId>parent</artifactId>
                                           <version>1</version>
                                           <packaging>pom</packaging>
                                           
                                           """.trimIndent())
    createProfilesXmlOldStyle("parent",
                              """
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
    val child = createModulePom("m",
                                """
                                          <groupId>test</groupId>
                                          <artifactId>m</artifactId>
                                          <version>1</version>
                                          <parent>
                                            <groupId>test</groupId>
                                            <artifactId>parent</artifactId>
                                            <version>1</version>
                                          </parent>
                                          <build>
                                            <sourceDirectory>${'$'}{prop}</sourceDirectory>
                                          </build>
                                          
                                          """.trimIndent())
    updateAll(parent, child)
    val roots = tree.rootProjects
    assertEquals(2, roots.size)
    val childProject = roots[0]
    assertUnorderedPathsAreEqual(childProject.sources, Arrays.asList(FileUtil.toSystemDependentName(
      "$projectPath/m/value1")))
    createProfilesXmlOldStyle("parent",
                              """
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
    update(parent)
    assertUnorderedPathsAreEqual(childProject.sources, Arrays.asList(FileUtil.toSystemDependentName(
      "$projectPath/m/value2")))
  }

  @Test
  fun testUpdatingModelWhenParentProfilesXmlChangeAndItIsAModuleAlso() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m</module>
                       </modules>
                       
                       """.trimIndent())
    createProfilesXmlOldStyle("""
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
    createModulePom("m",
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
    updateAll(myProjectPom)
    val childNode = tree.getModules(tree.rootProjects[0])[0]
    assertUnorderedPathsAreEqual(childNode.sources, Arrays.asList(FileUtil.toSystemDependentName(
      "$projectPath/m/value1")))
    createProfilesXmlOldStyle("""
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
    updateAll(myProjectPom)
    assertUnorderedPathsAreEqual(childNode.sources, Arrays.asList(FileUtil.toSystemDependentName(
      "$projectPath/m/value2")))
  }

  @Test
  fun testDoNotUpdateModelWhenAggregatorProfilesXmlChange() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m</module>
                       </modules>
                       
                       """.trimIndent())
    createModulePom("m",
                    """
                      <groupId>test</groupId>
                      <artifactId>m</artifactId>
                      <version>1</version>
                      <build>
                        <sourceDirectory>${'$'}{prop}</sourceDirectory>
                      </build>
                      
                      """.trimIndent())
    createProfilesXmlOldStyle("""
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
    updateAll(myProjectPom)
    createProfilesXmlOldStyle("""
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
    updateAll(myProjectPom)
    val existingManagedFiles = tree.getExistingManagedFiles()
    val obsoleteFiles = tree.rootProjectsFiles
    assertEquals(existingManagedFiles, obsoleteFiles)
  }

  @Test
  @Throws(Exception::class)
  fun testSaveLoad() {
    //todo: move to resolver test
    // stripping down plugins
    // stripping down Xpp3Dom fields
    createProjectPom("""
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
    val m1 = createModulePom("m1",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m1</artifactId>
                                       <version>1</version>
                                       """.trimIndent())
    val m2 = createModulePom("m2",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m2</artifactId>
                                       <version>1</version>
                                       """.trimIndent())
    updateAll(myProjectPom)
    val parentProject = tree.findProject(myProjectPom)
    val embeddersManager = MavenEmbeddersManager(myProject)
    try {
      resolve(myProject,
              parentProject!!,
              mavenGeneralSettings,
              embeddersManager,
              NULL_MAVEN_CONSOLE,
              mavenProgressIndicator)
    }
    finally {
      embeddersManager.releaseInTests()
    }
    val f = myDir.toPath().resolve("tree.dat")
    tree.save(f)
    val read = MavenProjectsTree.read(myProject, f)
    val roots = read!!.rootProjects
    assertEquals(1, roots.size)
    val rootProject = roots[0]
    assertEquals(myProjectPom, rootProject.file)
    assertEquals(2, read.getModules(rootProject).size)
    assertEquals(m1, read.getModules(rootProject)[0].file)
    assertEquals(m2, read.getModules(rootProject)[1].file)
    assertNull(read.findAggregator(rootProject))
    assertEquals(rootProject, read.findAggregator(read.findProject(m1)))
    assertEquals(rootProject, read.findAggregator(read.findProject(m2)))
  }

  @Test
  @Throws(Exception::class)
  fun testCollectingProfilesFromSettingsXmlAndPluginsXml() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <profiles>
                         <profile>
                           <id>one</id>
                         </profile>
                       </profiles>
                       
                       """.trimIndent())
    createProfilesXml("""
                        <profile>
                          <id>two</id>
                        </profile>
                        
                        """.trimIndent())
    updateSettingsXml("""
                        <profiles>
                          <profile>
                            <id>three</id>
                          </profile>
                        </profiles>
                        
                        """.trimIndent())
    updateAll(myProjectPom)
    assertUnorderedElementsAreEqual(tree.getAvailableProfiles(), "one", "two", "three")
  }

  @Test
  @Throws(Exception::class)
  fun testCollectingProfilesFromSettingsXmlAndPluginsXmlAfterResolve() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <profiles>
                         <profile>
                           <id>one</id>
                         </profile>
                       </profiles>
                       
                       """.trimIndent())
    createProfilesXml("""
                        <profile>
                          <id>two</id>
                        </profile>
                        
                        """.trimIndent())
    updateSettingsXml("""
                        <profiles>
                          <profile>
                            <id>three</id>
                          </profile>
                        </profiles>
                        
                        """.trimIndent())
    updateAll(myProjectPom)
    val embeddersManager = MavenEmbeddersManager(myProject)
    try {
      resolve(myProject,
              tree.rootProjects[0],
              mavenGeneralSettings,
              embeddersManager,
              NULL_MAVEN_CONSOLE,
              mavenProgressIndicator
      )
    }
    finally {
      embeddersManager.releaseInTests()
    }
    assertUnorderedElementsAreEqual(tree.getAvailableProfiles(), "one", "two", "three")
  }

  @Test
  @Throws(Exception::class)
  fun testCollectingProfilesFromParentsAfterResolve() {
    createModulePom("parent1",
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
    createProfilesXml("parent1",
                      """
                        <profile>
                          <id>parent1ProfileXml</id>
                        </profile>
                        
                        """.trimIndent())
    createModulePom("parent2",
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
    createProfilesXml("parent2",
                      """
                        <profile>
                          <id>parent2ProfileXml</id>
                        </profile>
                        
                        """.trimIndent())
    createProjectPom("""
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
    createProfilesXml("""
                        <profile>
                          <id>projectProfileXml</id>
                        </profile>
                        
                        """.trimIndent())
    updateSettingsXml("""
                        <profiles>
                          <profile>
                            <id>settings</id>
                          </profile>
                        </profiles>
                        
                        """.trimIndent())
    updateAll(mutableListOf<String?>("projectProfileXml",
                                     "projectProfile",
                                     "parent1Profile",
                                     "parent1ProfileXml",
                                     "parent2Profile",
                                     "parent2ProfileXml",
                                     "settings",
                                     "xxx"),
              myProjectPom)
    val project = tree.findProject(myProjectPom)
    assertUnorderedElementsAreEqual(
      project!!.activatedProfilesIds.enabledProfiles,
      "projectProfileXml",
      "projectProfile",
      "parent1Profile",
      "parent1ProfileXml",
      "parent2Profile",
      "parent2ProfileXml",
      "settings")
    val embeddersManager = MavenEmbeddersManager(myProject)
    try {
      resolve(myProject,
              project,
              mavenGeneralSettings,
              embeddersManager,
              NULL_MAVEN_CONSOLE,
              mavenProgressIndicator
      )
    }
    finally {
      embeddersManager.releaseInTests()
    }
    assertUnorderedElementsAreEqual(
      project.activatedProfilesIds.enabledProfiles,
      "projectProfile",
      "parent1Profile",
      "parent2Profile",
      "settings")
  }

  @Test
  @Throws(IOException::class)
  fun testDeletingAndRestoringActiveProfilesWhenAvailableProfilesChange() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <profiles>
                         <profile>
                           <id>one</id>
                         </profile>
                       </profiles>
                       
                       """.trimIndent())
    createProfilesXml("""
                        <profile>
                          <id>two</id>
                        </profile>
                        
                        """.trimIndent())
    updateAll(mutableListOf<String?>("one", "two"), myProjectPom)
    assertUnorderedElementsAreEqual(
      tree.explicitProfiles.enabledProfiles, "one", "two")
    deleteProfilesXml()
    update(myProjectPom)
    assertUnorderedElementsAreEqual(
      tree.explicitProfiles.enabledProfiles, "one")
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       
                       """.trimIndent())
    update(myProjectPom)
    assertUnorderedElementsAreEqual(tree.explicitProfiles.enabledProfiles)
    createProfilesXml("""
                        <profile>
                          <id>two</id>
                        </profile>
                        
                        """.trimIndent())
    update(myProjectPom)
    assertUnorderedElementsAreEqual(
      tree.explicitProfiles.enabledProfiles, "two")
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <profiles>
                         <profile>
                           <id>one</id>
                         </profile>
                       </profiles>
                       
                       """.trimIndent())
    update(myProjectPom)
    assertUnorderedElementsAreEqual(
      tree.explicitProfiles.enabledProfiles, "one", "two")
  }

  @Test
  @Throws(IOException::class)
  fun testDeletingAndRestoringActiveProfilesWhenProjectDeletes() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <profiles>
                         <profile>
                           <id>one</id>
                         </profile>
                       </profiles>
                       <modules>
                         <module>m</module>
                       </modules>
                       
                       """.trimIndent())
    var m = createModulePom("m",
                            """
                                      <groupId>test</groupId>
                                      <artifactId>m</artifactId>
                                      <version>1</version>
                                      <profiles>
                                        <profile>
                                          <id>two</id>
                                        </profile>
                                      </profiles>
                                      
                                      """.trimIndent())
    updateAll(mutableListOf<String?>("one", "two"), myProjectPom)
    assertUnorderedElementsAreEqual(
      tree.explicitProfiles.enabledProfiles, "one", "two")
    val finalM = m
    WriteCommandAction.writeCommandAction(myProject).run<IOException> {
      finalM.delete(this)
      deleteProject(finalM)
    }
    assertUnorderedElementsAreEqual(
      tree.explicitProfiles.enabledProfiles, "one")
    m = createModulePom("m",
                        """
                          <groupId>test</groupId>
                          <artifactId>m</artifactId>
                          <version>1</version>
                          <profiles>
                            <profile>
                              <id>two</id>
                            </profile>
                          </profiles>
                          
                          """.trimIndent())
    update(m)
    assertUnorderedElementsAreEqual(
      tree.explicitProfiles.enabledProfiles, "one", "two")
  }

  @Test
  fun testFindRootWithMultiLevelAggregator() {
    val p1 = createModulePom("project1", """
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
    val p2 = createModulePom("project2", """
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
    val m = createModulePom("module", """
      <groupId>test</groupId>
      <artifactId>module</artifactId>
      <version>1</version>
      """
      .trimIndent()
    )
    updateAll(p1, p2, m)
    val roots = tree.rootProjects
    assertEquals(1, roots.size)
    val p1Project = roots[0]
    assertEquals(p1, p1Project.file)
    assertEquals(p1Project, tree.findRootProject(p1Project))
    assertEquals(1, tree.getModules(p1Project).size)
    val p2Project = tree.getModules(p1Project)[0]
    assertEquals(p2, p2Project.file)
    assertEquals(p1Project, tree.findRootProject(p2Project))
    assertEquals(1, tree.getModules(p2Project).size)
    val mProject = tree.getModules(p2Project)[0]
    assertEquals(m, mProject.file)
    assertEquals(p1Project, tree.findRootProject(mProject))
    assertEquals(0, tree.getModules(mProject).size)
  }

  @Test
  @Throws(Exception::class)
  fun testOutputPathsAreBasedOnTargetPathWhenResolving() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <directory>my-target</directory>
                       </build>
                       
                       """.trimIndent())
    updateAll(myProjectPom)
    val project = tree.rootProjects[0]
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("my-target"), project.buildDirectory)
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("my-target/classes"), project.outputDirectory)
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("my-target/test-classes"), project.testOutputDirectory)
    val embeddersManager = MavenEmbeddersManager(myProject)
    try {
      resolve(myProject,
              project,
              mavenGeneralSettings,
              embeddersManager,
              NULL_MAVEN_CONSOLE,
              mavenProgressIndicator)
    }
    finally {
      embeddersManager.releaseInTests()
    }
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("my-target"), project.buildDirectory)
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("my-target/classes"), project.outputDirectory)
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("my-target/test-classes"), project.testOutputDirectory)
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
