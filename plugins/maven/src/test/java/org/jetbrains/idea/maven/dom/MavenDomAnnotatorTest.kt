// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom

import com.intellij.maven.testFramework.MavenDomTestCase
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.openapi.project.modules
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.common.runAll
import junit.framework.TestCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.dom.annotator.MavenDomGutterAnnotatorLogger
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenLog
import org.junit.Test

class MavenDomAnnotatorTest : MavenDomTestCase() {
  override fun setUp() {
    super.setUp()
    MavenDomGutterAnnotatorLogger.setLogLevel(LogLevel.WARNING)
  }

  override fun tearDown() {
    runAll(
      { super.tearDown() },
      { MavenDomGutterAnnotatorLogger.resetLogLevel() },
    )
  }

  @Test
  fun testAnnotatePlugin() = runBlocking {
    val modulePomContent = """
<parent>
  <groupId>test</groupId>
  <artifactId>project</artifactId>
  <version>1</version>
</parent>

<artifactId>m</artifactId>
<build>
  <plugins>
    <plugin>
      <artifactId>maven-compiler-plugin</artifactId>
    </plugin>
  </plugins>
</build>
"""
    val modulePom = createModulePom("m", modulePomContent)

    createProjectPom("""
<groupId>test</groupId>
<artifactId>project</artifactId>
<version>1</version>
<packaging>pom</packaging>

<modules>
  <module>m</module>
</modules>

<build>
  <pluginManagement>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
      </plugin>
    </plugins>
  </pluginManagement>
</build>
""")

    importProjectAsync()

    withContext(Dispatchers.EDT) {
      val modules = project.modules
      assertSize(2, modules)
      val projectsManager = MavenProjectsManager.getInstance(project)
      val tree = projectsManager.projectsTree
      assertSize(2, tree.projects)
      assertSize(2, tree.nonIgnoredProjects)
    }

    checkGutters(modulePom, modulePomContent, listOf(
      "<artifactId>maven-compiler-plugin</artifactId>",
      """<parent>
          <groupId>test</groupId>
          <artifactId>project</artifactId>
          <version>1</version>
         </parent>"""))
  }

  @Test
  fun testAnnotateDependency() = runBlocking {
    val modulePomContent = """
<parent>
  <groupId>test</groupId>
  <artifactId>project</artifactId>
  <version>1</version>
</parent>
    
<artifactId>m</artifactId>
<dependencies>
  <dependency>
    <groupId>junit</groupId>
    <artifactId>junit</artifactId>                
  </dependency>
</dependencies>
"""
    val modulePom = createModulePom("m", modulePomContent)

    importProjectAsync("""
<groupId>test</groupId>
<artifactId>project</artifactId>
<version>1</version>
<packaging>pom</packaging>

<modules>
  <module>m</module>
</modules>

<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>junit</groupId>
      <artifactId>junit</artifactId>                
      <version>4.0</version>
    </dependency>
  </dependencies>
</dependencyManagement>
""")

    checkGutters(modulePom, modulePomContent, listOf(
      """<dependency>
         <groupId>junit</groupId>
         <artifactId>junit</artifactId>       
         </dependency>""",
      """<parent>
         <groupId>test</groupId>
         <artifactId>project</artifactId>
         <version>1</version>
         </parent>"""))
  }

  @Test
  fun testAnnotateDependencyWithEmptyRelativePath() = runBlocking {
    val modulePomContent = """
<parent>
  <groupId>test</groupId>
  <artifactId>project</artifactId>
  <version>1</version>
  <relativePath/>
</parent>
    
<artifactId>m</artifactId>
<dependencies>
  <dependency>
    <groupId>junit</groupId>
    <artifactId>junit</artifactId>                
  </dependency>
</dependencies>
"""
    val modulePom = createModulePom("m", modulePomContent)

    importProjectAsync("""
<groupId>test</groupId>
<artifactId>project</artifactId>
<version>1</version>
<packaging>pom</packaging>

<modules>
  <module>m</module>
</modules>

<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>junit</groupId>
      <artifactId>junit</artifactId>                
      <version>4.0</version>
    </dependency>
  </dependencies>
</dependencyManagement>
""")

    checkGutters(modulePom, modulePomContent, listOf(
      """<dependency>
         <groupId>junit</groupId>
         <artifactId>junit</artifactId>       
         </dependency>""",
      """<parent>
         <groupId>test</groupId>
         <artifactId>project</artifactId>
         <version>1</version>
         <relativePath/>
         </parent>"""))
  }

  private suspend fun checkGutters(virtualFile: VirtualFile, expectedFileContent: String, expectedProperties: Collection<String>) {
    withContext(Dispatchers.EDT) {
      //maybe narrower
      //maybe readaction
      writeIntentReadAction {
        val file = PsiManager.getInstance(project).findFile(virtualFile)!!
        val text = file.text
        TestCase.assertTrue("Unexpected pom content:\n$text", text.contains(expectedFileContent))

        //fixture.configureFromExistingVirtualFile(virtualFile)
        fixture.configureByText("pom.xml", text)
        val highlighting = fixture.doHighlighting()
        MavenLog.LOG.warn("Highlighting:\n\n" + highlighting.joinToString("\n\n") { it.toString() })
        val actualProperties = highlighting
          .filter { it.gutterIconRenderer != null }
          .map { text.substring(it.getStartOffset(), it.getEndOffset()) }
          .map { it.replace(" ", "") }
          .toSet()

        val expectedPropertiesClearing = expectedProperties
          .map { it.replace(" ", "") }
          .toSet()
        assertEquals(expectedPropertiesClearing, actualProperties)
      }
    }
  }
}