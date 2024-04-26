// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom

import com.intellij.openapi.application.EDT
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.xml.XmlTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.indices.MavenIndicesTestFixture
import org.junit.Test

class MavenPluginCompletionAndResolutionTest : MavenDomWithIndicesTestCase() {
  override fun createIndicesFixture(): MavenIndicesTestFixture {
    return MavenIndicesTestFixture(dir.toPath(), project, testRootDisposable,"plugins")
  }
  override fun setUp() = runBlocking {
    super.setUp()

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
  }

  @Test
  fun testGroupIdCompletion() = runBlocking {
    createProjectPom("""
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

    assertCompletionVariantsInclude(projectPom, RENDERING_TEXT, "intellij.test", "test", "org.apache.maven.plugins", "org.codehaus.mojo",
                                    "org.codehaus.plexus")
  }

  @Test
  fun testArtifactIdCompletion() = runBlocking {
    createProjectPom("""
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

    assertCompletionVariantsInclude(projectPom, RENDERING_TEXT, "maven-site-plugin", "maven-eclipse-plugin", "maven-war-plugin",
                                    "maven-resources-plugin", "maven-surefire-plugin", "maven-jar-plugin", "maven-clean-plugin",
                                    "maven-install-plugin", "maven-compiler-plugin", "maven-deploy-plugin")
  }

  @Test
  fun testVersionCompletion() = runBlocking {
    createProjectPom("""
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


    if (mavenVersionIsOrMoreThan("3.9.3")) {
      assertCompletionVariants(projectPom, "2.0.2", "3.1", "3.10.1", "3.11.0")
    }
    else if (mavenVersionIsOrMoreThan("3.9.0")) {
      assertCompletionVariants(projectPom, "2.0.2", "3.1", "3.10.1")
    }
    else {
      assertCompletionVariants(projectPom, "2.0.2", "3.1")
    }
  }

  @Test
  fun testPluginWithoutGroupIdResolution() = runBlocking {
    createProjectPom("""
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
    val filePath = myIndicesFixture!!.repositoryHelper.getTestDataPath(pluginPath)
    val f = LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath)
    assertNotNull("file: $filePath not exists!", f)
    withContext(Dispatchers.EDT) {
      assertResolved(projectPom, findPsiFile(f))
    }
  }

  @Test
  fun testArtifactWithoutGroupCompletion() = runBlocking {
    createProjectPom("""
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

    assertCompletionVariantsInclude(projectPom, RENDERING_TEXT,
                                    "project",
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
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin><caret></plugin>
                         </plugins>
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
  fun testVersionWithoutGroupCompletion() = runBlocking {
    createProjectPom("""
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

    if (mavenVersionIsOrMoreThan("3.9.3")) {
      assertCompletionVariants(projectPom, RENDERING_TEXT, "2.0.2", "3.1", "3.10.1", "3.11.0")
    }
    else if (mavenVersionIsOrMoreThan("3.9.0")) {
      assertCompletionVariants(projectPom, RENDERING_TEXT, "2.0.2", "3.1", "3.10.1")
    }
    else {
      assertCompletionVariants(projectPom, RENDERING_TEXT, "2.0.2", "3.1")
    }
  }

  @Test
  fun testResolvingPlugins() = runBlocking {
    createProjectPom("""
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
    val pluginVersion = getDefaultPluginVersion("org.apache.maven:maven-compiler-plugin")

    val pluginPath =
      "plugins/org/apache/maven/plugins/maven-compiler-plugin/$pluginVersion/maven-compiler-plugin-$pluginVersion.pom"
    val filePath = myIndicesFixture!!.repositoryHelper.getTestDataPath(pluginPath)
    val f = LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath)
    assertNotNull("file: $filePath not exists!", f)
    withContext(Dispatchers.EDT) {
      assertResolved(projectPom, findPsiFile(f))
    }
  }

  @Test
  fun testResolvingAbsentPlugins() = runBlocking {
    removeFromLocalRepository("org/apache/maven/plugins/maven-compiler-plugin")

    createProjectPom("""
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

    withContext(Dispatchers.EDT) {
      val ref = getReferenceAtCaret(projectPom)
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
                         <plugins>
                           <plugin>
                             <artifactId>maven-compiler-plugin</artifactId>
                           </plugin>
                         </plugins>
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
                         <plugins>
                           <<error descr="'artifactId' child tag should be defined">plugin</error>>
                           </plugin>
                         </plugins>
                       </build>
                       """.trimIndent())

    checkHighlighting()
  }

  @Test
  fun testBasicConfigurationCompletion() = runBlocking {
    putCaretInConfigurationSection()
    assertCompletionVariantsInclude(projectPom, "source", "target")
  }

  @Test
  fun testIncludingConfigurationParametersFromAllTheMojos() = runBlocking {
    putCaretInConfigurationSection()
    assertCompletionVariantsInclude(projectPom, "excludes", "testExcludes")
  }

  private fun putCaretInConfigurationSection() {
    createProjectPom("""
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
    createProjectPom("""
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

    assertCompletionVariants(projectPom)
  }

  @Test
  fun testNoParametersIfNothingIsSpecified() = runBlocking {
    createProjectPom("""
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

    assertCompletionVariants(projectPom)
  }

  @Test
  fun testResolvingParamaters() = runBlocking {
    createProjectPom("""
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

    withContext(Dispatchers.EDT) {
      val ref = getReferenceAtCaret(projectPom)
      assertNotNull(ref)
      val resolved = ref!!.resolve()
      assertNotNull(resolved)
      assertTrue(resolved is XmlTag)
      assertEquals("parameter", (resolved as XmlTag?)!!.getName())
      assertEquals("includes", resolved!!.findFirstSubTag("name")!!.getValue().getText())
    }
  }

  @Test
  fun testResolvingInnerParamatersIntoOuter() = runBlocking {
    createProjectPom("""
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

    withContext(Dispatchers.EDT) {
      val ref = getReferenceAtCaret(projectPom)
      assertNotNull(ref)
      val resolved = ref!!.resolve()
      assertNotNull(resolved)
      assertTrue(resolved is XmlTag)
      assertEquals("parameter", (resolved as XmlTag?)!!.getName())
      assertEquals("includes", resolved!!.findFirstSubTag("name")!!.getValue().getText())
    }
  }

  @Test
  fun testGoalsCompletionAndHighlighting() = runBlocking {
    createProjectPom("""
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

    assertCompletionVariants(projectPom, "help", "compile", "testCompile")

    createProjectPom("""
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

    checkHighlighting()
  }

  @Test
  fun testDontHighlightGoalsForUnresolvedPlugin() = runBlocking {
    createProjectPom("""
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

    checkHighlighting()
  }

  @Test
  fun testGoalsCompletionAndHighlightingInPluginManagement() = runBlocking {
    createProjectPom("""
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

    assertCompletionVariants(projectPom, "help", "compile", "testCompile")

    createProjectPom("""
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

    checkHighlighting()
  }

  @Test
  fun testGoalsResolution() = runBlocking {
    createProjectPom("""
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

    withContext(Dispatchers.EDT) {
      val ref = getReferenceAtCaret(projectPom)
      assertNotNull(ref)

      val resolved = ref!!.resolve()
      assertNotNull(resolved)
      assertTrue(resolved is XmlTag)
      assertEquals("mojo", (resolved as XmlTag?)!!.getName())
      assertEquals("compile", resolved!!.findFirstSubTag("goal")!!.getValue().getText())
    }
  }


  @Test
  fun testMavenDependencyReferenceProvider() = runBlocking {
    createProjectPom("""
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

    withContext(Dispatchers.EDT) {
      val ref = getReferenceAtCaret(projectPom)
      assertNotNull(ref)
    }
  }


  @Test
  fun testGoalsCompletionAndResolutionForUnknownPlugin() = runBlocking {
    createProjectPom("""
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
    withContext(Dispatchers.EDT) {
      assertCompletionVariants(projectPom)
    }

    createProjectPom("""
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
    withContext(Dispatchers.EDT) {
      assertUnresolved(projectPom)
    }
  }

  @Test
  fun testPhaseCompletionAndHighlighting() = runBlocking {
    createProjectPom("""
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

    assertCompletionVariantsInclude(projectPom, "clean", "compile", "package")

    createProjectPom("""
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

    checkHighlighting()
  }

  @Test
  fun testNoExecutionParametersIfNoGoalNorIdAreSpecified() = runBlocking {
    createProjectPom("""
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

    assertCompletionVariants(projectPom)
  }

  @Test
  fun testExecutionParametersForSpecificGoal() = runBlocking {
    createProjectPom("""
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

    val variants = getCompletionVariants(projectPom)
    assertTrue(variants.toString(), variants.contains("excludes"))
    assertFalse(variants.toString(), variants.contains("testExcludes"))
  }

  @Test
  fun testExecutionParametersForDefaultGoalExecution() = runBlocking {
    createProjectPom("""
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

    val variants = getCompletionVariants(projectPom)
    assertTrue(variants.toString(), variants.contains("excludes"))
    assertFalse(variants.toString(), variants.contains("testExcludes"))
  }

  @Test
  fun testExecutionParametersForSeveralSpecificGoals() = runBlocking {
    createProjectPom("""
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

    assertCompletionVariantsInclude(projectPom, "excludes", "testExcludes")
  }

  @Test
  fun testAliasCompletion() = runBlocking {
    createProjectPom("""
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

    assertCompletionVariantsInclude(projectPom, "warSourceExcludes", "excludes")
  }

  @Test
  fun testListElementsCompletion() = runBlocking {
    createProjectPom("""
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

    assertCompletionVariants(projectPom, "exclude")
  }

  @Test
  fun testListElementWhatHasUnpluralizedNameCompletion() = runBlocking {
    // NPE test - StringUtil.unpluralize returns null.

    createProjectPom("""
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

    assertCompletionVariants(projectPom, "additionalConfig", "config")
  }

  @Test
  fun testDoNotHighlightUnknownElementsUnderLists() = runBlocking {
    createProjectPom("""
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

    checkHighlighting()
  }

  @Test
  fun testArrayElementsCompletion() = runBlocking {
    createProjectPom("""
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

    assertCompletionVariants(projectPom, "resource", "webResource")
  }

  @Test
  fun testCompletionInCustomObjects() = runBlocking {
    if (ignore()) return@runBlocking

    createProjectPom("""
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

    assertCompletionVariants(projectPom)
  }

  @Test
  fun testDocumentationForParameter() = runBlocking {
    createProjectPom("""
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

    if (mavenVersionIsOrMoreThan("3.9.3")) {
      assertDocumentation("""
          Type: <b>java.lang.String</b><br>Default Value: <b>1.8</b><br>Expression: <b>${'$'}{maven.compiler.source}</b><br><br><i>The -source argument for the Java compiler.

          NOTE: 

          Since 3.8.0 the default value has changed from 1.5 to 1.6

          Since 3.9.0 the default value has changed from 1.6 to 1.7

          Since 3.11.0 the default value has changed from 1.7 to 1.8</i>
          """.trimIndent())
    }
    else if (mavenVersionIsOrMoreThan("3.9.0")) {
      assertDocumentation("""
          Type: <b>java.lang.String</b><br>Default Value: <b>1.7</b><br>Expression: <b>${'$'}{maven.compiler.source}</b><br><br><i><p>The -source argument for the Java compiler.</p>

          <b>NOTE: </b>Since 3.8.0 the default value has changed from 1.5 to 1.6.
          Since 3.9.0 the default value has changed from 1.6 to 1.7</i>
          """.trimIndent())
    }
    else {
      assertDocumentation(
        "Type: <b>java.lang.String</b><br>Default Value: <b>1.5</b><br>Expression: <b>\${maven.compiler.source}</b><br><br><i>The -source argument for the Java compiler.</i>")
    }
  }

  @Test
  fun testDoNotCompleteNorHighlightNonPluginConfiguration() = runBlocking {
    createProjectPom("""
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

    checkHighlighting()
  }

  @Test
  fun testDoNotHighlightInnerParameters() = runBlocking {
    createProjectPom("""
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

    checkHighlighting()
  }

  @Test
  fun testDoNotHighlightRequiredParametersWithDefaultValues() = runBlocking {
    createProjectPom("""
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

    checkHighlighting() // surefire plugin has several required parameters with default values.
  }

  @Test
  fun testDoNotHighlightInnerParameterAttributes() = runBlocking {
    createProjectPom("""
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

    checkHighlighting()
  }

  @Test
  fun testDoNotCompleteParameterAttributes() = runBlocking {
    createProjectPom("""
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

    assertCompletionVariants(projectPom, "combine.children", "combine.self")
  }

  @Test
  fun testWorksWithPropertiesInPluginId() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <properties>
                         <plugin.groupId>org.apache.maven.plugins</plugin.groupId>
                         <plugin.artifactId>maven-compiler-plugin</plugin.artifactId>
                         <plugin.version>2.0.2</plugin.version>
                       </properties>
                       """.trimIndent())
    importProjectAsync() // let us recognize the properties first

    createProjectPom("""
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

    checkHighlighting()
  }

  @Test
  fun testDoNotHighlightPropertiesForUnknownPlugins() = runBlocking {
    createProjectPom("""
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

    checkHighlighting()
  }

  @Test
  fun testTellNobodyThatIdeaIsRulezzz() = runBlocking {
    createProjectPom("""
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

    assertCompletionVariants(projectPom)
  }
}
