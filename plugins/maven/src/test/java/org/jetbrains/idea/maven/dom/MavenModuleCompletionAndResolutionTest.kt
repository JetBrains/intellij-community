/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.PsiDocumentManager
import kotlinx.coroutines.runBlocking
import org.junit.Test

class MavenModuleCompletionAndResolutionTest : MavenDomWithIndicesTestCase() {
  @Test
  fun testCompleteFromAllAvailableModules() = runBlocking {
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

    createModulePom("m1",
                    """
                      <groupId>test</groupId>
                      <artifactId>m1</artifactId>
                      <version>1</version>
                      """.trimIndent())

    val module2Pom = createModulePom("m2",
                                     """
                                               <groupId>test</groupId>
                                               <artifactId>m2</artifactId>
                                               <version>1</version>
                                               <packaging>pom</packaging>
                                               <modules>
                                                 <module>m3</module>
                                               </modules>
                                               """.trimIndent())

    createModulePom("m2/m3",
                    """
                      <groupId>test</groupId>
                      <artifactId>m3</artifactId>
                      <version>1</version>
                      """.trimIndent())

    importProjectAsync()
    assertModules("project", "m1", "m2", "m3")

    createProjectPom("""
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

    assertCompletionVariants(myProjectPom, "m1", "m2", "m2/m3")

    createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <packaging>pom</packaging>
      <modules>
        <module>m3</module>
        <module><caret></module>
      </modules>
      """.trimIndent())

    assertCompletionVariants(module2Pom, "..", "../m1", "m3")
  }

  @Test
  fun testDoesNotCompeteIfThereIsNoModules() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       """.trimIndent())
    importProjectAsync()

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module><caret></module>
                       </modules>
                       """.trimIndent())

    assertCompletionVariants(myProjectPom)
  }

  @Test
  fun testIncludesAllThePomsAvailable() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       """.trimIndent())
    importProjectAsync()

    createModulePom("subDir1",
                    """
                      <groupId>test</groupId>
                      <artifactId>m1</artifactId>
                      <version>1</version>
                      """.trimIndent())

    createModulePom("subDir1/subDir2",
                    """
                      <groupId>test</groupId>
                      <artifactId>m2</artifactId>
                      <version>1</version>
                      """.trimIndent())

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module><caret></module>
                       </modules>
                       """.trimIndent())

    assertCompletionVariants(myProjectPom, "subDir1", "subDir1/subDir2")
  }

  @Test
  fun testResolution() = runBlocking {
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

    importProjectAsync()

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m<caret>1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())

    assertResolved(myProjectPom, findPsiFile(m1), "m1")

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m1</module>
                         <module>m<caret>2</module>
                       </modules>
                       """.trimIndent())

    assertResolved(myProjectPom, findPsiFile(m2), "m2")

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>unknown<caret>Module</module>
                       </modules>
                       """.trimIndent())

    assertUnresolved(myProjectPom, "unknownModule")
  }

  @Test
  fun testResolutionWithSlashes() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>./m</module>
                       </modules>
                       """.trimIndent())

    val m = createModulePom("m",
                            """
                                      <groupId>test</groupId>
                                      <artifactId>m</artifactId>
                                      <version>1</version>
                                      """.trimIndent())

    importProjectAsync()

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>./m<caret></module>
                       </modules>
                       """.trimIndent())

    assertResolved(myProjectPom, findPsiFile(m), "./m")

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>.\m<caret></module>
                       </modules>
                       """.trimIndent())

    assertResolved(myProjectPom, findPsiFile(m), ".\\m")
  }

  @Test
  fun testResolutionWithProperties() = runBlocking {
    createProjectPom("""
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

    val m = createModulePom("subDir/m",
                            """
                                      <groupId>test</groupId>
                                      <artifactId>m</artifactId>
                                      <version>1</version>
                                      """.trimIndent())

    importProjectAsync()

    createProjectPom("""
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

    assertResolved(myProjectPom, findPsiFile(m), "subDir/m")

    createProjectPom("""
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

    assertResolved(myProjectPom, findTag(myProjectPom, "project.properties.dirName"))
  }

  @Test
  fun testCreatePomQuickFix() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       """.trimIndent())
    importProjectAsync()

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>subDir/new<caret>Module</module>
                       </modules>
                       """.trimIndent())

    val i = getIntentionAtCaret(createModuleIntention)
    assertNotNull(i)

    myFixture.launchAction(i)

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
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       """.trimIndent())
    importProjectAsync()

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>subDir/new<caret>Module.xml</module>
                       </modules>
                       """.trimIndent())

    val i = getIntentionAtCaret(createModuleIntention)
    assertNotNull(i)

    myFixture.launchAction(i)

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
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       """.trimIndent())
    importProjectAsync()

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>subDir/new<caret>Module.xml</module>
                       </modules>
                       """.trimIndent())
    createProjectSubFile("subDir/newModule.xml/empty") // ensure that "subDir/newModule.xml" exists as a directory

    val i = getIntentionAtCaret(createModuleIntention)
    assertNotNull(i)

    myFixture.launchAction(i)

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
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       """.trimIndent())
    importProjectAsync()

    createProjectPom("""
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

    val i = getIntentionAtCaret(createModuleIntention)
    assertNotNull(i)

    myFixture.launchAction(i)

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
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       """.trimIndent())
    importProjectAsync()

    createProjectPom("""
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

    val i = getIntentionAtCaret(createModuleIntention)
    assertNotNull(i)

    myFixture.launchAction(i)

    val pom = myProjectRoot.findFileByRelativePath("subDir/newModule/pom.xml")
    assertNotNull(pom)
  }

  @Test
  fun testCreatePomQuickFixTakesDefaultGroupAndVersionIfNothingToOffer() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       """.trimIndent())
    importProjectAsync()

    createProjectPom("""
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <modules>
                         <module>new<caret>Module</module>
                       </modules>
                       """.trimIndent())

    val i = getIntentionAtCaret(createModuleIntention)
    assertNotNull(i)
    myFixture.launchAction(i)

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
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       """.trimIndent())
    importProjectAsync()

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>new<caret>Module</module>
                       </modules>
                       """.trimIndent())

    val i = getIntentionAtCaret(createModuleWithParentIntention)
    assertNotNull(i)
    myFixture.launchAction(i)

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
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       """.trimIndent())
    importProjectAsync()

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>ppp/new<caret>Module</module>
                       </modules>
                       """.trimIndent())

    val i = getIntentionAtCaret(createModuleWithParentIntention)
    assertNotNull(i)
    myFixture.launchAction(i)

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
    val parentPom = createModulePom("parent",
                                    """
                                              <groupId>test</groupId>
                                              <artifactId>project</artifactId>
                                              <version>1</version>
                                              <packaging>pom</packaging>
                                              """.trimIndent())

    importProjectAsync(parentPom)

    myFixture.saveText(parentPom, createPomXml(
      """
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
        <packaging>pom</packaging>
        <modules>
          <module>../ppp/new<caret>Module</module>
        </modules>
        """.trimIndent()))
    PsiDocumentManager.getInstance(myProject).commitAllDocuments()
    val i = getIntentionAtCaret(parentPom, createModuleWithParentIntention)
    assertNotNull(i)
    myFixture.launchAction(i)

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
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       """.trimIndent())
    importProjectAsync()

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module><caret></module>
                       </modules>
                       """.trimIndent())

    assertNull(getIntentionAtCaret(createModuleIntention))
  }

  @Test
  fun testDoesNotShowCreatePomQuickFixExistingModule() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>module</module>
                       </modules>
                       """.trimIndent())

    createModulePom("module",
                    """
                      <groupId>test</groupId>
                      <artifactId>module</artifactId>
                      <version>1</version>
                      """.trimIndent())
    importProjectAsync()

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m<caret>odule</module>
                       </modules>
                       """.trimIndent())

    assertNull(getIntentionAtCaret(createModuleIntention))
  }

  private fun assertCreateModuleFixResult(relativePath: String, expectedText: String) {
    val pom = myProjectRoot.findFileByRelativePath(relativePath)
    assertNotNull(pom)

    val doc = FileDocumentManager.getInstance().getDocument(pom!!)

    val selectedEditor = FileEditorManager.getInstance(myProject).getSelectedTextEditor()
    assertEquals(doc, selectedEditor!!.getDocument())

    assertEquals(expectedText, doc!!.text)
  }

  companion object {
    private val createModuleIntention: String
      get() = MavenDomBundle.message("fix.create.module")

    private val createModuleWithParentIntention: String
      get() = MavenDomBundle.message("fix.create.module.with.parent")
  }
}
