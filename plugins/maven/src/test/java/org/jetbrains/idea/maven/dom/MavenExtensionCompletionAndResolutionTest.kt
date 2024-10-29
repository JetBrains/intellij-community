// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom

import com.intellij.openapi.application.readAction
import com.intellij.openapi.vfs.LocalFileSystem
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.indices.MavenIndicesTestFixture
import org.junit.Test

class MavenExtensionCompletionAndResolutionTest : MavenDomWithIndicesTestCase() {
  override fun createIndicesFixture(): MavenIndicesTestFixture {
    return MavenIndicesTestFixture(dir.toPath(), project, testRootDisposable,"plugins")
  }

  override fun importProjectOnSetup(): Boolean {
    return true
  }

  @Test
  fun testGroupIdCompletion() = runBlocking {
    createProjectPom("""
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

    assertCompletionVariantsInclude(projectPom, RENDERING_TEXT,
                                    "org.apache.maven.plugins", "org.codehaus.plexus", "test", "intellij.test", "org.codehaus.mojo")
  }

  @Test
  fun testArtifactIdCompletion() = runBlocking {
    createProjectPom("""
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


    assertCompletionVariantsInclude(projectPom, RENDERING_TEXT, "maven-site-plugin", "maven-eclipse-plugin", "maven-war-plugin",
                                    "maven-resources-plugin", "maven-surefire-plugin", "maven-jar-plugin", "maven-clean-plugin",
                                    "maven-install-plugin", "maven-compiler-plugin", "maven-deploy-plugin")
  }

  @Test
  fun testArtifactWithoutGroupCompletion() = runBlocking {
    createProjectPom("""
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

    assertCompletionVariantsInclude(projectPom, RENDERING_TEXT,
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
                                    "build-helper-maven-plugin",
                                    "project")
  }

  @Test
  fun testCompletionInsideTag() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <extensions>
                           <extension><caret></extension>
                         </extensions>
                       </build>
                       """.trimIndent())
    val variants = getDependencyCompletionVariants(projectPom) { it!!.getGroupId() + ":" + it.getArtifactId() }

    assertContain(variants,
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
    createProjectPom("""
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

    val pluginVersion = getDefaultPluginVersion("org.apache.maven:maven-compiler-plugin")
    val pluginPath =
      "plugins/org/apache/maven/plugins/maven-compiler-plugin/$pluginVersion/maven-compiler-plugin-$pluginVersion.pom"
    val filePath = myIndicesFixture!!.repositoryHelper.getTestDataPath(pluginPath)
    val f = LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath)
    assertNotNull("file: $filePath not exists!", f)
    assertResolved(projectPom, findPsiFile(f))
  }


  @Test
  fun testResolvingAbsentPlugins() = runBlocking {
    removeFromLocalRepository("org/apache/maven/plugins/maven-compiler-plugin")

    createProjectPom("""
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

    val ref = getReferenceAtCaret(projectPom)

    readAction {
      assertNotNull(ref)
      ref!!.resolve() // shouldn't throw;
    }
    return@runBlocking
  }

  @Test
  fun testDoNotHighlightAbsentGroupIdAndVersion() = runBlocking {
    createProjectPom("""
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
    checkHighlighting()
  }

  @Test
  fun testHighlightingAbsentArtifactId() = runBlocking {
    createProjectPom("""
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

    checkHighlighting()
  }
}
