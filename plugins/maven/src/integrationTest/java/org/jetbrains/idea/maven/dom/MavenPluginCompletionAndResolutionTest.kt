// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom

import com.intellij.maven.testFramework.fixtures.MavenDomTestFixture
import com.intellij.maven.testFramework.fixtures.MavenDomTestFixtureIndices
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.findPsiFile
import com.intellij.maven.testFramework.fixtures.getActualMavenVersion
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenDomFixture
import com.intellij.maven.testFramework.fixtures.mavenVersionIsOrMoreThan
import com.intellij.maven.testFramework.fixtures.removeFromLocalRepository
import com.intellij.maven.testFramework.fixtures.updateProjectPom
import com.intellij.openapi.application.readAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.xml.XmlTag
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.fixtures.assertCompletionVariants
import org.jetbrains.idea.maven.fixtures.assertCompletionVariantsInclude
import org.jetbrains.idea.maven.fixtures.assertDocumentation
import org.jetbrains.idea.maven.fixtures.assertResolved
import org.jetbrains.idea.maven.fixtures.assertUnresolved
import org.jetbrains.idea.maven.fixtures.checkHighlighting
import org.jetbrains.idea.maven.fixtures.getCompletionVariants
import org.jetbrains.idea.maven.fixtures.getDependencyCompletionVariants
import org.jetbrains.idea.maven.fixtures.getReferenceAtCaret
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenPluginCompletionAndResolutionTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenDomFixture(
    mavenVersion = mavenVersion, modelVersion = modelVersion,
    initialPom = MavenDomTestFixture.DEFAULT_POM,
    skipPluginResolution = false,
    indices = MavenDomTestFixtureIndices("plugins", listOf()),
  )

  @Test
  fun testGroupIdCompletion() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <groupId><caret></groupId>
                           </plugin>
                         </plugins>
                       </build>
                       """.trimIndent())

    maven.assertCompletionVariantsInclude(maven.projectPom, maven.RENDERING_TEXT, "intellij.test", "test", "org.apache.maven.plugins", "org.codehaus.mojo",
                                          "org.codehaus.plexus")
  }

  @Test
  fun testArtifactIdCompletion() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <groupId>org.apache.maven.plugins</groupId>
                             <artifactId><caret></artifactId>
                           </plugin>
                         </plugins>
                       </build>
                       """.trimIndent())

    maven.assertCompletionVariantsInclude(maven.projectPom, maven.RENDERING_TEXT, "maven-site-plugin", "maven-eclipse-plugin", "maven-war-plugin",
                                          "maven-resources-plugin", "maven-surefire-plugin", "maven-jar-plugin", "maven-clean-plugin",
                                          "maven-install-plugin", "maven-compiler-plugin", "maven-deploy-plugin")
  }

  @Test
  fun testVersionCompletion() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <groupId>org.apache.maven.plugins</groupId>
                             <artifactId>maven-compiler-plugin</artifactId>
                             <version><caret></version>
                           </plugin>
                         </plugins>
                       </build>
                       """.trimIndent())


    if (maven.mavenVersionIsOrMoreThan("3.9.7")) {
      val v = maven.projectsManager.projects[0].plugins.first { it.artifactId == "maven-compiler-plugin" }.version
      maven.assertCompletionVariants(maven.projectPom, "2.0.2", "3.1", "3.10.1", "3.11.0", v)
    }
    else if (maven.mavenVersionIsOrMoreThan("3.9.3")) {
      maven.assertCompletionVariants(maven.projectPom, "2.0.2", "3.1", "3.10.1", "3.11.0")
    }
    else if (maven.mavenVersionIsOrMoreThan("3.9.0")) {
      maven.assertCompletionVariants(maven.projectPom, "2.0.2", "3.1", "3.10.1")
    }
    else if (maven.getActualMavenVersion() in setOf("3.8.9", "3.6.3", "3.5.4", "3.3.9")) {
      maven.assertCompletionVariants(maven.projectPom, maven.RENDERING_TEXT, "2.0.2", "3.1", "3.10.1", "3.11.0")
    }
    else {
      maven.assertCompletionVariants(maven.projectPom, "2.0.2", "3.1")
    }
  }

  @Test
  fun testPluginWithoutGroupIdResolution() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId><caret>maven-surefire-plugin</artifactId>
                             <version>2.12.4</version>
                           </plugin>
                         </plugins>
                       </build>
                       """.trimIndent())

    val pluginPath =
      "plugins/org/apache/maven/plugins/maven-surefire-plugin/2.12.4/maven-surefire-plugin-2.12.4.pom"
    val filePath = maven.repositoryHelper.getTestData(pluginPath)
    val f = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(filePath)
    assertNotNull(f, "file: $filePath not exists!")
    maven.assertResolved(maven.projectPom, maven.findPsiFile(f))
  }

  @Test
  fun testArtifactWithoutGroupCompletion() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId><caret></artifactId>
                           </plugin>
                         </plugins>
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
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin><caret></plugin>
                         </plugins>
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
  fun testVersionWithoutGroupCompletion() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId>maven-compiler-plugin</artifactId>
                             <version><caret></version>
                           </plugin>
                         </plugins>
                       </build>
                       """.trimIndent())

    if (maven.mavenVersionIsOrMoreThan("3.9.7")) {
      val v = maven.projectsManager.projects[0].plugins.first { it.artifactId == "maven-compiler-plugin" }.version
      maven.assertCompletionVariants(maven.projectPom, maven.RENDERING_TEXT, "2.0.2", "3.1", "3.10.1", "3.11.0", v)
    }
    else if (maven.mavenVersionIsOrMoreThan("3.9.3")) {
      maven.assertCompletionVariants(maven.projectPom, maven.RENDERING_TEXT, "2.0.2", "3.1", "3.10.1", "3.11.0")
    }
    else if (maven.mavenVersionIsOrMoreThan("3.9.0")) {
      maven.assertCompletionVariants(maven.projectPom, maven.RENDERING_TEXT, "2.0.2", "3.1", "3.10.1")
    }
    else if (maven.getActualMavenVersion() in setOf("3.8.9", "3.6.3", "3.5.4", "3.3.9")) {
      maven.assertCompletionVariants(maven.projectPom, maven.RENDERING_TEXT, "2.0.2", "3.1", "3.10.1", "3.11.0")
    }
    else {
      maven.assertCompletionVariants(maven.projectPom, maven.RENDERING_TEXT, "2.0.2", "3.1")
    }
  }

  @Test
  fun testResolvingPlugins() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId><caret>maven-compiler-plugin</artifactId>
                           </plugin>
                         </plugins>
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

    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId><caret>maven-compiler-plugin</artifactId>
                           </plugin>
                         </plugins>
                       </build>
                       """.trimIndent())

    val ref = maven.getReferenceAtCaret(maven.projectPom)
    assertNotNull(ref)
    readAction {
      ref!!.resolve() // shouldn't throw;
    }
    Unit
  }

  @Test
  fun testDoNotHighlightAbsentGroupIdAndVersion() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId>maven-compiler-plugin</artifactId>
                           </plugin>
                         </plugins>
                       </build>
                       """.trimIndent())
    maven.checkHighlighting()
  }

  @Test
  fun testHighlightingAbsentArtifactId() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <<error descr="'artifactId' child tag should be defined">plugin</error>>
                           </plugin>
                         </plugins>
                       </build>
                       """.trimIndent())

    maven.checkHighlighting()
  }

  @Test
  fun testBasicConfigurationCompletion() = runBlocking {
    putCaretInConfigurationSection()
    maven.assertCompletionVariantsInclude(maven.projectPom, "source", "target")
  }

  @Test
  fun testIncludingConfigurationParametersFromAllTheMojos() = runBlocking {
    putCaretInConfigurationSection()
    maven.assertCompletionVariantsInclude(maven.projectPom, "excludes", "testExcludes")
  }

  private suspend fun putCaretInConfigurationSection() {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId>maven-compiler-plugin</artifactId>
                             <configuration>
                               <<caret>
                             </configuration>
                           </plugin>
                         </plugins>
                       </build>
                       """.trimIndent())
  }

  @Test
  fun testNoParametersForUnknownPlugin() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId>unknown-plugin</artifactId>
                             <configuration>
                               <<caret>
                             </configuration>
                           </plugin>
                         </plugins>
                       </build>
                       """.trimIndent())

    maven.assertCompletionVariants(maven.projectPom)
  }

  @Test
  fun testNoParametersIfNothingIsSpecified() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <configuration>
                               <<caret>
                             </configuration>
                           </plugin>
                         </plugins>
                       </build>
                       """.trimIndent())

    maven.assertCompletionVariants(maven.projectPom)
  }

  @Test
  fun testResolvingParameters() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId>maven-compiler-plugin</artifactId>
                             <configuration>
                               <<caret>includes></includes>
                             </configuration>
                           </plugin>
                         </plugins>
                       </build>
                       """.trimIndent())

    val ref = maven.getReferenceAtCaret(maven.projectPom)
    assertNotNull(ref)
    readAction {
      val resolved = ref!!.resolve()
      assertNotNull(resolved)
      assertTrue(resolved is XmlTag)
      assertEquals("parameter", (resolved as XmlTag?)!!.getName())
      assertEquals("includes", resolved!!.findFirstSubTag("name")!!.getValue().getText())
    }
  }

  @Test
  fun testResolvingInnerParamatersIntoOuter() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId>maven-compiler-plugin</artifactId>
                             <configuration>
                               <includes>
                                 <<caret>include></include        </includes>
                             </configuration>
                           </plugin>
                         </plugins>
                       </build>
                       """.trimIndent())

    val ref = maven.getReferenceAtCaret(maven.projectPom)
    assertNotNull(ref)
    readAction {
      val resolved = ref!!.resolve()
      assertNotNull(resolved)
      assertTrue(resolved is XmlTag)
      assertEquals("parameter", (resolved as XmlTag?)!!.getName())
      assertEquals("includes", resolved!!.findFirstSubTag("name")!!.getValue().getText())
    }
  }

  @Test
  fun testGoalsCompletionAndHighlighting() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId>maven-compiler-plugin</artifactId>
                             <executions>
                               <execution>
                                 <goals>
                                   <goal><caret></goal>
                                 </goals>
                               </execution>
                             </executions>
                           </plugin>
                         </plugins>
                       </build>
                       """.trimIndent())

    maven.assertCompletionVariants(maven.projectPom, "help", "compile", "testCompile")

    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId>maven-compiler-plugin</artifactId>
                             <executions>
                               <execution>
                                 <goals>
                                   <goal><error>xxx</error></goal>
                                 </goals>
                               </execution>
                             </executions>
                           </plugin>
                         </plugins>
                       </build>
                       """.trimIndent())

    maven.checkHighlighting()
  }

  @Test
  fun testDontHighlightGoalsForUnresolvedPlugin() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId>maven-compiler-plugin</artifactId>
                             <executions>
                               <execution>
                                 <goals>
                                   <goal>compile</goal>
                                   <goal><error>unknownGoal</error></goal>
                                 </goals>
                               </execution>
                             </executions>
                           </plugin>
                           <plugin>
                             <artifactId><error>unresolved-plugin</error></artifactId>
                             <executions>
                               <execution>
                                 <goals>
                                   <goal>compile</goal>
                                   <goal>unknownGoal</goal>
                                 </goals>
                               </execution>
                             </executions>
                           </plugin>
                         </plugins>
                       </build>
                       """.trimIndent())

    maven.checkHighlighting()
  }

  @Test
  fun testGoalsCompletionAndHighlightingInPluginManagement() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <pluginManagement>
                         <plugins>
                           <plugin>
                             <artifactId>maven-compiler-plugin</artifactId>
                             <executions>
                               <execution>
                                 <goals>
                                   <goal><caret></goal>
                                 </goals>
                               </execution>
                             </executions>
                           </plugin>
                         </plugins>
                         </pluginManagement>
                       </build>
                       """.trimIndent())

    maven.assertCompletionVariants(maven.projectPom, "help", "compile", "testCompile")

    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <pluginManagement>
                         <plugins>
                           <plugin>
                             <artifactId>maven-compiler-plugin</artifactId>
                             <executions>
                               <execution>
                                 <goals>
                                   <goal><error>xxx</error></goal>
                                 </goals>
                               </execution>
                             </executions>
                           </plugin>
                         </plugins>
                         </pluginManagement>
                       </build>
                       """.trimIndent())

    maven.checkHighlighting()
  }

  @Test
  fun testGoalsResolution() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId>maven-compiler-plugin</artifactId>
                             <executions>
                               <execution>
                                 <goals>
                                   <goal><caret>compile</goal>
                                 </goals>
                               </execution>
                             </executions>
                           </plugin>
                         </plugins>
                       </build>
                       """.trimIndent())

    val ref = maven.getReferenceAtCaret(maven.projectPom)
    assertNotNull(ref)
    readAction {
      val resolved = ref!!.resolve()
      assertNotNull(resolved)
      assertTrue(resolved is XmlTag)
      assertEquals("mojo", (resolved as XmlTag?)!!.getName())
      assertEquals("compile", resolved!!.findFirstSubTag("goal")!!.getValue().getText())
    }
  }


  @Test
  fun testMavenDependencyReferenceProvider() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                               <plugins>
                                   <plugin>
                                       <artifactId>maven-invoker-plugin</artifactId>
                                       <version>3.2.1</version>
                                       <executions>
                                           <execution>
                                               <id>pre-integration-tests</id>
                                               <goals>
                                                   <goal>install</goal>
                                               </goals>
                                               <configuration>
                                                   <extraArtifacts>
                                                       <extraArtifact>junit:<caret>junit:4.8</extraArtifact>
                                                   </extraArtifacts>
                                               </configuration>
                                           </execution>
                                       </executions>
                                   </plugin>
                               </plugins>
                           </build>
                       """.trimIndent())

    val ref = maven.getReferenceAtCaret(maven.projectPom)
    assertNotNull(ref)
  }


  @Test
  fun testGoalsCompletionAndResolutionForUnknownPlugin() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId>xxx</artifactId>
                             <executions>
                               <execution>
                                 <goals>
                                   <goal><caret></goal>
                                 </goals>
                               </execution>
                             </executions>
                           </plugin>
                         </plugins>
                       </build>
                       """.trimIndent())
    maven.assertCompletionVariants(maven.projectPom)

    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId>xxx</artifactId>
                             <executions>
                               <execution>
                                 <goals>
                                   <goal><caret>compile</goal>
                                 </goals>
                               </execution>
                             </executions>
                           </plugin>
                         </plugins>
                       </build>
                       """.trimIndent())
    maven.assertUnresolved(maven.projectPom)
  }

  @Test
  fun testPhaseCompletionAndHighlighting() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId>maven-compiler-plugin</artifactId>
                             <executions>
                               <execution>
                                 <phase><caret></phase>
                               </execution>
                             </executions>
                           </plugin>
                         </plugins>
                       </build>
                       """.trimIndent())

    maven.assertCompletionVariantsInclude(maven.projectPom, "clean", "compile", "package")

    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId>maven-compiler-plugin</artifactId>
                             <executions>
                               <execution>
                                 <phase><error>xxx</error></phase>
                               </execution>
                             </executions>
                           </plugin>
                         </plugins>
                       </build>
                       """.trimIndent())

    maven.checkHighlighting()
  }

  @Test
  fun testNoExecutionParametersIfNoGoalNorIdAreSpecified() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId>maven-compiler-plugin</artifactId>
                             <executions>
                               <execution>
                                 <configuration>
                                   <<caret>
                                 </configuration>
                               </execution>
                             </executions>
                           </plugin>
                         </plugins>
                       </build>
                       """.trimIndent())

    maven.assertCompletionVariants(maven.projectPom)
  }

  @Test
  fun testExecutionParametersForSpecificGoal() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId>maven-compiler-plugin</artifactId>
                             <executions>
                               <execution>
                                 <goals>
                                   <goal>compile</goal>
                                 </goals>
                                 <configuration>
                                   <<caret>
                                 </configuration>
                               </execution>
                             </executions>
                           </plugin>
                         </plugins>
                       </build>
                       """.trimIndent())

    val variants = maven.getCompletionVariants(maven.projectPom)
    assertTrue(variants.contains("excludes"), variants.toString())
    assertFalse(variants.contains("testExcludes"), variants.toString())
  }

  @Test
  fun testExecutionParametersForDefaultGoalExecution() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId>maven-compiler-plugin</artifactId>
                             <executions>
                               <execution>
                                 <id>default-compile</id>
                                 <configuration>
                                   <<caret>
                                 </configuration>
                               </execution>
                             </executions>
                           </plugin>
                         </plugins>
                       </build>
                       """.trimIndent())

    val variants = maven.getCompletionVariants(maven.projectPom)
    assertTrue(variants.contains("excludes"), variants.toString())
    assertFalse(variants.contains("testExcludes"), variants.toString())
  }

  @Test
  fun testExecutionParametersForSeveralSpecificGoals() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId>maven-compiler-plugin</artifactId>
                             <executions>
                               <execution>
                                 <goals>
                                   <goal>compile</goal>
                                   <goal>testCompile</goal>
                                 </goals>
                                 <configuration>
                                   <<caret>
                                 </configuration>
                               </execution>
                             </executions>
                           </plugin>
                         </plugins>
                       </build>
                       """.trimIndent())

    maven.assertCompletionVariantsInclude(maven.projectPom, "excludes", "testExcludes")
  }

  @Test
  fun testAliasCompletion() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId>maven-war-plugin</artifactId>
                             <configuration>
                               <<caret>
                             </configuration>
                           </plugin>
                         </plugins>
                       </build>
                       """.trimIndent())

    maven.assertCompletionVariantsInclude(maven.projectPom, "warSourceExcludes", "excludes")
  }

  @Test
  fun testListElementsCompletion() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId>maven-compiler-plugin</artifactId>
                             <configuration>
                               <excludes>
                                 <exclude></exclude>
                                 <<caret>
                               </excludes>
                             </configuration>
                           </plugin>
                         </plugins>
                       </build>
                       """.trimIndent())

    maven.assertCompletionVariants(maven.projectPom, "exclude")
  }

  @Test
  fun testListElementWhatHasUnpluralizedNameCompletion() = runBlocking {
    // NPE test - StringUtil.unpluralize returns null.

    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId>maven-eclipse-plugin</artifactId>
                             <configuration>
                               <additionalConfig>
                                 <<caret>
                               </additionalConfig>
                             </configuration>
                           </plugin>
                         </plugins>
                       </build>
                       """.trimIndent())

    maven.assertCompletionVariants(maven.projectPom, "additionalConfig", "config")
  }

  @Test
  fun testDoNotHighlightUnknownElementsUnderLists() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId>maven-compiler-plugin</artifactId>
                             <configuration>
                               <excludes>
                                 <foo>foo</foo>
                               </excludes>
                             </configuration>
                           </plugin>
                         </plugins>
                       </build>
                       """.trimIndent())

    maven.checkHighlighting()
  }

  @Test
  fun testArrayElementsCompletion() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId>maven-war-plugin</artifactId>
                             <configuration>
                               <webResources>
                                 <webResource></webResource>
                                 <<caret>
                               </webResources>
                             </configuration>
                           </plugin>
                         </plugins>
                       </build>
                       """.trimIndent())

    maven.assertCompletionVariants(maven.projectPom, "resource", "webResource")
  }

  @Test
  fun testCompletionInCustomObjects() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId>maven-war-plugin</artifactId>
                             <configuration>
                               <webResources>
                                 <webResource>
                                   <<caret>
                                 </webResource>
                               </webResources>
                             </configuration>
                           </plugin>
                         </plugins>
                       </build>
                       """.trimIndent())

    maven.assertCompletionVariants(maven.projectPom)
  }

  @Test
  fun testDocumentationForParameter() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId>maven-compiler-plugin</artifactId>
                             <configuration>
                               <s<caret>ource></source>
                             </configuration>
                           </plugin>
                         </plugins>
                       </build>
                       """.trimIndent())

    if (maven.mavenVersionIsOrMoreThan("3.9.7")) {
      maven.assertDocumentation("""
          Type: <b>java.lang.String</b><br>Default Value: <b>1.8</b><br>Expression: <b>${'$'}{maven.compiler.source}</b><br><br><i>The -source argument for the Java compiler.

          NOTE: 

          Since 3.8.0 the default value has changed from 1.5 to 1.6

          Since 3.9.0 the default value has changed from 1.6 to 1.7
          
          Since 3.11.0 the default value has changed from 1.7 to 1.8
          
          See also: javac -source <https://docs.oracle.com/en/java/javase/17/docs/specs/man/javac.html#option-source></i>
          """.trimIndent())
    }
    else if (maven.mavenVersionIsOrMoreThan("3.9.3")) {
      maven.assertDocumentation("""
          Type: <b>java.lang.String</b><br>Default Value: <b>1.8</b><br>Expression: <b>${'$'}{maven.compiler.source}</b><br><br><i>The -source argument for the Java compiler.

          NOTE: 

          Since 3.8.0 the default value has changed from 1.5 to 1.6

          Since 3.9.0 the default value has changed from 1.6 to 1.7

          Since 3.11.0 the default value has changed from 1.7 to 1.8</i>
          """.trimIndent())
    }
    else if (maven.mavenVersionIsOrMoreThan("3.9.0")) {
      maven.assertDocumentation("""
          Type: <b>java.lang.String</b><br>Default Value: <b>1.7</b><br>Expression: <b>${'$'}{maven.compiler.source}</b><br><br><i><p>The -source argument for the Java compiler.</p>

          <b>NOTE: </b>Since 3.8.0 the default value has changed from 1.5 to 1.6.
          Since 3.9.0 the default value has changed from 1.6 to 1.7</i>
          """.trimIndent())
    }
    else {
      maven.assertDocumentation(
        "Type: <b>java.lang.String</b><br>Default Value: <b>1.5</b><br>Expression: <b>\${maven.compiler.source}</b><br><br><i>The -source argument for the Java compiler.</i>")
    }
  }

  @Test
  fun testDoNotCompleteNorHighlightNonPluginConfiguration() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <ciManagement>
                         <system>foo</system>
                         <notifiers>
                           <notifier>
                             <type>mail</type>
                             <configuration>
                               <address>foo@bar.com</address>
                             </configuration>
                           </notifier>
                         </notifiers>
                       </ciManagement>
                       """.trimIndent())

    maven.checkHighlighting()
  }

  @Test
  fun testDoNotHighlightInnerParameters() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId>maven-compiler-plugin</artifactId>
                             <configuration>
                               <source>
                                 <foo>*.java</foo>
                               </source>
                             </configuration>
                           </plugin>
                         </plugins>
                       </build>
                       """.trimIndent())

    maven.checkHighlighting()
  }

  @Test
  fun testDoNotHighlightRequiredParametersWithDefaultValues() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <groupId>org.apache.maven.plugins</groupId>
                             <artifactId>maven-surefire-plugin</artifactId>
                             <version>2.4.3</version>
                             <configuration>
                             </configuration>
                           </plugin>
                         </plugins>
                       </build>
                       """.trimIndent())

    maven.checkHighlighting() // surefire plugin has several required parameters with default values.
  }

  @Test
  fun testDoNotHighlightInnerParameterAttributes() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId>maven-compiler-plugin</artifactId>
                             <configuration>
                               <includes value1='1'>
                                 <include value2='2'/>
                               </includes>
                               <source value3='3'>
                                 <child value4='4'/>
                               </source>
                             </configuration>
                           </plugin>
                         </plugins>
                       </build>
                       """.trimIndent())

    maven.checkHighlighting()
  }

  @Test
  fun testDoNotCompleteParameterAttributes() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId>maven-compiler-plugin</artifactId>
                             <configuration>
                               <source <caret>/>
                             </configuration>
                           </plugin>
                         </plugins>
                       </build>
                       """.trimIndent())

    maven.assertCompletionVariants(maven.projectPom, "combine.children", "combine.self")
  }

  @Test
  fun testWorksWithPropertiesInPluginId() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <properties>
                         <plugin.groupId>org.apache.maven.plugins</plugin.groupId>
                         <plugin.artifactId>maven-compiler-plugin</plugin.artifactId>
                         <plugin.version>2.0.2</plugin.version>
                       </properties>
                       """.trimIndent())
    maven.importProjectAsync() // let us recognize the properties first

    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <properties>
                         <plugin.groupId>org.apache.maven.plugins</plugin.groupId>
                         <plugin.artifactId>maven-compiler-plugin</plugin.artifactId>
                         <plugin.version>2.0.2</plugin.version>
                       </properties>
                       <build>
                         <plugins>
                           <plugin>
                             <groupId>${'$'}{plugin.groupId}</groupId>
                             <artifactId>${'$'}{plugin.artifactId}</artifactId>
                             <version>${'$'}{plugin.version}</version>
                             <configuration>
                               <source></source>
                             </configuration>
                           </plugin>
                         </plugins>
                       </build>
                       """.trimIndent())

    maven.checkHighlighting()
  }

  @Test
  fun testDoNotHighlightPropertiesForUnknownPlugins() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId><error>foo.bar</error></artifactId>
                             <configuration>
                               <prop>
                                 <value>foo</value>
                               </prop>
                             </configuration>
                           </plugin>
                         </plugins>
                       </build>
                       """.trimIndent())

    maven.checkHighlighting()
  }

  @Test
  fun testTellNobodyThatIdeaIsRulezzz() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId>maven-compiler-plugin</artifactId>
                             <configuration>
                               <includes>
                                 <bar a<caret> />
                               </includes>
                             </configuration>
                           </plugin>
                         </plugins>
                       </build>
                       """.trimIndent())

    maven.assertCompletionVariants(maven.projectPom)
  }
}
