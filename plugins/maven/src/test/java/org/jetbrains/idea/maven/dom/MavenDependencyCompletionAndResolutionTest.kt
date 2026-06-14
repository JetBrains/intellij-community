// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom

import com.intellij.codeInsight.intention.IntentionActionDelegate
import com.intellij.maven.testFramework.fixtures.MavenCustomRepositoryHelper
import com.intellij.maven.testFramework.fixtures.MavenDomTestFixture
import com.intellij.maven.testFramework.fixtures.MavenDomTestFixtureIndices
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertModules
import com.intellij.maven.testFramework.fixtures.configConfirmationForYesAnswer
import com.intellij.maven.testFramework.fixtures.configureProjectPom
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.findPsiFile
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.importProjectsAsync
import com.intellij.maven.testFramework.fixtures.importProjectsWithErrors
import com.intellij.maven.testFramework.fixtures.mavenDomFixture
import com.intellij.maven.testFramework.fixtures.mn
import com.intellij.maven.testFramework.fixtures.projectRoot
import com.intellij.maven.testFramework.fixtures.updateAllProjects
import com.intellij.maven.testFramework.fixtures.updateModulePom
import com.intellij.maven.testFramework.fixtures.updateProjectPom
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.formatter.xml.XmlCodeStyleSettings
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.dom.converters.MavenDependencyCompletionUtil
import org.jetbrains.idea.maven.dom.intentions.ChooseFileIntentionAction
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel
import org.jetbrains.idea.maven.fixtures.assertCompletionVariants
import org.jetbrains.idea.maven.fixtures.assertCompletionVariantsInclude
import org.jetbrains.idea.maven.fixtures.assertCompletionVariantsNoCache
import org.jetbrains.idea.maven.fixtures.assertResolved
import org.jetbrains.idea.maven.fixtures.checkHighlighting
import org.jetbrains.idea.maven.fixtures.getCompletionVariants
import org.jetbrains.idea.maven.fixtures.getIntentionAtCaret
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import java.io.IOException

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenDependencyCompletionAndResolutionTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenDomFixture(
    mavenVersion = mavenVersion, modelVersion = modelVersion,
    initialPom = MavenDomTestFixture.DEFAULT_POM,
    indices = MavenDomTestFixtureIndices("local1", listOf("local2")),
  )

  @Test
  fun testGroupIdCompletion() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId><caret></groupId>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    maven.assertCompletionVariantsInclude(maven.projectPom, maven.RENDERING_TEXT, "junit", "jmock", "test")
  }

  @Test
  fun testArtifactIdCompletion() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId>junit</groupId>
                           <artifactId><caret></artifactId>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    maven.assertCompletionVariants(maven.projectPom, maven.RENDERING_TEXT, "junit")
  }

  @Test
  fun testDoNotCompleteArtifactIdOnUnknownGroup() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId>unknown</groupId>
                           <artifactId><caret></artifactId>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    maven.assertCompletionVariants(maven.projectPom)
  }

  @Test
  fun testVersionCompletion() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId>junit</groupId>
                           <artifactId>junit</artifactId>
                           <version><caret></version>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    val variants = maven.getCompletionVariants(maven.projectPom)
    assertEquals(mutableListOf("4.0", "3.8.2", "3.8.1"), variants)
  }

  @Test
  fun testDoNotCompleteVersionIfNoGroupIdAndArtifactId() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <version><caret></version>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    maven.assertCompletionVariants(maven.projectPom) // should not throw
  }

  @Test
  fun testAddingLocalProjectsIntoCompletion() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>project-group</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                        <module>m1</module>
                        <module>m2</module>
                       </modules>
                       """.trimIndent())

    maven.createModulePom("m1",
                          """
                      <groupId>project-group</groupId>
                      <artifactId>m1</artifactId>
                      <version>1</version>
                      """.trimIndent())

    val m = maven.createModulePom("m2",
                                  """
                                      <groupId>project-group</groupId>
                                      <artifactId>m2</artifactId>
                                      <version>2</version>
                                      """.trimIndent())

    maven.importProjectAsync()

    maven.createModulePom("m2", """
      <groupId>project-group</groupId>
      <artifactId>m2</artifactId>
      <version>2</version>
      <dependencies>
        <dependency>
          <groupId>project-group</groupId>
          <artifactId><caret></artifactId>
        </dependency>
      </dependencies>
      """.trimIndent())

    maven.assertCompletionVariants(m, maven.RENDERING_TEXT, "project", "m1", "m2")
  }

  @Test
  fun testResolvingPropertiesForLocalProjectsInCompletion() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                        <module>m1</module>
                        <module>m2</module>
                       </modules>
                       """.trimIndent())

    maven.createModulePom("m1",
                          """
                      <artifactId>module1</artifactId>
                      <parent>
                        <groupId>test</groupId>
                        <artifactId>project</artifactId>
                        <version>1</version>
                      </parent>
                      """.trimIndent())

    val m = maven.createModulePom("m2",
                                  """
                                      <groupId>test</groupId>
                                      <artifactId>module2</artifactId>
                                      <version>1</version>
                                      """.trimIndent())

    maven.importProjectAsync()
    maven.assertModules("project", maven.mn("project", "module1"), "module2")

    maven.updateModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>module2</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>test</groupId>
          <artifactId>module1</artifactId>
          <version><caret></version>
        </dependency>
      </dependencies>
      """.trimIndent())

    maven.assertCompletionVariants(m, "1")

    maven.updateModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>module2</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>test</groupId>
          <artifactId>module1</artifactId>
          <version>1</version>
        </dependency>
      </dependencies>
      """.trimIndent())

    maven.checkHighlighting(m)
  }

  @Test
  fun testChangingExistingProjects() = runBlocking {
    maven.updateProjectPom("""
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

    maven.createModulePom("m2",
                          """
                      <groupId>test</groupId>
                      <artifactId>m2</artifactId>
                      <version>1</version>
                      """.trimIndent())
    maven.importProjectAsync()

    maven.updateModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>test</groupId>
          <artifactId><caret></artifactId>
        </dependency>
      </dependencies>
      """.trimIndent())

    maven.assertCompletionVariants(m1, maven.RENDERING_TEXT, "project", "m1", "m2")

    maven.updateModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      """.trimIndent())

    maven.updateModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2_new</artifactId>
      <version>1</version>
      """.trimIndent())

    maven.updateAllProjects()

    maven.updateModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>test</groupId>
          <artifactId><caret></artifactId>
        </dependency>
      </dependencies>
      """.trimIndent())

    maven.assertCompletionVariantsNoCache(m1, maven.RENDERING_TEXT, "project", "m1", "m2_new")
  }

  @Test
  fun testChangingExistingProjectsWithArtifactIdsRemoval() = runBlocking {
    val m = maven.createModulePom("m1",
                                  """
                                      <groupId>project-group</groupId>
                                      <artifactId>m1</artifactId>
                                      <version>1</version>
                                      """.trimIndent())

    maven.configureProjectPom("""
                          <groupId>test</groupId>
                          <artifactId>project</artifactId>
                          <version>1</version>
                          <dependencies>
                            <dependency>
                              <groupId>project-group</groupId>
                              <artifactId><caret></artifactId>
                            </dependency>
                          </dependencies>
                          """.trimIndent())

    maven.importProjectsWithErrors(maven.projectPom, m)

    maven.assertCompletionVariants(maven.projectPom, maven.RENDERING_TEXT, "m1")

    maven.updateModulePom("m1", "")
    maven.importProjectsWithErrors(maven.projectPom, m)

    maven.configureProjectPom("""
                          <groupId>test</groupId>
                          <artifactId>project</artifactId>
                          <version>1</version>
                          <dependencies>
                            <dependency>
                              <groupId>project-group</groupId>
                              <artifactId><caret></artifactId>
                            </dependency>
                          </dependencies>
                          """.trimIndent())

    maven.assertCompletionVariantsNoCache(maven.projectPom, maven.RENDERING_TEXT)
  }

  @Test
  fun testRemovingExistingProjects() = runBlocking {
    val m1 = maven.createModulePom("m1",
                                   """
                                             <groupId>project-group</groupId>
                                             <artifactId>m1</artifactId>
                                             <version>1</version>
                                             """.trimIndent())

    val m2 = maven.createModulePom("m2",
                                   """
                                             <groupId>project-group</groupId>
                                             <artifactId>m2</artifactId>
                                             <version>1</version>
                                             """.trimIndent())

    maven.configureProjectPom("""
                          <groupId>test</groupId>
                          <artifactId>project</artifactId>
                          <version>1</version>
                          <dependencies>
                            <dependency>
                              <groupId>project-group</groupId>
                              <artifactId><caret></artifactId>
                            </dependency>
                          </dependencies>
                          """.trimIndent())

    maven.importProjectsWithErrors(maven.projectPom, m1, m2)

    IndexingTestUtil.waitUntilIndexesAreReady(maven.project)

    maven.assertCompletionVariantsInclude(maven.projectPom, maven.RENDERING_TEXT, "m1", "m2")

    WriteAction.runAndWait<IOException> { m1.delete(null) }

    maven.configConfirmationForYesAnswer()

    maven.assertCompletionVariantsInclude(maven.projectPom, maven.RENDERING_TEXT, "m2")
  }

  @Test
  fun testResolutionOutsideTheProject() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId>junit</groupId>
                           <artifactId><caret>junit</artifactId>
                           <version>4.0</version>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    val filePath = maven.repositoryHelper.getTestData("local1/junit/junit/4.0/junit-4.0.pom")
    val f = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(filePath)
    maven.assertResolved(maven.projectPom, maven.findPsiFile(f))
  }

  @Test
  fun testResolutionParentPathOutsideTheProject() = runBlocking {
    val filePath = maven.repositoryHelper.getTestData("local1/org/example/example/1.0/example-1.0.pom")

    val relativePath = maven.projectRoot.toNioPath().relativize(filePath).toString()
    val relativePathUnixSeparator = relativePath.replace("\\\\".toRegex(), "/")

    maven.updateProjectPom("""<groupId>test</groupId>
<artifactId>project</artifactId>
<version>1</version>
<parent>
  <groupId>org.example</groupId>
  <artifactId>example</artifactId>
  <version>1.0</version>
  <relativePath>$relativePathUnixSeparator<caret></relativePath>
</parent>"""
    )

    val f = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(filePath)
    maven.assertResolved(maven.projectPom, maven.findPsiFile(f))
  }

  @Test
  fun testResolveManagedDependency() = runBlocking {
    maven.configureProjectPom("""
                          <groupId>test</groupId>
                          <artifactId>project</artifactId>
                          <version>1</version>
                          <dependencyManagement>
                            <dependencies>
                              <dependency>
                                <groupId>junit</groupId>
                                <artifactId>junit</artifactId>
                                <version>4.0</version>
                              </dependency>
                            </dependencies>
                          </dependencyManagement>
                          <dependencies>
                            <dependency>
                              <groupId>junit</groupId>
                              <artifactId>junit<caret></artifactId>
                            </dependency>
                          </dependencies>
                          """.trimIndent())
    maven.importProjectAsync()

    val filePath = maven.repositoryHelper.getTestData("local1/junit/junit/4.0/junit-4.0.pom")
    val f = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(filePath)
    maven.assertResolved(maven.projectPom, maven.findPsiFile(f))
  }

  @Test
  fun testResolveLATESTDependency() = runBlocking {
    val helper = MavenCustomRepositoryHelper(maven.dir, "local1")
    val repoPath = helper.getTestData("local1")
    maven.repositoryPath = repoPath

    maven.updateProjectPom("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <dependencies>
                      <dependency>
                        <groupId>junit</groupId>
                        <artifactId>junit</artifactId>
                        <version>[1,4.0]</version>
                      </dependency>
                    </dependencies>
                    """.trimIndent())
    maven.updateAllProjects()

    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId>junit</groupId>
                           <artifactId>junit<caret></artifactId>
                           <version>[1,4.0]</version>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    val filePath = maven.repositoryHelper.getTestData("local1/junit/junit/4.0/junit-4.0.pom")
    val f = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(filePath)

    maven.assertResolved(maven.projectPom, maven.findPsiFile(f))
  }

  @Test
  fun testResolutionIsTypeBased() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId>junit</groupId>
                           <artifactId><caret>junit</artifactId>
                           <version>4.0</version>
                           <type>pom</type>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    val filePath = maven.repositoryHelper.getTestData("local1/junit/junit/4.0/junit-4.0.pom")
    val f = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(filePath)

    maven.assertResolved(maven.projectPom, maven.findPsiFile(f))
  }

  @Test
  fun testResolutionInsideTheProject() = runBlocking {
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

    maven.importProjectsAsync(maven.projectPom, m1, m2)

    maven.createModulePom("m1",
                          """
                      <groupId>test</groupId>
                      <artifactId>m1</artifactId>
                      <version>1</version>
                      <dependencies>
                        <dependency>
                          <groupId>test</groupId>
                          <artifactId><caret>m2</artifactId>
                          <version>1</version>
                        </dependency>
                      </dependencies>
                      """.trimIndent())

    maven.assertResolved(m1, maven.findPsiFile(m2))
  }

  @Test
  fun testResolvingSystemScopeDependencies() = runBlocking {
    val libPath = maven.repositoryHelper.getTestData("local1/junit/junit/4.0/junit-4.0.jar")

    maven.updateProjectPom("""<groupId>test</groupId>
<artifactId>project</artifactId>
<version>1</version>
<dependencies>
  <dependency>
    <groupId>xxx</groupId>
    <artifactId>xxx</artifactId>
    <version><caret>xxx</version>
    <scope>system</scope>
    <systemPath>$libPath</systemPath>
  </dependency>
</dependencies>
""")

    maven.assertResolved(maven.projectPom, maven.findPsiFile(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(libPath)))
    maven.checkHighlighting()
  }

  @Test
  fun testHighlightInvalidSystemScopeDependencies() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId><error  descr="Dependency 'xxx:xxx:xxx' not found">xxx</error></groupId>
                           <artifactId><error  descr="Dependency 'xxx:xxx:xxx' not found">xxx</error></artifactId>
                           <version><error  descr="Dependency 'xxx:xxx:xxx' not found">xxx</error></version>
                           <scope>system</scope>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    maven.checkHighlighting()
  }

  @Test
  fun testDoNotHighlightValidSystemScopeDependencies() = runBlocking {
    val libPath = maven.repositoryHelper.getTestData("local1/junit/junit/4.0/junit-4.0.jar")

    maven.updateProjectPom("""<groupId>test</groupId>
<artifactId>project</artifactId>
<version>1</version>
<dependencies>
  <dependency>
    <groupId>xxx</groupId>
    <artifactId>xxx</artifactId>
    <version>xxx</version>
    <scope>system</scope>
    <systemPath>
$libPath</systemPath>
  </dependency>
</dependencies>
""")
    maven.checkHighlighting()
  }

  @Test
  fun testResolvingSystemScopeDependenciesWithProperties() = runBlocking {
    val libPath = maven.repositoryHelper.getTestData("local1/junit/junit/4.0/junit-4.0.jar")

    maven.updateProjectPom("""<groupId>test</groupId>
<artifactId>project</artifactId>
<version>1</version>
<properties>
  <depPath>
$libPath</depPath>
</properties>
<dependencies>
  <dependency>
    <groupId>xxx</groupId>
    <artifactId>xxx</artifactId>
    <version><caret>xxx</version>
    <scope>system</scope>
    <systemPath>${"$"}{depPath}</systemPath>
  </dependency>
</dependencies>
""")

    maven.assertResolved(maven.projectPom, maven.findPsiFile(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(libPath)))
    maven.checkHighlighting()
  }

  @Test
  fun testCompletionSystemScopeDependenciesWithProperties() = runBlocking {
    val libPath = maven.repositoryHelper.getTestData("local1/junit/junit/4.0/junit-4.0.jar")

    maven.updateProjectPom("""<groupId>test</groupId>
<artifactId>project</artifactId>
<version>1</version>
<properties>
  <depDir>
${libPath.parent}</depDir>
</properties>
<dependencies>
  <dependency>
    <groupId>xxx</groupId>
    <artifactId>xxx</artifactId>
    <version>xxx</version>
    <scope>system</scope>
    <systemPath>${"$"}{depDir}/<caret></systemPath>
  </dependency>
</dependencies>
""")

    maven.assertCompletionVariants(maven.projectPom, maven.RENDERING_TEXT, "junit-4.0.jar")
  }

  @Test
  fun testResolvingSystemScopeDependenciesFromSystemPath() = runBlocking {
    val libPath = maven.repositoryHelper.getTestData("local1/junit/junit/4.0/junit-4.0.jar")

    maven.updateProjectPom("""<groupId>test</groupId>
<artifactId>project</artifactId>
<version>1</version>
<dependencies>
  <dependency>
    <groupId>xxx</groupId>
    <artifactId>xxx</artifactId>
    <version>xxx</version>
    <scope>system</scope>
    <systemPath>$libPath<caret></systemPath>
  </dependency>
</dependencies>
""")

    maven.assertResolved(maven.projectPom, maven.findPsiFile(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(libPath)))
    maven.checkHighlighting()
  }

  @Test
  fun testChooseFileIntentionForSystemDependency() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency><caret>
                           <groupId>xxx</groupId>
                           <artifactId>xxx</artifactId>
                           <version>xxx</version>
                           <scope>system</system>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    val action = maven.getIntentionAtCaret("Choose File")
    assertNotNull(action)

    val libPath = maven.repositoryHelper.getTestData("local1/junit/junit/4.0/junit-4.0.jar")
    val libFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(libPath)

    val intentionAction = IntentionActionDelegate.unwrap(action!!)
    (intentionAction as ChooseFileIntentionAction).setFileChooser { arrayOf(libFile) }
    val xmlSettings =
      CodeStyleSettingsManager.getInstance(maven.project).getCurrentSettings().getCustomSettings(XmlCodeStyleSettings::class.java)

    val prevValue = xmlSettings.XML_TEXT_WRAP
    try {
      // prevent file path from wrapping.
      xmlSettings.XML_TEXT_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP
      maven.fixture.launchAction(action)
    }
    finally {
      xmlSettings.XML_TEXT_WRAP = prevValue
      intentionAction.setFileChooser(null)
    }

    val expectedValue = readAction {
      val model = MavenDomUtil.getMavenDomProjectModel(maven.project, maven.projectPom)
      val dep = model!!.getDependencies().getDependencies()[0]
      dep.getSystemPath().getValue()
    }
    assertEquals(maven.findPsiFile(libFile), expectedValue)
  }

  @Test
  fun testNoChooseFileIntentionForNonSystemDependency() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency><caret>
                           <groupId>xxx</groupId>
                           <artifactId>xxx</artifactId>
                           <version>xxx</version>
                           <scope>compile</system>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    val action = maven.getIntentionAtCaret("Choose File")
    assertNull(action)
  }

  @Test
  fun testTypeCompletion() = runBlocking {
    maven.configureProjectPom("""
                          <groupId>test</groupId>
                          <artifactId>project</artifactId>
                          <version>1</version>
                          <dependencies>
                            <dependency>
                              <type><caret></type>
                            </dependency>
                          </dependencies>
                          """.trimIndent())

    maven.assertCompletionVariants(maven.projectPom, maven.RENDERING_TEXT, "jar", "test-jar", "pom", "ear", "ejb", "ejb-client", "war", "bundle",
                                   "jboss-har", "jboss-sar", "maven-plugin")
  }

  @Test
  fun testDoNotHighlightUnknownType() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId>junit</groupId>
                           <artifactId>junit</artifactId>
                           <version>4.0</version>
                           <type>xxx</type>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    maven.checkHighlighting()
  }

  @Test
  fun testScopeCompletion() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <scope><caret></scope>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    maven.assertCompletionVariants(maven.projectPom, maven.RENDERING_TEXT, "compile", "provided", "runtime", "test", "system")
  }

  @Test
  fun testDoNotHighlightUnknownScopes() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId>junit</groupId>
                           <artifactId>junit</artifactId>
                           <version>4.0</version>
                           <scope>xxx</scope>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    maven.checkHighlighting()
  }

  @Test
  fun testPropertiesInScopes() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <properties>
                         <my.scope>compile</my.scope>
                       </properties>
                       <dependencies>
                         <dependency>
                           <groupId>junit</groupId>
                           <artifactId>junit</artifactId>
                           <version>4.0</version>
                           <scope>${'$'}{my.scope}</scope>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    maven.checkHighlighting()
  }

  @Test
  fun testDoesNotHighlightCorrectValues() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId>junit</groupId>
                           <artifactId>junit</artifactId>
                           <version>4.0</version>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    maven.checkHighlighting()
  }

  @Test
  fun testHighlightingVersionIfVersionIsWrong() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId>junit</groupId>
                           <artifactId>junit</artifactId>
                           <version><error>4.0.wrong</error></version>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    maven.checkHighlighting()
  }

  @Test
  fun testHighlightingArtifactIdAndVersionIfGroupIsUnknown() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId><error>unknown</error></groupId>
                           <artifactId><error>junit</error></artifactId>
                           <version><error>4.0</error></version>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    maven.checkHighlighting()
  }

  @Test
  fun testHighlightingArtifactAndVersionIfGroupIsEmpty() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId><error></error></groupId>
                           <artifactId><error>junit</error></artifactId>
                           <version><error>4.0</error></version>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    maven.checkHighlighting()
  }

  @Test
  fun testHighlightingVersionAndArtifactIfArtifactTheyAreFromAnotherGroup() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId>jmock</groupId>
                           <artifactId><error>junit</error></artifactId>
                           <version><error>4.0</error></version>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    maven.checkHighlighting()
  }

  @Test
  fun testHighlightingVersionIfArtifactIsEmpty() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId>junit</groupId>
                           <artifactId><error></error></artifactId>
                           <version><error>4.0</error></version>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    maven.checkHighlighting()
  }

  @Test
  fun testHighlightingVersionIfArtifactIsUnknown() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId>junit</groupId>
                           <artifactId><error>unknown</error></artifactId>
                           <version><error>4.0</error></version>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    maven.checkHighlighting()
  }

  @Test
  fun testHighlightingVersionItIsFromAnotherGroup() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId>jmock</groupId>
                           <artifactId>jmock</artifactId>
                           <version><error>4.0</error></version>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    maven.checkHighlighting()
  }

  @Test
  fun testHighlightingCoordinatesWithClosedTags() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId/><error></error>
                           <artifactId/><error></error>
                           <version/><error></error>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    maven.checkHighlighting()
  }

  @Test
  fun testHandlingProperties() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <properties>
                         <dep.groupId>junit</dep.groupId>
                         <dep.artifactId>junit</dep.artifactId>
                         <dep.version>4.0</dep.version>
                       </properties>
                       """.trimIndent())
    maven.importProjectAsync()

    // properties are taken from loaded project
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId>${'$'}{dep.groupId}</groupId>
                           <artifactId>${'$'}{dep.artifactId}</artifactId>
                           <version>${'$'}{dep.version}</version>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    maven.checkHighlighting()
  }

  @Test
  fun testHandlingPropertiesWhenProjectIsNotYetLoaded() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <properties>
                         <dep.groupId>junit</dep.groupId>
                         <dep.artifactId>junit</dep.artifactId>
                         <dep.version>4.0</dep.version>
                       </properties>
                       <dependencies>
                         <dependency>
                           <groupId>${'$'}{dep.groupId}</groupId>
                           <artifactId>${'$'}{dep.artifactId}</artifactId>
                           <version>${'$'}{dep.version}</version>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    maven.checkHighlighting()
  }

  @Test
  fun testDontHighlightProblemsInNonManagedPom1() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <properties>
                         <junitVersion>4.0</junitVersion>
                       </properties>
                       """.trimIndent())

    val m = maven.createModulePom("m1",
                                  """
                                      <artifactId>m1</artifactId>
                                      <parent>
                                        <groupId>test</groupId>
                                        <artifactId>project</artifactId>
                                        <version>1</version>
                                      </parent>
                                      <dependencies>
                                       <dependency>
                                       <groupId>junit</groupId>
                                       <artifactId>junit</artifactId>
                                       <version>${'$'}{junitVersion}</version>
                                       </dependency>
                                      </dependencies>
                                      """.trimIndent())

    maven.importProjectAsync()

    maven.checkHighlighting(m)
  }

  @Test
  fun testDontHighlightProblemsInNonManagedPom2() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <properties>
                         <junitVersion>4.0</junitVersion>
                       </properties>
                       """.trimIndent())

    val m = maven.createModulePom("m1",
                                  """
                                      <artifactId>m1</artifactId>
                                      <parent>
                                        <groupId>test</groupId>
                                        <artifactId>project</artifactId>
                                        <version>1</version>
                                      </parent>
                                      <properties>
                                       <aaa>${'$'}{junitVersion}</aaa>
                                      </properties>
                                      """.trimIndent())

    maven.importProjectAsync()
    maven.checkHighlighting(m)
  }

  @Test
  fun testExclusionCompletion() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId>junit</groupId>
                           <artifactId>junit</artifactId>
                           <exclusions>
                             <exclusion>
                               <groupId>jmock</groupId>
                               <artifactId><caret></artifactId>
                             </exclusion>
                           </exclusions>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    maven.assertCompletionVariants(maven.projectPom, maven.RENDERING_TEXT, "jmock")
  }

  @Test
  fun testDoNotHighlightUnknownExclusions() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId>junit</groupId>
                           <artifactId>junit</artifactId>
                           <exclusions>
                             <exclusion>
                               <groupId>foo</groupId>
                               <artifactId>bar</artifactId>
                             </exclusion>
                           </exclusions>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    maven.checkHighlighting()
  }

  @Test
  fun testExclusionHighlightingAbsentGroupId() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId>junit</groupId>
                           <artifactId>junit</artifactId>
                           <exclusions>
                             <<error descr="'groupId' child tag should be defined">exclusion</error>>
                               <artifactId>jmock</artifactId>
                             </exclusion>
                           </exclusions>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    maven.checkHighlighting()
  }

  @Test
  fun testExclusionHighlightingAbsentArtifactId() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId>junit</groupId>
                           <artifactId>junit</artifactId>
                           <version>3.8.1</version>
                           <exclusions>
                             <<error descr="'artifactId' child tag should be defined">exclusion</error>>
                               <groupId>jmock</groupId>
                             </exclusion>
                           </exclusions>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    maven.checkHighlighting()
  }

  @Test
  fun testImportDependencyChainedProperty() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                          <module>m1</module>
                       </modules>
                       <dependencyManagement>
                           <dependencies>
                               <dependency>
                                   <groupId>org.deptest</groupId>
                                   <artifactId>bom-depparent</artifactId>
                                   <version>1.0</version>
                                   <type>pom</type>
                                   <scope>import</scope>
                               </dependency>
                           </dependencies>
                       </dependencyManagement>
                       """.trimIndent())

    maven.createModulePom("m1", """
      <parent>
          <groupId>test</groupId>
          <artifactId>project</artifactId>
          <version>1</version>
        </parent>
      <artifactId>m1</artifactId>
      <dependencies>
        <dependency>
          <groupId>org.example</groupId>
          <artifactId>something</artifactId>
        </dependency>
      </dependencies>
      """.trimIndent())
    maven.importProjectAsync()

    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        val model = MavenDomUtil.getMavenDomModel(maven.project, maven.projectPom, MavenDomProjectModel::class.java)!!

        val dependency = MavenDependencyCompletionUtil.findManagedDependency(model, maven.project, "org.example", "something")
        assertNotNull(dependency)
        assertEquals("42", dependency!!.version.stringValue)
      }
    }
  }

  @Test
  fun testNoCompletionInProjectRootGroupId() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>junit<caret></groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """.trimIndent())

    // completion must NOT fire inside the root project coordinate tags
    maven.assertCompletionVariants(maven.projectPom)
  }

  @Test
  fun testNoCompletionInProjectRootArtifactId() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project<caret></artifactId>
                       <version>1</version>
                       """.trimIndent())

    // completion must NOT fire inside the root project coordinate tags
    maven.assertCompletionVariants(maven.projectPom)
  }
}
