// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom

import com.intellij.maven.testFramework.MavenDomTestCase
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.modules
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import junit.framework.TestCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.intellij.lang.annotations.Language
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class MavenDomAnnotatorTest : MavenDomTestCase() {
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
    val modulePom = createModulePomNonVfs("m", modulePomContent)

    importProjectAsync("""
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
      val file = PsiManager.getInstance(project).findFile(virtualFile)!!
      val text = file.text
      TestCase.assertTrue("Unexpected pom content:\n$text", text.contains(expectedFileContent))

      fixture.configureFromExistingVirtualFile(virtualFile)
      val actualProperties = fixture.doHighlighting()
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

  private fun createModulePomNonVfs(
    relativePath: String,
    @Language(value = "XML", prefix = "<project>", suffix = "</project>") xml: String,
  ): VirtualFile {
    val folderPath = Path.of(projectPath, relativePath)
    if (!Files.exists(folderPath)) {
      Files.createDirectories(folderPath)
    }
    val file = folderPath.resolve("pom.xml")
    val content = createPomXml(xml)
    Files.write(file, content.toByteArray(StandardCharsets.UTF_8))
    return LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file)!!
  }
}