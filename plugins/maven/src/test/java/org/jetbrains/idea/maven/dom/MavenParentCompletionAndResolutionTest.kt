// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.maven.testFramework.fixtures.MavenDomTestFixtureIndices
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assumeMaven3
import com.intellij.maven.testFramework.fixtures.assumeMaven4
import com.intellij.maven.testFramework.fixtures.assumeVersionLessThan
import com.intellij.maven.testFramework.fixtures.assumeVersionMoreThan
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createPomFile
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.findPsiFile
import com.intellij.maven.testFramework.fixtures.getElementAtCaret
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.importProjectsAsync
import com.intellij.maven.testFramework.fixtures.mavenDomFixture
import com.intellij.maven.testFramework.fixtures.moveCaretTo
import com.intellij.maven.testFramework.fixtures.projectRoot
import com.intellij.maven.testFramework.fixtures.runBlockingNoSync
import com.intellij.maven.testFramework.fixtures.setPomContent
import com.intellij.maven.testFramework.fixtures.updateProjectPom
import com.intellij.maven.testFramework.fixtures.withoutSync
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.dom.inspections.MavenParentMissedGroupIdArtifactIdInspection
import org.jetbrains.idea.maven.dom.inspections.MavenParentMissedVersionInspection
import org.jetbrains.idea.maven.dom.inspections.MavenPropertyInParentInspection
import org.jetbrains.idea.maven.dom.inspections.MavenRedundantGroupIdInspection
import org.jetbrains.idea.maven.fixtures.assertCompletionVariants
import org.jetbrains.idea.maven.fixtures.assertCompletionVariantsInclude
import org.jetbrains.idea.maven.fixtures.assertResolved
import org.jetbrains.idea.maven.fixtures.checkHighlighting
import org.jetbrains.idea.maven.fixtures.getIntentionAtCaret
import org.jetbrains.idea.maven.utils.MavenLog
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenParentCompletionAndResolutionTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenDomFixture(
    mavenVersion = mavenVersion, modelVersion = modelVersion,
    indices = MavenDomTestFixtureIndices("local1", listOf("local2")),
  )

  @Test
  fun testVariants() = maven.runBlockingNoSync {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <parent>
                         <groupId><caret></groupId>
                         <artifactId>junit</artifactId>
                         <version></version>
                       </parent>
                       """.trimIndent())
    maven.assertCompletionVariantsInclude(maven.projectPom, maven.RENDERING_TEXT, "junit")

    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <parent>
                         <groupId>junit</groupId>
                         <artifactId><caret></artifactId>
                       </parent>
                       """.trimIndent())
    maven.assertCompletionVariants(maven.projectPom, maven.RENDERING_TEXT, "junit")

    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <parent>
                         <groupId>junit</groupId>
                         <artifactId>junit</artifactId>
                         <version><caret></version>
                       </parent>
                       """.trimIndent())
    maven.assertCompletionVariants(maven.projectPom, maven.RENDERING_TEXT, "3.8.1", "3.8.2", "4.0")
  }

  @Test
  fun testResolutionInsideTheProject() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """.trimIndent())

    val m = maven.createModulePom("m",
                            """
                                      <groupId>test</groupId>
                                      <artifactId>m</artifactId>
                                      <version>1</version>
                                      """.trimIndent())

    maven.importProjectsAsync(maven.projectPom, m)

    maven.withoutSync {
      maven.createModulePom("m", """
      <groupId>test</groupId>
      <artifactId>m</artifactId>
      <version>1</version>
      <parent>
        <groupId><caret>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
      </parent>
      """.trimIndent())

      maven.assertResolved(m, maven.findPsiFile(maven.projectPom))
    }

  }

  @Test
  fun testResolutionOutsideOfTheProject() = maven.runBlockingNoSync {

    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <parent>
                         <groupId><caret>junit</groupId>
                         <artifactId>junit</artifactId>
                         <version>4.0</version>
                       </parent>
                       """.trimIndent())

    val filePath = maven.repositoryHelper.getTestData("local1/junit/junit/4.0/junit-4.0.pom")
    val f = LocalFileSystem.getInstance().findFileByNioFile(filePath)

    maven.assertResolved(maven.projectPom, maven.findPsiFile(f))
  }

  @Test
  fun testResolvingByRelativePath() = maven.runBlockingNoSync {

    maven.createProjectPom("""
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

    val parent = maven.createModulePom("parent",
                                 """
                                           <groupId>test</groupId>
                                           <artifactId>parent</artifactId>
                                           <version>1</version>
                                           """.trimIndent())

    maven.assertResolved(maven.projectPom, maven.findPsiFile(parent))
  }

  @Test
  fun testResolvingByRelativePathWithProperties() = maven.runBlockingNoSync {
    maven.projectsManager.initForTests()
    val parent = maven.createModulePom("parent",
                                 """
                                           <groupId>test</groupId>
                                           <artifactId>parent</artifactId>
                                           <version>1</version>
                                           """.trimIndent())

    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <properties>
                         <parentPath>parent/pom.xml</parentPath>
                       </properties>
                       <parent>
                         <groupId>test</groupId>
                         <artifactId>parent</artifactId>
                         <version>1</version>
                         <relativePath>${'$'}{parentPath}</relativePath>
                       </parent>
                       """.trimIndent())

    maven.moveCaretTo(maven.projectPom, """
      <parent>
        <groupId><caret>test</groupId>""".trimIndent())
    maven.assertResolved(maven.projectPom, maven.findPsiFile(parent))
  }

  @Test
  fun testResolvingByRelativePathWhenOutsideOfTheProject() = runBlocking {
    val parent = maven.createPomFile(maven.projectRoot.getParent(),
                               """
                                         <groupId>test</groupId>
                                         <artifactId>project</artifactId>
                                         <version>1</version>
                                         """.trimIndent())

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    maven.withoutSync {
      maven.createProjectPom("""
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

      maven.assertResolved(maven.projectPom, maven.findPsiFile(parent))
    }

  }

  @Test
  fun testDoNotHighlightResolvedParentByRelativePathWhenOutsideOfTheProject() = runBlocking {
    maven.createPomFile(maven.projectRoot.getParent(),
                  """
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    val projectPom = maven.createProjectPom("""
                                                <groupId>test</groupId>
                                                <artifactId>project</artifactId>
                                                <version>1</version>
                                                """.trimIndent())

    maven.importProjectAsync()

    maven.withoutSync {
      maven.setPomContent(projectPom,
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

      maven.fixture.enableInspections(MavenRedundantGroupIdInspection::class.java)
      maven.checkHighlighting()
    }

  }

  @Test
  fun testHighlightParentProperties() = runBlocking {
    maven.assumeVersionMoreThan("3.5.0")

    maven.createProjectPom("""
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

    val m1 = maven.createModulePom("m1",
                             """
                                      <parent>
                                      <groupId>test</groupId>
                                      <artifactId>project0</artifactId>
                                      <version>1.${'$'}{revision}</version>
                                      </parent>
                                      <artifactId>m1</artifactId>
                                      """.trimIndent())

    var m2 = maven.createModulePom("m2",
                             """
                                       <parent>
                                       <groupId>test</groupId>
                                       <artifactId>project0</artifactId>
                                       <version>1.${'$'}{revision}</version>
                                       </parent>
                                       <artifactId>m1</artifactId>
                                       """.trimIndent())

    maven.importProjectAsync()

    maven.withoutSync {
      m2 = maven.createModulePom("m2",
                           """
                           <parent>
                           <groupId>test</groupId>
                           <artifactId><error descr="Properties in parent definition are prohibited">project${'$'}{anotherProperty}</error></artifactId>
                           <version>1.${'$'}{revision}</version>
                           </parent>
                           <artifactId>m1</artifactId>
                           """.trimIndent())

      maven.fixture.enableInspections(listOf<Class<out LocalInspectionTool?>>(MavenPropertyInParentInspection::class.java))
      maven.checkHighlighting(m2)
    }

  }

  @Test
  fun testHighlightParentPropertiesForMavenLess35() = runBlocking {
    maven.assumeVersionLessThan("3.5.0")

    maven.createProjectPom("""
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

    maven.createModulePom("m1",
                    """
                      <parent>
                      <groupId>test</groupId>
                      <artifactId>project0</artifactId>
                      <version>1.${'$'}{revision}</version>
                      </parent>
                      <artifactId>m1</artifactId>
                      """.trimIndent())

    maven.createModulePom("m2",
                    """
                      <parent>
                      <groupId>test</groupId>
                      <artifactId>project0</artifactId>
                      <version>1.${'$'}{revision}</version>
                      </parent>
                      <artifactId>m1</artifactId>
                      """.trimIndent())

    maven.importProjectAsync()

    val m2 = maven.createModulePom("m2",
                             """
                                       <parent>
                                       <groupId>test</groupId>
                                       <artifactId><error descr="Properties in parent definition are prohibited">project${'$'}{anotherProperty}</error></artifactId>
                                       <version><error descr="Properties in parent definition are prohibited">1.${'$'}{revision}</error></version>
                                       </parent>
                                       <artifactId>m1</artifactId>
                                       """.trimIndent())

    maven.fixture.enableInspections(listOf<Class<out LocalInspectionTool?>>(MavenPropertyInParentInspection::class.java))
    maven.checkHighlighting(m2)
  }

  @Test
  fun testRelativePathCompletion() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    maven.createProjectPom("""
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

    maven.createModulePom("dir/one",
                    """
                      <groupId>test</groupId>
                      <artifactId>one</artifactId>
                      <version>1</version>
                      """.trimIndent())

    maven.createModulePom("two",
                    """
                      <groupId>test</groupId>
                      <artifactId>two</artifactId>
                      <version>1</version>
                      """.trimIndent())

    maven.assertCompletionVariants(maven.projectPom, "dir", "two", "pom.xml")
  }

  @Test
  fun testRelativePathCompletion_2() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    maven.createProjectPom("""
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

    maven.createModulePom("dir/one", """
      <groupId>test</groupId>
      <artifactId>one</artifactId>
      <version>1</version>
      """.trimIndent())

    maven.createModulePom("dir/two", """
      <groupId>test</groupId>
      <artifactId>two</artifactId>
      <version>1</version>
      """.trimIndent())
    maven.createModulePom("dir", """
      <groupId>test</groupId>
      <artifactId>two</artifactId>
      <version>1</version>
      """.trimIndent())

    maven.assertCompletionVariants(maven.projectPom, "one", "two", "pom.xml")
  }

  @Test
  fun testHighlightingUnknownValues() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <parent>
                         <groupId><error>xxx</error></groupId>
                         <artifactId><error>xxx</error></artifactId>
                         <version><error>xxx</error></version>
                       </parent>
                       """.trimIndent())

    maven.checkHighlighting()
  }

  private class LoggingLocalInspectionTool : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
      MavenLog.LOG.warn("Creating visitor")
      return PsiFileElementVisitor()
    }

    class PsiFileElementVisitor : PsiElementVisitor() {
      override fun visitFile(psiFile: PsiFile) {
        MavenLog.LOG.warn("Visiting file $psiFile, text ${psiFile.text}")
      }
    }
  }

  @Test
  fun testHighlightingAbsentGroupId() = runBlocking {
    // Both 3 and 4 Maven versions require the presence of <groupId> if relativePath (default ../pom.xml) does not lead to the parent POM
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <<error descr="'groupId' child tag should be defined">parent</error>>
                         <artifactId>jupiter</artifactId>
                         <version>4.0</version>
                       </parent>
                       """.trimIndent())

    maven.fixture.enableInspections(MavenParentMissedGroupIdArtifactIdInspection::class.java)
    maven.checkHighlighting()
  }

  @Test
  fun testHighlightingAbsentArtifactId() = runBlocking {
    // Both 3 and 4 Maven versions require the presence of <artifactId> if relativePath (default ../pom.xml) does not lead to the parent POM
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <<error descr="'artifactId' child tag should be defined">parent</error>>
                         <groupId>junit</groupId>
                         <version>4.0</version>
                       </parent>
                       """.trimIndent())

    maven.fixture.enableInspections(MavenParentMissedGroupIdArtifactIdInspection::class.java)
    maven.checkHighlighting()
  }

  @Test
  fun testHighlightingMaven3AbsentArtifactId() = runBlocking {
    maven.assumeMaven3()
    maven.createProjectPom("""
                      <groupId>test</groupId>
                      <artifactId>project</artifactId>
                      <version>1</version>
                      """.trimIndent())
    // In Maven 3, <groupId> and <artifactId> in <parent> are required even if the parent's POM could be found by relativePath.
    val subprojectPom = maven.createModulePom("subdirectory", """
                      <<error descr="'artifactId' child tag should be defined">parent</error>>
                         <groupId>test</groupId> 
                         <version>1</version>
                         <relativePath>../pom.xml</relativePath>
                      </parent>
                      <artifactId>subproject1</artifactId>
                      """.trimIndent())
    maven.fixture.enableInspections(MavenParentMissedGroupIdArtifactIdInspection::class.java)
    maven.checkHighlighting(subprojectPom)
  }

  @Test
  fun testHighlightingMaven3AbsentGroupId() = runBlocking {
    maven.assumeMaven3()
    maven.createProjectPom("""
                      <groupId>test</groupId>
                      <artifactId>project</artifactId>
                      <version>1</version>
                      """.trimIndent())
    // In Maven 3, <groupId> and <artifactId> in <parent> are required even if the parent's POM could be found by relativePath.
    val subprojectPom = maven.createModulePom("subdirectory", """
                      <<error descr="'groupId' child tag should be defined">parent</error>>
                         <artifactId>project</artifactId>
                         <version>1</version>
                         <relativePath>../pom.xml</relativePath>
                       </parent>
                      <artifactId>subproject1</artifactId>
                      """.trimIndent())
    maven.fixture.enableInspections(MavenParentMissedGroupIdArtifactIdInspection::class.java)
    maven.checkHighlighting(subprojectPom)
  }

  @Test
  fun testHighlightingMaven4AbsentGroupIdArtefactId() = runBlocking {
    maven.assumeMaven4()
    maven.createProjectPom("""
                      <groupId>test</groupId>
                      <artifactId>project</artifactId>
                      <version>1</version>
                      """.trimIndent())
    // In Maven 4, it's possible to omit <groupId> and <artifactId> in <parent> if the parent's POM could be found by relativePath.
    val subprojectPom = maven.createModulePom("sub/subdirectory", """
                      <parent>
                          <relativePath>../../pom.xml</relativePath>
                      </parent>
                      <artifactId>subproject1</artifactId>
                      """.trimIndent())
    maven.fixture.enableInspections(MavenParentMissedGroupIdArtifactIdInspection::class.java)
    maven.checkHighlighting(subprojectPom)
  }

  @Test
  fun testHighlightingMaven4AbsentGroupIdArtefactId_2() = runBlocking {
    maven.assumeMaven4()
    maven.createProjectPom("""
                      <groupId>test</groupId>
                      <artifactId>project</artifactId>
                      <version>1</version>
                      """.trimIndent())
    // In Maven 4, it's possible to omit <groupId> and <artifactId> in <parent> if the parent's POM could be found by relativePath.
    // If <relativePath> is not specified, the default value is used (../pom.xml)
    val subprojectPom = maven.createModulePom("subdirectory", """
                      <parent/>
                      <artifactId>subproject1</artifactId>
                      """.trimIndent())
    maven.fixture.enableInspections(MavenParentMissedGroupIdArtifactIdInspection::class.java)
    maven.checkHighlighting(subprojectPom)
  }

  @Test
  fun testHighlightingAbsentVersion() = runBlocking {
    maven.assumeMaven3()
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <<error descr="'version' child tag should be defined">parent</error>>
                         <groupId>junit</groupId>
                         <artifactId>junit</artifactId>
                       </parent>
                       """.trimIndent())
    maven.fixture.enableInspections(MavenParentMissedVersionInspection::class.java)
    maven.checkHighlighting()
  }

  @Test
  fun testHighlightingInvalidRelativePath() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    maven.withoutSync {
      maven.createProjectPom("""
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

      maven.checkHighlighting()
    }
  }

  @Test
  fun testPathQuickFixForInvalidValue() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """.trimIndent())

    val m = maven.createModulePom("bar",
                            """
                                      <groupId>test</groupId>
                                      <artifactId>one</artifactId>
                                      <version>1</version>
                                      """.trimIndent())

    maven.importProjectsAsync(maven.projectPom, m)
    maven.withoutSync {
      maven.updateProjectPom("""
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

      val i = maven.getIntentionAtCaret("Fix Relative Path")
      assertNotNull(i)
      maven.fixture.launchAction(i!!)
      val el = maven.getElementAtCaret(maven.projectPom)!!

      assertEquals("bar/pom.xml", ElementManipulators.getValueText(el))
    }


  }

  @Test
  fun testDoNotShowPathQuickFixForValidPath() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """.trimIndent())

    val m = maven.createModulePom("bar",
                            """
                                      <groupId>test</groupId>
                                      <artifactId>one</artifactId>
                                      <version>1</version>
                                      """.trimIndent())

    maven.importProjectsAsync(maven.projectPom, m)

    maven.withoutSync {
      maven.createProjectPom("""
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

      assertNull(maven.getIntentionAtCaret("Fix relative path"))
    }
  }
}
