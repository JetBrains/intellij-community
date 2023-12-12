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
package org.jetbrains.idea.maven.dom

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.openapi.application.EDT
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.ElementManipulators
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.dom.inspections.MavenParentMissedVersionInspection
import org.jetbrains.idea.maven.dom.inspections.MavenPropertyInParentInspection
import org.jetbrains.idea.maven.dom.inspections.MavenRedundantGroupIdInspection
import org.junit.Test

class MavenParentCompletionAndResolutionTest : MavenDomWithIndicesTestCase() {
  @Test
  fun testVariants() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <parent>
                         <groupId><caret></groupId>
                         <artifactId>junit</artifactId>
                         <version></version>
                       </parent>
                       """.trimIndent())
    assertCompletionVariantsInclude(myProjectPom, RENDERING_TEXT, "junit")

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <parent>
                         <groupId>junit</groupId>
                         <artifactId><caret></artifactId>
                       </parent>
                       """.trimIndent())
    assertCompletionVariants(myProjectPom, RENDERING_TEXT, "junit")

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <parent>
                         <groupId>junit</groupId>
                         <artifactId>junit</artifactId>
                         <version><caret></version>
                       </parent>
                       """.trimIndent())
    assertCompletionVariants(myProjectPom, RENDERING_TEXT, "3.8.1", "3.8.2", "4.0")
  }

  @Test
  fun testResolutionInsideTheProject() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """.trimIndent())

    val m = createModulePom("m",
                            """
                                      <groupId>test</groupId>
                                      <artifactId>m</artifactId>
                                      <version>1</version>
                                      """.trimIndent())

    importProjectsAsync(myProjectPom, m)

    createModulePom("m", """
      <groupId>test</groupId>
      <artifactId>m</artifactId>
      <version>1</version>
      <parent>
        <groupId><caret>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
      </parent>
      """.trimIndent())

    withContext(Dispatchers.EDT) {
      assertResolved(m, findPsiFile(myProjectPom))
    }
  }

  @Test
  fun testResolutionOutsideOfTheProject() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <parent>
                         <groupId><caret>junit</groupId>
                         <artifactId>junit</artifactId>
                         <version>4.0</version>
                       </parent>
                       """.trimIndent())

    val filePath = myIndicesFixture!!.repositoryHelper.getTestDataPath("local1/junit/junit/4.0/junit-4.0.pom")
    val f = LocalFileSystem.getInstance().findFileByPath(filePath)

    withContext(Dispatchers.EDT) {
      assertResolved(myProjectPom, findPsiFile(f))
    }
  }

  @Test
  fun testResolvingByRelativePath() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <parent>
                         <groupId><caret>test</groupId>
                         <artifactId>parent</artifactId>
                         <version>1</version>
                         <relativePath>parent/pom.xml</relativePath>
                       </parent>
                       """.trimIndent())

    val parent = createModulePom("parent",
                                 """
                                           <groupId>test</groupId>
                                           <artifactId>parent</artifactId>
                                           <version>1</version>
                                           """.trimIndent())

    withContext(Dispatchers.EDT) {
      assertResolved(myProjectPom, findPsiFile(parent))
    }
  }

  @Test
  fun testResolvingByRelativePathWithProperties() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <properties>
                         <parentPath>parent/pom.xml</parentPath>
                       </properties>
                       <parent>
                         <groupId><caret>test</groupId>
                         <artifactId>parent</artifactId>
                         <version>1</version>
                         <relativePath>${'$'}{parentPath}</relativePath>
                       </parent>
                       """.trimIndent())

    val parent = createModulePom("parent",
                                 """
                                           <groupId>test</groupId>
                                           <artifactId>parent</artifactId>
                                           <version>1</version>
                                           """.trimIndent())

    withContext(Dispatchers.EDT) {
      assertResolved(myProjectPom, findPsiFile(parent))
    }
  }

  @Test
  fun testResolvingByRelativePathWhenOutsideOfTheProject() = runBlocking {
    val parent = createPomFile(myProjectRoot.getParent(),
                               """
                                         <groupId>test</groupId>
                                         <artifactId>project</artifactId>
                                         <version>1</version>
                                         """.trimIndent())

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <parent>
                         <groupId><caret>test</groupId>
                         <artifactId>parent</artifactId>
                         <version>1</version>
                         <relativePath>../pom.xml</relativePath>
                       </parent>
                       """.trimIndent())

    withContext(Dispatchers.EDT) {
      assertResolved(myProjectPom, findPsiFile(parent))
    }
  }

  @Test
  fun testDoNotHighlightResolvedParentByRelativePathWhenOutsideOfTheProject() = runBlocking {
    createPomFile(myProjectRoot.getParent(),
                  """
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    val projectPom = createProjectPom("""
                                                <groupId>test</groupId>
                                                <artifactId>project</artifactId>
                                                <version>1</version>
                                                """.trimIndent())

    importProjectAsync()

    setPomContent(projectPom,
                  """
                    <warning descr="Definition of groupId is redundant, because it's inherited from the parent"><groupId>test</groupId></warning>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <parent>
                      <groupId>test</groupId>
                      <artifactId>parent</artifactId>
                      <version>1</version>
                      <relativePath>../pom.xml</relativePath>
                    </parent>
                    """.trimIndent())

    fixture.enableInspections(MavenRedundantGroupIdInspection::class.java)
    checkHighlighting()
  }

  @Test
  fun testHighlightParentProperties() = runBlocking {
    assumeVersionMoreThan("3.5.0")

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project0</artifactId>
                       <version>1.${'$'}{revision}</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       <properties>
                         <revision>0</revision>
                         <anotherProperty>0</anotherProperty>
                       </properties>
                       """.trimIndent())

    val m1 = createModulePom("m1",
                             """
                                      <parent>
                                      <groupId>test</groupId>
                                      <artifactId>project0</artifactId>
                                      <version>1.${'$'}{revision}</version>
                                      </parent>
                                      <artifactId>m1</artifactId>
                                      """.trimIndent())

    var m2 = createModulePom("m2",
                             """
                                       <parent>
                                       <groupId>test</groupId>
                                       <artifactId>project0</artifactId>
                                       <version>1.${'$'}{revision}</version>
                                       </parent>
                                       <artifactId>m1</artifactId>
                                       """.trimIndent())

    importProjectAsync()

    m2 = createModulePom("m2",
                         """
                           <parent>
                           <groupId>test</groupId>
                           <artifactId><error descr="Properties in parent definition are prohibited">project${'$'}{anotherProperty}</error></artifactId>
                           <version>1.${'$'}{revision}</version>
                           </parent>
                           <artifactId>m1</artifactId>
                           """.trimIndent())

    fixture.enableInspections(listOf<Class<out LocalInspectionTool?>>(MavenPropertyInParentInspection::class.java))
    checkHighlighting(m2)
  }

  @Test
  fun testHighlightParentPropertiesForMavenLess35() = runBlocking {
    assumeVersionLessThan("3.5.0")

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project0</artifactId>
                       <version>1.${'$'}{revision}</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       <properties>
                         <revision>0</revision>
                         <anotherProperty>0</anotherProperty>
                       </properties>
                       """.trimIndent())

    createModulePom("m1",
                    """
                      <parent>
                      <groupId>test</groupId>
                      <artifactId>project0</artifactId>
                      <version>1.${'$'}{revision}</version>
                      </parent>
                      <artifactId>m1</artifactId>
                      """.trimIndent())

    createModulePom("m2",
                    """
                      <parent>
                      <groupId>test</groupId>
                      <artifactId>project0</artifactId>
                      <version>1.${'$'}{revision}</version>
                      </parent>
                      <artifactId>m1</artifactId>
                      """.trimIndent())

    importProjectAsync()

    val m2 = createModulePom("m2",
                             """
                                       <parent>
                                       <groupId>test</groupId>
                                       <artifactId><error descr="Properties in parent definition are prohibited">project${'$'}{anotherProperty}</error></artifactId>
                                       <version><error descr="Properties in parent definition are prohibited">1.${'$'}{revision}</error></version>
                                       </parent>
                                       <artifactId>m1</artifactId>
                                       """.trimIndent())

    fixture.enableInspections(listOf<Class<out LocalInspectionTool?>>(MavenPropertyInParentInspection::class.java))
    checkHighlighting(m2)
  }

  @Test
  fun testRelativePathCompletion() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <parent>
                         <groupId>test</groupId>
                         <artifactId>parent</artifactId>
                         <version>1</version>
                         <relativePath><caret></relativePath>
                       </parent>
                       """.trimIndent())

    createModulePom("dir/one",
                    """
                      <groupId>test</groupId>
                      <artifactId>one</artifactId>
                      <version>1</version>
                      """.trimIndent())

    createModulePom("two",
                    """
                      <groupId>test</groupId>
                      <artifactId>two</artifactId>
                      <version>1</version>
                      """.trimIndent())

    assertCompletionVariants(myProjectPom, "dir", "two", "pom.xml")
  }

  @Test
  fun testRelativePathCompletion_2() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <parent>
                         <groupId>test</groupId>
                         <artifactId>parent</artifactId>
                         <version>1</version>
                         <relativePath>dir/<caret></relativePath>
                       </parent>
                       """.trimIndent())

    createModulePom("dir/one", """
      <groupId>test</groupId>
      <artifactId>one</artifactId>
      <version>1</version>
      """.trimIndent())

    createModulePom("dir/two", """
      <groupId>test</groupId>
      <artifactId>two</artifactId>
      <version>1</version>
      """.trimIndent())
    createModulePom("dir", """
      <groupId>test</groupId>
      <artifactId>two</artifactId>
      <version>1</version>
      """.trimIndent())

    assertCompletionVariants(myProjectPom, "one", "two", "pom.xml")
  }

  @Test
  fun testHighlightingUnknownValues() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <parent>
                         <groupId><error>xxx</error></groupId>
                         <artifactId><error>xxx</error></artifactId>
                         <version><error>xxx</error></version>
                       </parent>
                       """.trimIndent())

    checkHighlighting()
  }

  @Test
  fun testHighlightingAbsentGroupId() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <<error descr="'groupId' child tag should be defined">parent</error>>
                         <artifactId><error>junit</error></artifactId>
                         <version><error>4.0</error></version>
                       </parent>
                       """.trimIndent())
    importProjectWithErrors()
    checkHighlighting()
  }

  @Test
  fun testHighlightingAbsentArtifactId() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <<error descr="'artifactId' child tag should be defined">parent</error>>
                         <groupId>junit</groupId>
                         <version><error>4.0</error></version>
                       </parent>
                       """.trimIndent())
    importProjectWithErrors()
    checkHighlighting()
  }

  @Test
  fun testHighlightingAbsentVersion() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <<error descr="'version' child tag should be defined">parent</error>>
                         <groupId>junit</groupId>
                         <artifactId>junit</artifactId>
                       </parent>
                       """.trimIndent())
    importProjectWithErrors()

    fixture.enableInspections(MavenParentMissedVersionInspection::class.java)
    checkHighlighting()
  }

  @Test
  fun testHighlightingInvalidRelativePath() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <parent>
                         <groupId>junit</groupId>
                         <artifactId>junit</artifactId>
                         <version>4.0</version>
                         <relativePath><error>parent</error>/<error>pom.xml</error></relativePath>
                       </parent>
                       """.trimIndent())

    checkHighlighting()
  }

  @Test
  fun testPathQuickFixForInvalidValue() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """.trimIndent())

    val m = createModulePom("bar",
                            """
                                      <groupId>test</groupId>
                                      <artifactId>one</artifactId>
                                      <version>1</version>
                                      """.trimIndent())

    importProjectsAsync(myProjectPom, m)

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <parent>
                         <groupId>test</groupId>
                         <artifactId>one</artifactId>
                         <version>1</version>
                         <relativePath><caret>xxx</relativePath>
                       </parent>
                       """.trimIndent())

    withContext(Dispatchers.EDT) {
      val i = getIntentionAtCaret("Fix Relative Path")
      assertNotNull(i)

      fixture.launchAction(i!!)
      val el = getElementAtCaret(myProjectPom)!!

      assertEquals("bar/pom.xml", ElementManipulators.getValueText(el))
    }
  }

  @Test
  fun testDoNotShowPathQuickFixForValidPath() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """.trimIndent())

    val m = createModulePom("bar",
                            """
                                      <groupId>test</groupId>
                                      <artifactId>one</artifactId>
                                      <version>1</version>
                                      """.trimIndent())

    importProjects(myProjectPom, m)

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <parent>
                         <groupId>test</groupId>
                         <artifactId>one</artifactId>
                         <version>1</version>
                         <relativePath><caret>bar/pom.xml</relativePath>
                       </parent>
                       """.trimIndent())

    assertNull(getIntentionAtCaret("Fix relative path"))
  }
}
