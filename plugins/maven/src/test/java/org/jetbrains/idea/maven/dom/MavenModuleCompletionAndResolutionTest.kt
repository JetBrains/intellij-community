// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom

import com.intellij.maven.testFramework.fixtures.MavenDomTestFixtureIndices
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertModules
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createPomXml
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.createProjectSubFile
import com.intellij.maven.testFramework.fixtures.findPsiFile
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.importProjectsAsync
import com.intellij.maven.testFramework.fixtures.mavenDomFixture
import com.intellij.maven.testFramework.fixtures.projectRoot
import com.intellij.maven.testFramework.fixtures.updateProjectPom
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.fixtures.assertCompletionVariants
import org.jetbrains.idea.maven.fixtures.assertResolved
import org.jetbrains.idea.maven.fixtures.assertUnresolved
import org.jetbrains.idea.maven.fixtures.findTag
import org.jetbrains.idea.maven.fixtures.getIntentionAtCaret
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenModuleCompletionAndResolutionTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenDomFixture(
    mavenVersion = mavenVersion, modelVersion = modelVersion,
    indices = MavenDomTestFixtureIndices("local1", listOf("local2")),
  )

  @BeforeEach
  fun setUp() = runBlocking {
    edtWriteAction { ProjectRootManager.getInstance(maven.project).projectSdk = null }
  }

  @Test
  fun testCompleteFromAllAvailableModules() = runBlocking {
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

    maven.createModulePom("m1",
                    """
                      <groupId>test</groupId>
                      <artifactId>m1</artifactId>
                      <version>1</version>
                      """.trimIndent())

    val module2Pom = maven.createModulePom("m2",
                                     """
                                               <groupId>test</groupId>
                                               <artifactId>m2</artifactId>
                                               <version>1</version>
                                               <packaging>pom</packaging>
                                               <modules>
                                                 <module>m3</module>
                                               </modules>
                                               """.trimIndent())

    maven.createModulePom("m2/m3",
                    """
                      <groupId>test</groupId>
                      <artifactId>m3</artifactId>
                      <version>1</version>
                      """.trimIndent())

    maven.importProjectAsync()
    maven.assertModules("project", "m1", "m2", "m3")

    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                         <module><caret></module>
                       </modules>
                       """.trimIndent())

    maven.assertCompletionVariants(maven.projectPom, "m1", "m2", "m2/m3")

    maven.createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <packaging>pom</packaging>
      <modules>
        <module>m3</module>
        <module><caret></module>
      </modules>
      """.trimIndent())

    maven.assertCompletionVariants(module2Pom, "..", "../m1", "m3")
  }

  @Test
  fun testDoesNotCompeteIfThereIsNoModules() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       """.trimIndent())
    maven.importProjectAsync()

    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module><caret></module>
                       </modules>
                       """.trimIndent())

    maven.assertCompletionVariants(maven.projectPom)
  }

  @Test
  fun testIncludesAllThePomsAvailable() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       """.trimIndent())
    maven.importProjectAsync()

    maven.createModulePom("subDir1",
                    """
                      <groupId>test</groupId>
                      <artifactId>m1</artifactId>
                      <version>1</version>
                      """.trimIndent())

    maven.createModulePom("subDir1/subDir2",
                    """
                      <groupId>test</groupId>
                      <artifactId>m2</artifactId>
                      <version>1</version>
                      """.trimIndent())

    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module><caret></module>
                       </modules>
                       """.trimIndent())

    maven.assertCompletionVariants(maven.projectPom, "subDir1", "subDir1/subDir2")
  }

  @Test
  fun testResolution() = runBlocking {
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

    maven.importProjectAsync()

    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m<caret>1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())

    val psiFile1 = maven.findPsiFile(m1)
    maven.assertResolved(maven.projectPom, psiFile1, "m1")

    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m1</module>
                         <module>m<caret>2</module>
                       </modules>
                       """.trimIndent())

    val psiFile2 =  maven.findPsiFile(m2)
    maven.assertResolved(maven.projectPom, psiFile2, "m2")

    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>unknown<caret>Module</module>
                       </modules>
                       """.trimIndent())

    maven.assertUnresolved(maven.projectPom, "unknownModule")
  }

  @Test
  fun testResolutionWithSlashes() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>./m</module>
                       </modules>
                       """.trimIndent())

    val m = maven.createModulePom("m",
                            """
                                      <groupId>test</groupId>
                                      <artifactId>m</artifactId>
                                      <version>1</version>
                                      """.trimIndent())

    maven.importProjectAsync()

    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>./m<caret></module>
                       </modules>
                       """.trimIndent())

    val psiFile1 = maven.findPsiFile(m)
    maven.assertResolved(maven.projectPom, psiFile1, "./m")

    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>.\m<caret></module>
                       </modules>
                       """.trimIndent())

    val psiFile2 = maven.findPsiFile(m)
    maven.assertResolved(maven.projectPom, psiFile2, ".\\m")
  }

  @Test
  fun testResolutionWithProperties() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <properties>
                         <dirName>subDir</dirName>
                       </properties>
                       <modules>
                         <module>${'$'}{dirName}/m</module>
                       </modules>
                       """.trimIndent())

    val m = maven.createModulePom("subDir/m",
                            """
                                      <groupId>test</groupId>
                                      <artifactId>m</artifactId>
                                      <version>1</version>
                                      """.trimIndent())

    maven.importProjectAsync()

    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <properties>
                         <dirName>subDir</dirName>
                       </properties>
                       <modules>
                         <module><caret>${'$'}{dirName}/m</module>
                       </modules>
                       """.trimIndent())

    val psiFile = maven.findPsiFile(m)
    maven.assertResolved(maven.projectPom, psiFile, "subDir/m")

    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <properties>
                         <dirName>subDir</dirName>
                       </properties>
                       <modules>
                         <module>${'$'}{<caret>dirName}/m</module>
                       </modules>
                       """.trimIndent())

    val tag = maven.findTag(maven.projectPom, "project.properties.dirName")
    maven.assertResolved(maven.projectPom, tag)
  }

  @Test
  fun testCreatePomQuickFix() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       """.trimIndent())
    maven.importProjectAsync()

    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>subDir/new<caret>Module</module>
                       </modules>
                       """.trimIndent())

    val i = maven.getIntentionAtCaret(createModuleIntention)
    assertNotNull(i)

    maven.fixture.launchAction(i!!)

    assertCreateModuleFixResult(
      "subDir/newModule/pom.xml",
      """
        <?xml version="1.0" encoding="UTF-8"?>
        <project xmlns="http://maven.apache.org/POM/4.0.0"
                 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                 xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
            <modelVersion>4.0.0</modelVersion>

            <groupId>test</groupId>
            <artifactId>newModule</artifactId>
            <version>1</version>

            
        </project>
        """.trimIndent())
  }

  @Test
  fun testCreatePomQuickFixCustomPomFileName() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       """.trimIndent())
    maven.importProjectAsync()

    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>subDir/new<caret>Module.xml</module>
                       </modules>
                       """.trimIndent())

    val i = maven.getIntentionAtCaret(createModuleIntention)
    assertNotNull(i)

    maven.fixture.launchAction(i!!)

    assertCreateModuleFixResult(
      "subDir/newModule.xml",
      """
        <?xml version="1.0" encoding="UTF-8"?>
        <project xmlns="http://maven.apache.org/POM/4.0.0"
                 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                 xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
            <modelVersion>4.0.0</modelVersion>

            <groupId>test</groupId>
            <artifactId>subDir</artifactId>
            <version>1</version>

            
        </project>
        """.trimIndent())
  }

  @Test
  fun testCreatePomQuickFixInDotXmlFolder() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       """.trimIndent())
    maven.importProjectAsync()

    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>subDir/new<caret>Module.xml</module>
                       </modules>
                       """.trimIndent())
    maven.createProjectSubFile("subDir/newModule.xml/empty") // ensure that "subDir/newModule.xml" exists as a directory

    val i = maven.getIntentionAtCaret(createModuleIntention)
    assertNotNull(i)

    maven.fixture.launchAction(i!!)

    assertCreateModuleFixResult(
      "subDir/newModule.xml/pom.xml",
      """
        <?xml version="1.0" encoding="UTF-8"?>
        <project xmlns="http://maven.apache.org/POM/4.0.0"
                 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                 xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
            <modelVersion>4.0.0</modelVersion>

            <groupId>test</groupId>
            <artifactId>newModule.xml</artifactId>
            <version>1</version>

            
        </project>
        """.trimIndent())
  }

  @Test
  fun testCreatePomQuickFixTakesGroupAndVersionFromSuperParent() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       """.trimIndent())
    maven.importProjectAsync()

    maven.createProjectPom("""
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <parent>
                         <groupId>parentGroup</groupId>
                         <artifactId>parent</artifactId>
                         <version>parentVersion</version>
                       </parent>
                       <modules>
                         <module>new<caret>Module</module>
                       </modules>
                       """.trimIndent())

    val i = maven.getIntentionAtCaret(createModuleIntention)
    assertNotNull(i)

    maven.fixture.launchAction(i!!)

    assertCreateModuleFixResult(
      "newModule/pom.xml",
      """
        <?xml version="1.0" encoding="UTF-8"?>
        <project xmlns="http://maven.apache.org/POM/4.0.0"
                 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                 xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
            <modelVersion>4.0.0</modelVersion>

            <groupId>parentGroup</groupId>
            <artifactId>newModule</artifactId>
            <version>parentVersion</version>

            
        </project>
        """.trimIndent())
  }

  @Test
  fun testCreatePomQuickFixWithProperties() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       """.trimIndent())
    maven.importProjectAsync()

    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <properties>
                         <dirName>subDir</dirName>
                       </properties>
                       <modules>
                         <module>${'$'}{dirName}/new<caret>Module</module>
                       </modules>
                       """.trimIndent())

    val i = maven.getIntentionAtCaret(createModuleIntention)
    assertNotNull(i)

    maven.fixture.launchAction(i!!)

    val pom = maven.projectRoot.findFileByRelativePath("subDir/newModule/pom.xml")
    assertNotNull(pom)
  }

  @Test
  fun testCreatePomQuickFixTakesDefaultGroupAndVersionIfNothingToOffer() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       """.trimIndent())
    maven.importProjectAsync()

    maven.createProjectPom("""
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <modules>
                         <module>new<caret>Module</module>
                       </modules>
                       """.trimIndent())

    val i = maven.getIntentionAtCaret(createModuleIntention)
    assertNotNull(i)
    maven.fixture.launchAction(i!!)

    assertCreateModuleFixResult(
      "newModule/pom.xml",
      """
        <?xml version="1.0" encoding="UTF-8"?>
        <project xmlns="http://maven.apache.org/POM/4.0.0"
                 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                 xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
            <modelVersion>4.0.0</modelVersion>

            <groupId>groupId</groupId>
            <artifactId>newModule</artifactId>
            <version>version</version>

            
        </project>
        """.trimIndent())
  }

  @Test
  fun testCreateModuleWithParentQuickFix() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       """.trimIndent())
    maven.importProjectAsync()

    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>new<caret>Module</module>
                       </modules>
                       """.trimIndent())

    val i = maven.getIntentionAtCaret(createModuleWithParentIntention)
    assertNotNull(i)
    maven.fixture.launchAction(i!!)

    assertCreateModuleFixResult(
      "newModule/pom.xml",
      """
        <?xml version="1.0" encoding="UTF-8"?>
        <project xmlns="http://maven.apache.org/POM/4.0.0"
                 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                 xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
            <modelVersion>4.0.0</modelVersion>

            <parent>
                <groupId>test</groupId>
                <artifactId>project</artifactId>
                <version>1</version>
            </parent>

            <groupId>test</groupId>
            <artifactId>newModule</artifactId>
            <version>1</version>

            
        </project>
        """.trimIndent())
  }

  @Test
  fun testCreateModuleWithParentQuickFix2() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       """.trimIndent())
    maven.importProjectAsync()

    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>ppp/new<caret>Module</module>
                       </modules>
                       """.trimIndent())

    val i = maven.getIntentionAtCaret(createModuleWithParentIntention)
    assertNotNull(i)
    maven.fixture.launchAction(i!!)

    assertCreateModuleFixResult(
      "ppp/newModule/pom.xml",
      """
        <?xml version="1.0" encoding="UTF-8"?>
        <project xmlns="http://maven.apache.org/POM/4.0.0"
                 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                 xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
            <modelVersion>4.0.0</modelVersion>

            <parent>
                <groupId>test</groupId>
                <artifactId>project</artifactId>
                <version>1</version>
                <relativePath>../..</relativePath>
            </parent>

            <groupId>test</groupId>
            <artifactId>newModule</artifactId>
            <version>1</version>

            
        </project>
        """.trimIndent())
  }

  @Test
  fun testCreateModuleWithParentQuickFix3() = runBlocking {
    val parentPom = maven.createModulePom("parent",
                                    """
                                              <groupId>test</groupId>
                                              <artifactId>project</artifactId>
                                              <version>1</version>
                                              <packaging>pom</packaging>
                                              """.trimIndent())

    maven.importProjectsAsync(parentPom)

    maven.fixture.saveText(parentPom, maven.createPomXml(
      """
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
        <packaging>pom</packaging>
        <modules>
          <module>../ppp/new<caret>Module</module>
        </modules>
        """.trimIndent()))

    //PsiDocumentManager.getInstance(project).commitAllDocuments()
    val i = maven.getIntentionAtCaret(parentPom, createModuleWithParentIntention)
    assertNotNull(i)
    maven.fixture.launchAction(i!!)

    assertCreateModuleFixResult(
      "ppp/newModule/pom.xml",
      """
        <?xml version="1.0" encoding="UTF-8"?>
        <project xmlns="http://maven.apache.org/POM/4.0.0"
                 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                 xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
            <modelVersion>4.0.0</modelVersion>

            <parent>
                <groupId>test</groupId>
                <artifactId>project</artifactId>
                <version>1</version>
                <relativePath>../../parent</relativePath>
            </parent>

            <groupId>test</groupId>
            <artifactId>newModule</artifactId>
            <version>1</version>

            
        </project>
        """.trimIndent())
  }

  @Test
  fun testDoesNotShowCreatePomQuickFixForEmptyModuleTag() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       """.trimIndent())
    maven.importProjectAsync()

    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module><caret></module>
                       </modules>
                       """.trimIndent())

    assertNull(maven.getIntentionAtCaret(createModuleIntention))
  }

  @Test
  fun testDoesNotShowCreatePomQuickFixExistingModule() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>module</module>
                       </modules>
                       """.trimIndent())

    maven.createModulePom("module",
                    """
                      <groupId>test</groupId>
                      <artifactId>module</artifactId>
                      <version>1</version>
                      """.trimIndent())
    maven.importProjectAsync()

    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m<caret>odule</module>
                       </modules>
                       """.trimIndent())

    assertNull(maven.getIntentionAtCaret(createModuleIntention))
  }

  private suspend fun assertCreateModuleFixResult(relativePath: String, expectedText: String) {
    val pom = maven.projectRoot.findFileByRelativePath(relativePath)
    assertNotNull(pom)

    readAction {
      val doc = FileDocumentManager.getInstance().getDocument(pom!!)
      val selectedEditor = FileEditorManager.getInstance(maven.project).getSelectedTextEditor()
      assertEquals(doc, selectedEditor!!.getDocument())
      assertEquals(expectedText, doc!!.text)
    }
  }

  companion object {
    private val createModuleIntention: String
      get() = MavenDomBundle.message("fix.create.module")

    private val createModuleWithParentIntention: String
      get() = MavenDomBundle.message("fix.create.module.with.parent")
  }
}
