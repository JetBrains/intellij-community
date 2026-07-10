// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom

import com.intellij.maven.testFramework.fixtures.MavenDomTestFixture
import com.intellij.maven.testFramework.fixtures.MavenDomTestFixture.Highlight
import com.intellij.maven.testFramework.fixtures.MavenDomTestFixtureIndices
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assumeMaven3
import com.intellij.maven.testFramework.fixtures.assumeMaven4
import com.intellij.maven.testFramework.fixtures.configTest
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createProfilesXml
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.createProjectSubDir
import com.intellij.maven.testFramework.fixtures.createProjectSubFile
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenDomFixture
import com.intellij.maven.testFramework.fixtures.updateSettingsXml
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.psi.PsiFile
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.dom.inspections.MavenModelVersionMissedInspection
import org.jetbrains.idea.maven.fixtures.assertCompletionVariants
import org.jetbrains.idea.maven.fixtures.assertCompletionVariantsInclude
import org.jetbrains.idea.maven.fixtures.checkHighlighting
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenModelValidationTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenDomFixture(
    mavenVersion = mavenVersion, modelVersion = modelVersion,
    initialPom = MavenDomTestFixture.DEFAULT_POM,
    skipPluginResolution = false,
    indices = MavenDomTestFixtureIndices("local1", listOf("local2")),
  )

  @Test
  fun testCompletionRelativePath() = runBlocking {
    maven.createProjectSubDir("src")
    maven.createProjectSubFile("a.txt", "")

    val modulePom = maven.createModulePom("module1",
                                    """
                                              <groupId>test</groupId>
                                              <artifactId>module1</artifactId>
                                              <version>1</version>
                                              <parent>
                                              <relativePath>../<caret></relativePath>
                                              </parent>
                                              """.trimIndent())

    maven.assertCompletionVariants(modulePom, "src", "module1", "pom.xml")
  }

  @Test
  fun testRelativePathDefaultValue() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """.trimIndent())

    val modulePom = maven.createModulePom("module1",
                                    """
                                              <groupId>test</groupId>
                                              <artifactId>module1</artifactId>
                                              <version>1</version>
                                              <parent>
                                              <relativePath>../pom.<caret>xml</relativePath>
                                              </parent>
                                              """.trimIndent())

    maven.configTest(modulePom)
    withContext(Dispatchers.EDT) {
      //maybe readaction
      val elementAtCaret = writeIntentReadAction { maven.fixture.getElementAtCaret() }

      UsefulTestCase.assertInstanceOf(elementAtCaret, PsiFile::class.java)
      assertEquals((elementAtCaret as PsiFile).getVirtualFile(), maven.projectPom)
    }
  }

  @Test
  fun testUnderstandingProjectSchemaWithoutNamespace() = runBlocking {
    maven.fixture.saveText(maven.projectPom,
                     """
                         <project>
                           <dep<caret>
                         </project>
                         """.trimIndent())

    maven.assertCompletionVariants(maven.projectPom, "dependencies", "dependencyManagement")
  }

  @Test
  fun testUnderstandingProfilesSchemaWithoutNamespace() = runBlocking {
    val profiles = maven.createProfilesXml("""
                                               <profile>
                                                 <<caret>
                                               </profile>
                                               """.trimIndent())

    maven.assertCompletionVariantsInclude(profiles, "id", "activation")
  }

  @Test
  fun testUnderstandingSettingsSchemaWithoutNamespace() = runBlocking {
    val settings = maven.updateSettingsXml("""
                                               <profiles>
                                                 <profile>
                                                   <<caret>
                                                 </profile>
                                               </profiles>
                                               """.trimIndent())

    maven.assertCompletionVariantsInclude(settings, "id", "activation")
  }

  @Test
  fun testAbsentModelVersion() = runBlocking {
    maven.assumeMaven3()
    maven.fixture.saveText(maven.projectPom,
                     """
                         <<error descr="'modelVersion' child tag should be defined">project</error> xmlns="http://maven.apache.org/POM/4.0.0"         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                           <artifactId>foo</artifactId>
                         </project>
                         """.trimIndent())
    maven.fixture.enableInspections(listOf(MavenModelVersionMissedInspection::class.java))
    maven.checkHighlighting()
  }

  @Test
  fun testAbsentModelVersionAndXmlnsInMaven4() = runBlocking {
    maven.assumeMaven4()
    maven.fixture.saveText(maven.projectPom,
                     """
                         <<error descr="'modelVersion' child tag should be defined">project</error>>
                           <artifactId>foo</artifactId>
                         </project>
                         """.trimIndent())
    maven.fixture.enableInspections(listOf(MavenModelVersionMissedInspection::class.java))
    maven.checkHighlighting()
  }

  @Test
  fun testAbsentModelVersion410InMaven4() = runBlocking {
    maven.assumeMaven4()
    maven.fixture.saveText(maven.projectPom,
                     """
                         <project xmlns="http://maven.apache.org/POM/4.1.0"         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"         xsi:schemaLocation="http://maven.apache.org/POM/4.1.0 http://maven.apache.org/xsd/maven-4.1.0.xsd">
                           <artifactId>foo</artifactId>
                         </project>
                         """.trimIndent())
    maven.fixture.enableInspections(listOf(MavenModelVersionMissedInspection::class.java))
    maven.checkHighlighting()
  }

  @Test
  fun testAbsentModelVersion400InMaven4() = runBlocking {
    maven.assumeMaven4()
    maven.fixture.saveText(maven.projectPom,
                     """
                         <project xmlns="http://maven.apache.org/POM/4.0.0"         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                           <artifactId>foo</artifactId>
                         </project>
                         """.trimIndent())
    maven.fixture.enableInspections(listOf(MavenModelVersionMissedInspection::class.java))
    maven.checkHighlighting()
  }


  @Test
  fun testAbsentArtifactId() = runBlocking {
    maven.fixture.saveText(maven.projectPom,
                     """
                         <<error descr="'artifactId' child tag should be defined">project</error> xmlns="http://maven.apache.org/POM/4.0.0"         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                           <modelVersion>4.0.0</modelVersion>
                         </project>
                         """.trimIndent())
    maven.checkHighlighting()
  }


  @Test
  fun testModelVersion41isSupportedInMaven4() = runBlocking {
    maven.assumeMaven4()
    maven.fixture.saveText(maven.projectPom,
                     """
                         <project xmlns="http://maven.apache.org/POM/4.0.0"         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                           <modelVersion>4.1.0</modelVersion>
                           <artifactId>foo</artifactId>
                         </project>
                         """.trimIndent())
    maven.checkHighlighting()
  }

  @Test
  fun testEmptyValues() = runBlocking {
    maven.createProjectPom("""
                       <<error>groupId</error>></groupId>
                       <<error>artifactId</error>></artifactId>
                       <<error>version</error>></version>
                       """.trimIndent())
    maven.checkHighlighting()
  }

  @Test
  fun testAddingSettingsXmlReadingProblemsToProjectTag() = runBlocking {
    maven.fixture.saveText(maven.projectPom,
                     """
                         <project>
                           <modelVersion>4.0.0</modelVersion>
                           <groupId>test</groupId>
                           <artifactId>project</artifactId>
                           <version>1</version>
                         </project>
                         """.trimIndent())
    maven.updateSettingsXml("<<<")

    maven.importProjectAsync()

    maven.fixture.saveText(maven.projectPom,
                     """
                         <<error descr="'settings.xml' has syntax errors">project</error>>
                           <modelVersion>4.0.0</modelVersion>
                           <groupId>test</groupId>
                           <artifactId>project</artifactId>
                           <version>1</version>
                         </project>
                         """.trimIndent())
    maven.checkHighlighting()
  }

  @Test
  fun testAddingStructureReadingProblemsToParentTag() = runBlocking {
    maven.fixture.saveText(maven.projectPom,
                     """
                         <project>
                           <modelVersion>4.0.0</modelVersion>
                           <groupId>test</groupId>
                           <artifactId>project</artifactId>
                           <version>1</version>
                           <parent>
                             <groupId>test</groupId>
                             <artifactId>project</artifactId>
                             <version>1</version>
                           </parent>
                         </project>
                         """.trimIndent())

    maven.importProjectAsync()

    maven.fixture.saveText(maven.projectPom,
                     """
                         <project>
                           <modelVersion>4.0.0</modelVersion>
                           <groupId>test</groupId>
                           <artifactId>project</artifactId>
                           <version>1</version>
                           <<error descr="Self-inheritance found">parent</error>>
                             <groupId>test</groupId>
                             <artifactId>project</artifactId>
                             <version>1</version>
                           </parent>
                         </project>
                         """.trimIndent())

    maven.checkHighlighting()
  }

  @Test
  fun testAddingParentReadingProblemsToParentTag() = runBlocking {
    maven.createModulePom("parent",
                    """
                      <groupId>test</groupId>
                      <artifactId>parent</artifactId>
                      <version>1</version>
                      <<<
                      """.trimIndent())

    maven.fixture.saveText(maven.projectPom,
                     """
                         <project>
                           <modelVersion>4.0.0</modelVersion>
                           <groupId>test</groupId>
                           <artifactId>project</artifactId>
                           <version>1</version>
                           <parent>
                             <groupId>test</groupId>
                             <artifactId>parent</artifactId>
                             <version>1</version>
                             <relativePath>parent/pom.xml</relativePath>
                           </parent>
                         </project>
                         """.trimIndent())
    maven.importProjectAsync()

    maven.checkHighlighting(maven.projectPom, Highlight(text = "parent", description="Parent 'test:parent:1' has problems"))
  }

  @Test
  fun testDoNotAddReadingSyntaxProblemsToProjectTag() = runBlocking {
    maven.fixture.saveText(maven.projectPom,
                     """
                         <project>
                           <modelVersion>4.0.0</modelVersion>
                           <groupId>test</groupId>
                           <artifactId>project</artifactId>
                           <version>1</version>
                           <</project>
                         """.trimIndent())

    maven.importProjectAsync()

    maven.checkHighlighting(maven.projectPom, Highlight(text = "<"))
  }

  @Test
  fun testDoNotAddDependencyAndModuleProblemsToProjectTag() = runBlocking {
    maven.fixture.saveText(maven.projectPom,
                     """
                         <project>
                           <modelVersion>4.0.0</modelVersion>
                           <groupId>test</groupId>
                           <artifactId>project</artifactId>
                           <version>1</version>
                           <packaging>pom</packaging>
                           <modules>
                             <module>foo</module>
                           </modules>
                           <dependencies>
                             <dependency>
                               <groupId>xxx</groupId>
                               <artifactId>yyy</artifactId>
                               <version>zzz</version>
                             </dependency>
                           </dependencies>
                         </project>
                         """.trimIndent())

    maven.importProjectAsync()

    maven.checkHighlighting(maven.projectPom,
                      Highlight(text = "foo"),
                      Highlight(text = "xxx"),
                      Highlight(text = "yyy"),
                      Highlight(text = "zzz"),
    )
  }
}
