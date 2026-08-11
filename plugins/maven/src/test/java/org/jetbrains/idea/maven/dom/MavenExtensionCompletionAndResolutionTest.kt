// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom

import com.intellij.maven.testFramework.fixtures.MavenDomTestFixture
import com.intellij.maven.testFramework.fixtures.MavenDomTestFixtureIndices
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.findPsiFile
import com.intellij.maven.testFramework.fixtures.mavenDomFixture
import com.intellij.maven.testFramework.fixtures.removeFromLocalRepository
import com.intellij.openapi.application.readAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.fixtures.assertCompletionVariantsInclude
import org.jetbrains.idea.maven.fixtures.assertResolved
import org.jetbrains.idea.maven.fixtures.checkHighlighting
import org.jetbrains.idea.maven.fixtures.getDependencyCompletionVariants
import org.jetbrains.idea.maven.fixtures.getReferenceAtCaret
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenExtensionCompletionAndResolutionTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenDomFixture(
    mavenVersion = mavenVersion, modelVersion = modelVersion,
    initialPom = MavenDomTestFixture.DEFAULT_POM,
    skipPluginResolution = false,
    indices = MavenDomTestFixtureIndices("plugins", listOf()),
  )

  @Test
  fun testGroupIdCompletion() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <extensions>
                           <extension>
                             <groupId><caret></groupId>
                           </extension>
                         </extensions>
                       </build>
                       """.trimIndent())

    maven.assertCompletionVariantsInclude(maven.projectPom, maven.RENDERING_TEXT,
                                          "org.apache.maven.plugins", "org.codehaus.plexus", "test", "intellij.test", "org.codehaus.mojo")
  }

  @Test
  fun testArtifactIdCompletion() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <extensions>
                           <extension>
                             <groupId>org.apache.maven.plugins</groupId>
                             <artifactId><caret></artifactId>
                           </extension>
                         </extensions>
                       </build>
                       """.trimIndent())


    maven.assertCompletionVariantsInclude(maven.projectPom, maven.RENDERING_TEXT, "maven-site-plugin", "maven-eclipse-plugin", "maven-war-plugin",
                                          "maven-resources-plugin", "maven-surefire-plugin", "maven-jar-plugin", "maven-clean-plugin",
                                          "maven-install-plugin", "maven-compiler-plugin", "maven-deploy-plugin")
  }

  @Test
  fun testArtifactWithoutGroupCompletion() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <extensions>
                           <extension>
                             <artifactId><caret></artifactId>
                           </extension>
                         </extensions>
                       </build>
                       """.trimIndent())

    maven.assertCompletionVariantsInclude(maven.projectPom, maven.RENDERING_TEXT,
                                          "maven-clean-plugin",
                                          "maven-jar-plugin",
                                          "maven-war-plugin",
                                          "maven-deploy-plugin",
                                          "maven-resources-plugin",
                                          "maven-eclipse-plugin",
                                          "maven-install-plugin",
                                          "maven-compiler-plugin",
                                          "maven-site-plugin",
                                          "maven-surefire-plugin",
                                          "build-helper-maven-plugin")
  }

  @Test
  fun testCompletionInsideTag() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <extensions>
                           <extension><caret></extension>
                         </extensions>
                       </build>
                       """.trimIndent())
    val variants = maven.getDependencyCompletionVariants(maven.projectPom) { it!!.getGroupId() + ":" + it.getArtifactId() }

    UsefulTestCase.assertContainsElements(variants,
                                          "org.apache.maven.plugins:maven-clean-plugin",
                                          "org.apache.maven.plugins:maven-compiler-plugin",
                                          "org.apache.maven.plugins:maven-deploy-plugin",
                                          "org.apache.maven.plugins:maven-eclipse-plugin",
                                          "org.apache.maven.plugins:maven-install-plugin",
                                          "org.apache.maven.plugins:maven-jar-plugin",
                                          "org.apache.maven.plugins:maven-resources-plugin",
                                          "org.apache.maven.plugins:maven-site-plugin",
                                          "org.apache.maven.plugins:maven-surefire-plugin",
                                          "org.apache.maven.plugins:maven-war-plugin",
                                          "org.codehaus.mojo:build-helper-maven-plugin",
                                          "test:project")
  }

  @Test
  fun testResolving() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <extensions>
                           <extension>
                             <artifactId><caret>maven-compiler-plugin</artifactId>
                           </extension>
                         </extensions>
                       </build>
                       """.trimIndent())

    val pluginVersion = maven.projectsManager.projects[0].plugins.first { it.artifactId == "maven-compiler-plugin" }.version
    val pluginPath =
      "plugins/org/apache/maven/plugins/maven-compiler-plugin/$pluginVersion/maven-compiler-plugin-$pluginVersion.pom"
    val filePath = maven.repositoryHelper.getTestData(pluginPath)
    val f = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(filePath)
    assertNotNull(f, "file: $filePath not exists!")
    maven.assertResolved(maven.projectPom, maven.findPsiFile(f))
  }


  @Test
  fun testResolvingAbsentPlugins() = runBlocking {
    maven.removeFromLocalRepository("org/apache/maven/plugins/maven-compiler-plugin")

    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <extensions>
                           <extension>
                             <artifactId><caret>maven-compiler-plugin</artifactId>
                           </extension>
                         </extensions>
                       </build>
                       """.trimIndent())

    val ref = maven.getReferenceAtCaret(maven.projectPom)

    readAction {
      assertNotNull(ref)
      ref!!.resolve() // shouldn't throw;
    }
    return@runBlocking
  }

  @Test
  fun testDoNotHighlightAbsentGroupIdAndVersion() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <extensions>
                           <extension>
                             <artifactId>maven-compiler-plugin</artifactId>
                           </extension>
                         </extensions>
                       </build>
                       """.trimIndent())
    maven.checkHighlighting()
  }

  @Test
  fun testHighlightingAbsentArtifactId() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <extensions>
                           <<error descr="'artifactId' child tag should be defined">extension</error>>
                           </extension>
                         </extensions>
                       </build>
                       """.trimIndent())

    maven.checkHighlighting()
  }
}
