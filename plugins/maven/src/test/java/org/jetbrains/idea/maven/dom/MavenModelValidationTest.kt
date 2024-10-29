// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.psi.PsiFile
import com.intellij.testFramework.UsefulTestCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Test

class MavenModelValidationTest : MavenDomWithIndicesTestCase() {
  override fun setUp() = runBlocking {
    super.setUp()
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
  }

  @Test
  fun testCompletionRelativePath() = runBlocking {
    createProjectSubDir("src")
    createProjectSubFile("a.txt", "")

    val modulePom = createModulePom("module1",
                                    """
                                              <groupId>test</groupId>
                                              <artifactId>module1</artifactId>
                                              <version>1</version>
                                              <parent>
                                              <relativePath>../<caret></relativePath>
                                              </parent>
                                              """.trimIndent())

    assertCompletionVariants(modulePom, "src", "module1", "pom.xml")
  }

  @Test
  fun testRelativePathDefaultValue() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """.trimIndent())

    val modulePom = createModulePom("module1",
                                    """
                                              <groupId>test</groupId>
                                              <artifactId>module1</artifactId>
                                              <version>1</version>
                                              <parent>
                                              <relativePath>../pom.<caret>xml</relativePath>
                                              </parent>
                                              """.trimIndent())

    configTest(modulePom)
    withContext(Dispatchers.EDT) {
      //maybe readaction
      val elementAtCaret = writeIntentReadAction { fixture.getElementAtCaret () }

      UsefulTestCase.assertInstanceOf(elementAtCaret, PsiFile::class.java)
      assertEquals((elementAtCaret as PsiFile).getVirtualFile(), projectPom)
    }
  }

  @Test
  fun testUnderstandingProjectSchemaWithoutNamespace() = runBlocking {
    fixture.saveText(projectPom,
                     """
                         <project>
                           <dep<caret>
                         </project>
                         """.trimIndent())

    assertCompletionVariants(projectPom, "dependencies", "dependencyManagement")
  }

  @Test
  fun testUnderstandingProfilesSchemaWithoutNamespace() = runBlocking {
    val profiles = createProfilesXml("""
                                               <profile>
                                                 <<caret>
                                               </profile>
                                               """.trimIndent())

    assertCompletionVariantsInclude(profiles, "id", "activation")
  }

  @Test
  fun testUnderstandingSettingsSchemaWithoutNamespace() = runBlocking {
    val settings = updateSettingsXml("""
                                               <profiles>
                                                 <profile>
                                                   <<caret>
                                                 </profile>
                                               </profiles>
                                               """.trimIndent())

    assertCompletionVariantsInclude(settings, "id", "activation")
  }

  @Test
  fun testAbsentModelVersion() = runBlocking {
    fixture.saveText(projectPom,
                     """
                         <<error descr="'modelVersion' child tag should be defined">project</error> xmlns="http://maven.apache.org/POM/4.0.0"         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                           <artifactId>foo</artifactId>
                         </project>
                         """.trimIndent())
    checkHighlighting()
  }

  @Test
  fun testAbsentArtifactId() = runBlocking {
    fixture.saveText(projectPom,
                     """
                         <<error descr="'artifactId' child tag should be defined">project</error> xmlns="http://maven.apache.org/POM/4.0.0"         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                           <modelVersion>4.0.0</modelVersion>
                         </project>
                         """.trimIndent())
    checkHighlighting()
  }

  @Test
  fun testUnknownModelVersion() = runBlocking {
    fixture.saveText(projectPom,
                     """
                         <project xmlns="http://maven.apache.org/POM/4.0.0"         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                           <modelVersion><error descr="Unsupported model version. Only version 4.0.0 is supported.">666</error></modelVersion>
                           <artifactId>foo</artifactId>
                         </project>
                         """.trimIndent())
    checkHighlighting()
  }

  @Test
  fun testEmptyValues() = runBlocking {
    createProjectPom("""
                       <<error>groupId</error>></groupId>
                       <<error>artifactId</error>></artifactId>
                       <<error>version</error>></version>
                       """.trimIndent())
    checkHighlighting()
  }

  @Test
  fun testAddingSettingsXmlReadingProblemsToProjectTag() = runBlocking {
    fixture.saveText(projectPom,
                     """
                         <project>
                           <modelVersion>4.0.0</modelVersion>
                           <groupId>test</groupId>
                           <artifactId>project</artifactId>
                           <version>1</version>
                         </project>
                         """.trimIndent())
    updateSettingsXml("<<<")

    importProjectAsync()

    fixture.saveText(projectPom,
                     """
                         <<error descr="'settings.xml' has syntax errors">project</error>>
                           <modelVersion>4.0.0</modelVersion>
                           <groupId>test</groupId>
                           <artifactId>project</artifactId>
                           <version>1</version>
                         </project>
                         """.trimIndent())
    checkHighlighting()
  }

  @Test
  fun testAddingProfilesXmlReadingProblemsToProjectTag() = runBlocking {
    fixture.saveText(projectPom,
                     """
                         <project>
                           <modelVersion>4.0.0</modelVersion>
                           <groupId>test</groupId>
                           <artifactId>project</artifactId>
                           <version>1</version>
                         </project>
                         """.trimIndent())
    createProfilesXml("<<<")

    importProjectAsync()

    fixture.saveText(projectPom,
                     """
                         <<error descr="'profiles.xml' has syntax errors">project</error>>
                           <modelVersion>4.0.0</modelVersion>
                           <groupId>test</groupId>
                           <artifactId>project</artifactId>
                           <version>1</version>
                         </project>
                         """.trimIndent())
    checkHighlighting()
  }

  @Test
  fun testAddingStructureReadingProblemsToParentTag() = runBlocking {
    fixture.saveText(projectPom,
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

    importProjectAsync()

    fixture.saveText(projectPom,
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

    checkHighlighting()
  }

  @Test
  fun testAddingParentReadingProblemsToParentTag() = runBlocking {
    createModulePom("parent",
                    """
                      <groupId>test</groupId>
                      <artifactId>parent</artifactId>
                      <version>1</version>
                      <<<
                      """.trimIndent())

    fixture.saveText(projectPom,
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
    importProjectAsync()

    fixture.saveText(projectPom,
                     """
                         <project>
                           <modelVersion>4.0.0</modelVersion>
                           <groupId>test</groupId>
                           <artifactId>project</artifactId>
                           <version>1</version>
                           <<error descr="Parent 'test:parent:1' has problems">parent</error>>
                             <groupId>test</groupId>
                             <artifactId>parent</artifactId>
                             <version>1</version>
                             <relativePath>parent/pom.xml</relativePath>
                           </parent>
                         </project>
                         """.trimIndent())
    checkHighlighting()
  }

  @Test
  fun testDoNotAddReadingSyntaxProblemsToProjectTag() = runBlocking {
    fixture.saveText(projectPom,
                     """
                         <project>
                           <modelVersion>4.0.0</modelVersion>
                           <groupId>test</groupId>
                           <artifactId>project</artifactId>
                           <version>1</version>
                           <</project>
                         """.trimIndent())

    importProjectAsync()

    checkHighlighting(projectPom, Highlight(text = "<"))
  }

  @Test
  fun testDoNotAddDependencyAndModuleProblemsToProjectTag() = runBlocking {
    fixture.saveText(projectPom,
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

    importProjectAsync()

    checkHighlighting(projectPom,
                      Highlight(text = "foo"),
                      Highlight(text = "xxx"),
                      Highlight(text = "yyy"),
                      Highlight(text = "zzz"),
    )
  }
}
