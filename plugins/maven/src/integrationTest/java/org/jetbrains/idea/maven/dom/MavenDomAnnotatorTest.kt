// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom

import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.codeInsight.navigation.NavigationGutterIconRenderer
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assumeMaven4
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenDomFixture
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.openapi.project.modules
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.UsefulTestCase.assertSize
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.dom.annotator.MavenDomGutterAnnotatorLogger
import org.jetbrains.idea.maven.fixtures.doHighlighting
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenLog
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenDomAnnotatorTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenDomFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  @BeforeEach
  fun setUp() {
    MavenDomGutterAnnotatorLogger.setLogLevel(LogLevel.WARNING)
  }

  @AfterEach
  fun tearDown() {
    MavenDomGutterAnnotatorLogger.resetLogLevel()
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
    val modulePom = maven.createModulePom("m", modulePomContent)

    maven.createProjectPom("""
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

    maven.importProjectAsync()

    withContext(Dispatchers.EDT) {
      val modules = maven.project.modules
      assertSize(2, modules)
      val projectsManager = MavenProjectsManager.getInstance(maven.project)
      val tree = maven.projectsManager.projectsTree
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
    val modulePom = maven.createModulePom("m", modulePomContent)

    maven.importProjectAsync("""
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
    val modulePom = maven.createModulePom("m", modulePomContent)

    maven.importProjectAsync("""
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

  @Test
  fun testAnnotateParentIfParentPomUsesSiblingRelativePath() = runBlocking {
    maven.assumeMaven4()
    maven.createProjectPom("""
<groupId>test</groupId>
<artifactId>project</artifactId>
<version>1.1</version>
<packaging>pom</packaging>

<modules>
  <module>integrations-parent</module>
  <module>integrations</module>
</modules>
""")

    maven.createModulePom("integrations-parent", """
<groupId>test</groupId>
<artifactId>integrations-parent</artifactId>
<version>1.1</version>
<packaging>pom</packaging>
""")

    // The intermediate POM reaches its own parent through a sibling directory, and it inherits its own version.
    val integrationsPom = maven.createModulePom("integrations", """
<parent>
  <groupId>test</groupId>
  <artifactId>integrations-parent</artifactId>
  <version>1.1</version>
  <relativePath>../integrations-parent</relativePath>
</parent>

<artifactId>integrations</artifactId>
<packaging>pom</packaging>

<modules>
  <module>integrations-helper</module>
</modules>
""")

    val helperPom = maven.createModulePom("integrations/integrations-helper", """
<parent>
  <groupId>test</groupId>
  <artifactId>integrations</artifactId>
</parent>

<artifactId>integrations-helper</artifactId>
""")

    maven.importProjectAsync()

    // The parent gutter icon is the only icon in the child POM, and it must navigate to the intermediate POM.
    assertEquals(listOf(integrationsPom), gutterTargetFiles(helperPom))
  }

  @Test
  fun testChildrenProjectsOrder() = runBlocking {
    maven.createModulePom("module-c", """
<parent>
  <groupId>test</groupId>
  <artifactId>project</artifactId>
  <version>1</version>
</parent>
<artifactId>module-c</artifactId>
""")
    maven.createModulePom("module-a", """
<parent>
  <groupId>test</groupId>
  <artifactId>project</artifactId>
  <version>1</version>
</parent>
<artifactId>module-a</artifactId>
""")
    maven.createModulePom("module-d", """
<parent>
  <groupId>test</groupId>
  <artifactId>project</artifactId>
  <version>1</version>
</parent>
<artifactId>module-d</artifactId>
""")
    maven.createModulePom("module-b", """
<parent>
  <groupId>test</groupId>
  <artifactId>project</artifactId>
  <version>1</version>
</parent>
<artifactId>module-b</artifactId>
""")

    maven.importProjectAsync("""
<groupId>test</groupId>
<artifactId>project</artifactId>
<version>1</version>
<packaging>pom</packaging>

<modules>
  <module>module-c</module>
  <module>module-a</module>
  <module>module-d</module>
  <module>module-b</module>
</modules>
""")

    val artifactIds = readAction {
      val model = MavenDomUtil.getMavenDomProjectModel(maven.project, maven.projectPom)!!
      MavenDomProjectProcessorUtils.getChildrenProjects(model)
        .map { it.artifactId.stringValue }
    }

    assertEquals(listOf("module-a", "module-b", "module-c", "module-d"), artifactIds)
  }

  /**
   * Returns the file of every navigation target of every gutter icon in [pom].
   *
   * The Maven annotator builds each icon with [NavigationGutterIconBuilder], which produces a
   * [NavigationGutterIconRenderer]. The target of the parent icon is the `project` tag of the parent POM.
   */
  private suspend fun gutterTargetFiles(pom: VirtualFile): List<VirtualFile> {
    val renderers = maven.doHighlighting(pom)
      .mapNotNull { it.gutterIconRenderer }
      .filterIsInstance<NavigationGutterIconRenderer>()
    return readAction {
      renderers.flatMap { it.targetElements }.map { it.containingFile.virtualFile }
    }
  }

  private suspend fun checkGutters(virtualFile: VirtualFile, expectedFileContent: String, expectedProperties: Collection<String>) {
    withContext(Dispatchers.EDT) {
      //maybe narrower
      //maybe readaction
      writeIntentReadAction {
        val file = PsiManager.getInstance(maven.project).findFile(virtualFile)!!
        val text = file.text
        assertTrue(text.contains(expectedFileContent), "Unexpected pom content:\n$text")

        //maven.fixture.configureFromExistingVirtualFile(virtualFile)
        maven.fixture.configureByText("pom.xml", text)
        val highlighting = maven.fixture.doHighlighting()
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