// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project.importing

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.openapi.components.service
import com.intellij.openapi.util.text.StringUtil
import com.intellij.pom.java.LanguageLevel
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.model.MavenArtifactNode
import org.jetbrains.idea.maven.model.MavenPlugin
import org.jetbrains.idea.maven.project.MavenEmbedderWrappersManager
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenJDOMUtil
import org.jetbrains.idea.maven.utils.MavenUtil
import org.junit.Test
import java.io.File

class MavenProjectTest : MavenMultiVersionImportingTestCase() {
  @Test
  fun testCollectingPlugins() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>group1</groupId>
                          <artifactId>id1</artifactId>
                          <version>1</version>
                        </plugin>
                        <plugin>
                          <groupId>group1</groupId>
                          <artifactId>id2</artifactId>
                        </plugin>
                        <plugin>
                          <groupId>group2</groupId>
                          <artifactId>id1</artifactId>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())

    assertModules("project")

    assertDeclaredPlugins(p("group1", "id1"), p("group1", "id2"), p("group2", "id1"))
  }

  @Test
  fun testPluginsContainDefaultPlugins() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>group1</groupId>
                          <artifactId>id1</artifactId>
                          <version>1</version>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())

    assertModules("project")

    assertContain(p(
      mavenProject.plugins), p("group1", "id1"), p("org.apache.maven.plugins", "maven-compiler-plugin"))
  }

  @Test
  fun testDefaultPluginsAsDeclared() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.apache.maven.plugins</groupId>
                          <artifactId>maven-compiler-plugin</artifactId>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())

    assertModules("project")

    assertDeclaredPlugins(p("org.apache.maven.plugins", "maven-compiler-plugin"))
  }

  @Test
  fun testDoNotDuplicatePluginsFromBuildAndManagement() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.apache.maven.plugins</groupId>
                          <artifactId>maven-compiler-plugin</artifactId>
                        </plugin>
                      </plugins>
                      <pluginManagement>
                        <plugins>
                          <plugin>
                            <groupId>org.apache.maven.plugins</groupId>
                            <artifactId>maven-compiler-plugin</artifactId>
                          </plugin>
                        </plugins>
                      </pluginManagement>
                    </build>
                    """.trimIndent())

    assertModules("project")

    assertDeclaredPlugins(p("org.apache.maven.plugins", "maven-compiler-plugin"))
  }

  @Test
  fun testCollectingPluginsFromProfilesAlso() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>group</groupId>
                          <artifactId>id</artifactId>
                          <version>1</version>
                        </plugin>
                      </plugins>
                    </build>
                    <profiles>
                      <profile>
                        <id>profile1</id>
                        <build>
                          <plugins>
                            <plugin>
                              <groupId>group1</groupId>
                              <artifactId>id1</artifactId>
                            </plugin>
                          </plugins>
                        </build>
                      </profile>
                      <profile>
                        <id>profile2</id>
                        <build>
                          <plugins>
                            <plugin>
                              <groupId>group2</groupId>
                              <artifactId>id2</artifactId>
                            </plugin>
                          </plugins>
                        </build>
                      </profile>
                    </profiles>
                    """.trimIndent())

    assertModules("project")

    assertDeclaredPlugins(p("group", "id"))

    importProjectWithProfiles("profile1")
    assertDeclaredPlugins(p("group", "id"), p("group1", "id1"))

    importProjectWithProfiles("profile2")
    assertDeclaredPlugins(p("group", "id"), p("group2", "id2"))

    importProjectWithProfiles("profile1", "profile2")
    assertDeclaredPlugins(p("group", "id"), p("group1", "id1"), p("group2", "id2"))
  }

  @Test
  fun testFindingPlugin() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>group</groupId>
                          <artifactId>id</artifactId>
                          <version>1</version>
                        </plugin>
                      </plugins>
                    </build>
                    <profiles>
                      <profile>
                        <id>profile1</id>
                        <build>
                          <plugins>
                            <plugin>
                              <groupId>group1</groupId>
                              <artifactId>id1</artifactId>
                            </plugin>
                          </plugins>
                        </build>
                      </profile>
                      <profile>
                        <id>profile2</id>
                        <build>
                          <plugins>
                            <plugin>
                              <groupId>group2</groupId>
                              <artifactId>id2</artifactId>
                            </plugin>
                          </plugins>
                        </build>
                      </profile>
                    </profiles>
                    """.trimIndent())

    assertModules("project")

    assertEquals(p("group", "id"), p(findPlugin("group", "id")))
    assertNull(findPlugin("group1", "id1"))

    importProjectWithProfiles("profile1")
    assertEquals(p("group1", "id1"), p(findPlugin("group1", "id1")))
    assertNull(findPlugin("group2", "id2"))
  }

  @Test
  fun testFindingDefaultPlugin() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>group</groupId>
                          <artifactId>id</artifactId>
                          <version>1</version>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())

    assertModules("project")

    assertNotNull(findPlugin("group", "id"))
    assertNotNull(findPlugin("org.apache.maven.plugins", "maven-compiler-plugin"))
  }

  @Test
  fun testFindingMavenGroupPluginWithDefaultPluginGroup() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <artifactId>some.plugin.id</artifactId>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())
    assertModules("project")

    assertEquals(p("org.apache.maven.plugins", "some.plugin.id"),
                 p(findPlugin("org.apache.maven.plugins", "some.plugin.id")))
    assertNull(findPlugin("some.other.group.id", "some.plugin.id"))
  }

  @Test
  fun testPluginConfiguration() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>group</groupId>
                          <artifactId>id1</artifactId>
                          <version>1</version>
                        </plugin>
                        <plugin>
                          <groupId>group</groupId>
                          <artifactId>id2</artifactId>
                          <version>1</version>
                          <configuration>
                          </configuration>
                        </plugin>
                        <plugin>
                          <groupId>group</groupId>
                          <artifactId>id3</artifactId>
                          <version>1</version>
                          <configuration>
                            <one>
                              <two>foo</two>
                            </one>
                          </configuration>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())

    assertNull(findPluginConfig("group", "id1", "one.two"))
    assertNull(findPluginConfig("group", "id2", "one.two"))
    assertEquals("foo", findPluginConfig("group", "id3", "one.two"))
    assertNull(findPluginConfig("group", "id3", "one.two.three"))
  }

  @Test
  fun testPluginGoalConfiguration() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>group</groupId>
                          <artifactId>id</artifactId>
                          <version>1</version>
                          <executions>
                            <execution>
                              <id>a</id>
                              <goals>
                                <goal>compile</goal>
                              </goals>
                              <configuration>
                                <one>
                                  <two>a</two>
                                </one>
                              </configuration>
                            </execution>
                            <execution>
                              <id>b</id>
                              <goals>
                                <goal>testCompile</goal>
                              </goals>
                              <configuration>
                                <one>
                                  <two>b</two>
                                </one>
                              </configuration>
                            </execution>
                          </executions>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())

    assertNull(findPluginGoalConfig("group", "id", "package", "one.two"))
    assertEquals("a", findPluginGoalConfig("group", "id", "compile", "one.two"))
    assertEquals("b", findPluginGoalConfig("group", "id", "testCompile", "one.two"))
  }

  @Test
  fun testPluginConfigurationHasResolvedVariables() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <properties>
                      <some.path>somePath</some.path>
                    </properties>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>group</groupId>
                          <artifactId>id</artifactId>
                          <version>1</version>
                          <configuration>
                            <one>${'$'}{some.path}</one>
                          </configuration>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())

    assertEquals("somePath", findPluginConfig("group", "id", "one"))
  }

  @Test
  fun testPluginConfigurationWithStandardVariable() = runBlocking {
    importProjectAsync($$"""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>group</groupId>
                          <artifactId>id</artifactId>
                          <version>1</version>
                          <configuration>
                            <one>${project.build.directory}</one>
                          </configuration>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())

    assertPathsAreEqual("$projectPath/target",
                        findPluginConfig("group", "id", "one")!!)
  }

  @Test
  fun testPluginConfigurationWithColons() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>group</groupId>
                          <artifactId>id</artifactId>
                          <version>1</version>
                          <configuration>
                            <two:three>xxx</two:three>
                          </configuration>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())

    assertNull(findPluginConfig("group", "id", "two:three"))
  }

  @Test
  fun testMergingPluginConfigurationFromBuildAndProfiles() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <profiles>
                         <profile>
                           <id>one</id>
                           <build>
                             <plugins>
                               <plugin>
                                 <groupId>org.apache.maven.plugins</groupId>
                                 <artifactId>maven-compiler-plugin</artifactId>
                                 <configuration>
                                   <target>1.4</target>
                                 </configuration>
                               </plugin>
                             </plugins>
                           </build>
                         </profile>
                         <profile>
                           <id>two</id>
                           <build>
                             <plugins>
                               <plugin>
                                 <groupId>org.apache.maven.plugins</groupId>
                                 <artifactId>maven-compiler-plugin</artifactId>
                                 <configuration>
                                   <source>1.4</source>
                                 </configuration>
                               </plugin>
                             </plugins>
                           </build>
                         </profile>
                       </profiles>
                       <build>
                         <plugins>
                           <plugin>
                             <groupId>org.apache.maven.plugins</groupId>
                             <artifactId>maven-compiler-plugin</artifactId>
                             <configuration>
                               <debug>true</debug>
                             </configuration>
                           </plugin>
                         </plugins>
                       </build>
                       """.trimIndent())
    importProjectWithProfiles("one", "two")

    val plugin = findPlugin("org.apache.maven.plugins", "maven-compiler-plugin")
    assertEquals("1.4", plugin!!.configurationElement!!.getChildText("source"))
    assertEquals("1.4", plugin.configurationElement!!.getChildText("target"))
    assertEquals("true", plugin.configurationElement!!.getChildText("debug"))
  }

  @Test
  fun testCompilerPluginConfigurationFromProperties() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId><artifactId>project</artifactId><version>1</version><properties>
                               <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                               <maven.compiler.source>1.7</maven.compiler.source>
                               <maven.compiler.target>1.7</maven.compiler.target>
                       </properties>
                       """.trimIndent())

    importProjectAsync()

    assertEquals(LanguageLevel.JDK_1_7, getSourceLanguageLevel())
    assertEquals(LanguageLevel.JDK_1_7, getTargetLanguageLevel())
  }

  @Test
  fun testCompilerPluginConfigurationFromPropertiesOverride() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <properties>
                         <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                         <maven.compiler.source>1.7</maven.compiler.source>
                         <maven.compiler.target>1.7</maven.compiler.target>
                       </properties>
                       <build>
                         <plugins>
                           <plugin>      
                             <groupId>org.apache.maven.plugins</groupId>
                             <artifactId>maven-compiler-plugin</artifactId>
                             <configuration>
                               <target>1.4</target> 
                               <source>1.4</source>
                             </configuration>
                           </plugin>
                         </plugins>
                       </build>
                       """.trimIndent())

    importProjectAsync()

    assertEquals(LanguageLevel.JDK_1_4, getSourceLanguageLevel())
    assertEquals(LanguageLevel.JDK_1_4, getTargetLanguageLevel())
  }

  @Test
  fun testCompilerPluginConfigurationRelease() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <groupId>org.apache.maven.plugins</groupId>
                             <artifactId>maven-compiler-plugin</artifactId>
                             <version>3.6.0</version>
                             <configuration>
                               <release>7</release>
                             </configuration>
                           </plugin>
                         </plugins>
                       </build>
                       """.trimIndent())

    importProjectAsync()

    assertEquals(LanguageLevel.JDK_1_7, getSourceLanguageLevel())
    assertEquals(LanguageLevel.JDK_1_7, getTargetLanguageLevel())
  }

  @Test
  fun testMergingPluginConfigurationFromBuildProfilesAndPluginsManagement() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <profiles>
                         <profile>
                           <id>one</id>
                           <build>
                             <plugins>
                               <plugin>
                                 <groupId>org.apache.maven.plugins</groupId>
                                 <artifactId>maven-compiler-plugin</artifactId>
                                 <configuration>
                                   <target>1.4</target>
                                 </configuration>
                               </plugin>
                             </plugins>
                           </build>
                         </profile>
                       </profiles>
                       <build>
                         <plugins>
                           <plugin>
                             <groupId>org.apache.maven.plugins</groupId>
                             <artifactId>maven-compiler-plugin</artifactId>
                             <configuration>
                               <debug>true</debug>
                             </configuration>
                           </plugin>
                         </plugins>
                         <pluginManagement>
                           <plugins>
                             <plugin>
                               <groupId>org.apache.maven.plugins</groupId>
                               <artifactId>maven-compiler-plugin</artifactId>
                               <configuration>
                                 <source>1.4</source>
                               </configuration>
                             </plugin>
                           </plugins>
                         </pluginManagement>
                       </build>
                       """.trimIndent())
    importProjectWithProfiles("one")

    val plugin = findPlugin("org.apache.maven.plugins", "maven-compiler-plugin")
    assertEquals("1.4", plugin!!.configurationElement!!.getChildText("source"))
    assertEquals("1.4", plugin.configurationElement!!.getChildText("target"))
    assertEquals("true", plugin.configurationElement!!.getChildText("debug"))
  }

  @Test
  fun testDoesNotCollectProfilesWithoutId() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <profiles>
                      <profile>
                        <id>one</id>
                      </profile>
                      <profile>
                      </profile>
                    </profiles>
                    """.trimIndent())

    assertUnorderedElementsAreEqual(mavenProject.profilesIds, "one", "default")
  }

  @Test
  fun testResolveRemoteRepositories() = runBlocking {
    updateSettingsXml("""
                        <mirrors>
                          <mirror>
                            <id>mirror</id>
                            <url>https://test/mirror</url>
                            <mirrorOf>repo,repo-pom</mirrorOf>
                          </mirror>
                        </mirrors>
                        <profiles>
                          <profile>
                            <id>repo-test</id>
                            <repositories>
                              <repository>        
                                <id>repo</id>        
                                <url>https://settings/repo</url>      
                              </repository>      
                              <repository>        
                                <id>repo1</id>        
                                <url>https://settings/repo1</url>      
                              </repository>    
                            </repositories>
                          </profile>
                        </profiles>
                        <activeProfiles>
                           <activeProfile>repo-test</activeProfile>
                        </activeProfiles>
                        """.trimIndent())

    val projectPom = createProjectPom("""
                                      <groupId>test</groupId>
                                      <artifactId>test</artifactId>
                                      <version>1</version>
                                      <repositories>
                                        <repository>
                                          <id>repo-pom</id>
                                          <url>https://pom/repo</url>
                                        </repository>
                                        <repository>
                                          <id>repo-pom1</id>
                                          <url>https://pom/repo1</url>
                                        </repository>
                                        <repository>
                                          <id>repo-http</id>
                                          <url>http://pom/http</url>
                                        </repository>
                                      </repositories>
                                      """.trimIndent())
    //Registry.get("maven.server.debug").setValue(true);
    importProjectAsync()

    val repositories = projectsManager.getRemoteRepositories()
    val mavenEmbedderWrappers = project.service<MavenEmbedderWrappersManager>().createMavenEmbedderWrappers()
    val repos = mavenEmbedderWrappers.use {
      val mavenEmbedderWrapper = mavenEmbedderWrappers.getEmbedder(MavenUtil.getBaseDir(projectPom).toString())
      mavenEmbedderWrapper.resolveRepositories(repositories).toSet()
    }

    val repoIds = repos.map { it.id }

    val project = MavenProjectsManager.getInstance(project).findProject(projectPom)
    assertNotNull(project)

    assertTrue(repoIds.contains("mirror"))
    assertTrue(repoIds.contains("repo-pom1"))
    assertTrue(repoIds.contains("repo1"))
    assertTrue(repoIds.contains("central"))

    //    assertTrue(repoIds.contains("maven-default-http-blocker"));
    assertFalse(repoIds.contains("repo-pom"))
    assertFalse(repoIds.contains("repo"))
    if (mavenVersionIsOrMoreThan("3.8.1")) {
      assertFalse(repoIds.contains("repo-http"))
    }
  }

  @Test
  fun testMavenModelMap() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <finalName>foo</finalName>
                      <plugins>
                        <plugin>
                          <groupId>group1</groupId>
                          <artifactId>id1</artifactId>
                          <version>1</version>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())

    val p = mavenProject
    val map = p.modelMap

    assertEquals("test", map["groupId"])
    assertEquals("foo", map["build.finalName"])
    assertEquals(File(p.directory, "target").toString(), map["build.directory"])
    assertNull(map["build.plugins"])
    assertNull(map["build.pluginMap"])
  }

  @Test
  fun testDependenciesTree() = runBlocking {
    val m1 = createModulePom("p1",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m1</artifactId>
                                       <version>1</version>
                                       <dependencies>
                                         <dependency>
                                           <groupId>test</groupId>
                                           <artifactId>m2</artifactId>
                                           <version>1</version>
                                         </dependency>
                                         <dependency>
                                           <groupId>test</groupId>
                                           <artifactId>lib1</artifactId>
                                           <version>1</version>
                                         </dependency>
                                       </dependencies>
                                       """.trimIndent())

    val m2 = createModulePom("p2",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m2</artifactId>
                                       <version>1</version>
                                       <dependencies>
                                         <dependency>
                                           <groupId>junit</groupId>
                                           <artifactId>junit</artifactId>
                                           <version>4.0</version>
                                         </dependency>
                                         <dependency>
                                           <groupId>test</groupId>
                                           <artifactId>lib2</artifactId>
                                           <version>1</version>
                                         </dependency>
                                       </dependencies>
                                       """.trimIndent())

    importProjects(m1, m2)
    assertDependenciesNodes(projectsTree.rootProjects[0].dependencyTree,
                            "test:m2:jar:1->(junit:junit:jar:4.0->(),test:lib2:jar:1->()),test:lib1:jar:1->()")
  }

  @Test
  fun testDependenciesTreeWithTypesAndClassifiers() = runBlocking {
    val m1 = createModulePom("p1",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m1</artifactId>
                                       <version>1</version>
                                       <dependencies>
                                         <dependency>
                                           <groupId>test</groupId>
                                           <artifactId>m2</artifactId>
                                           <version>1</version>
                                           <type>pom</type>
                                           <classifier>test</classifier>
                                         </dependency>
                                       </dependencies>
                                       """.trimIndent())

    val m2 = createModulePom("p2",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m2</artifactId>
                                       <version>1</version>
                                       <dependencies>
                                         <dependency>
                                           <groupId>test</groupId>
                                           <artifactId>lib</artifactId>
                                           <version>1</version>
                                         </dependency>
                                       </dependencies>
                                       """.trimIndent())

    importProjects(m1, m2)

    assertDependenciesNodes(projectsTree.rootProjects[0].dependencyTree,
                            "test:m2:pom:test:1->(test:lib:jar:1->())")
  }

  @Test
  fun testDependenciesTreeWithConflict() = runBlocking {
    val m1 = createModulePom("p1",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m1</artifactId>
                                       <version>1</version>
                                       <dependencies>
                                         <dependency>
                                           <groupId>test</groupId>
                                           <artifactId>m2</artifactId>
                                           <version>1</version>
                                         </dependency>
                                         <dependency>
                                           <groupId>test</groupId>
                                           <artifactId>lib</artifactId>
                                           <version>1</version>
                                         </dependency>
                                       </dependencies>
                                       """.trimIndent())

    val m2 = createModulePom("p2",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m2</artifactId>
                                       <version>1</version>
                                       <dependencies>
                                         <dependency>
                                           <groupId>test</groupId>
                                           <artifactId>lib</artifactId>
                                           <version>2</version>
                                         </dependency>
                                       </dependencies>
                                       """.trimIndent())

    importProjects(m1, m2)
    val nodes = projectsTree.rootProjects[0].dependencyTree
    assertDependenciesNodes(nodes,
                            "test:m2:jar:1->(test:lib:jar:2[CONFLICT:test:lib:jar:1]->())," +
                            "test:lib:jar:1->()")
    assertSame(nodes[0]!!.dependencies[0].relatedArtifact,
               nodes[1]!!.artifact)
  }

  @Test
  fun testDependencyTreeDuplicates() = runBlocking {
    val m1 = createModulePom("p1",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m1</artifactId>
                                       <version>1</version>
                                       <dependencies>
                                         <dependency>
                                           <groupId>test</groupId>
                                           <artifactId>m2</artifactId>
                                           <version>1</version>
                                         </dependency>
                                         <dependency>
                                           <groupId>test</groupId>
                                           <artifactId>m3</artifactId>
                                           <version>1</version>
                                         </dependency>
                                       </dependencies>
                                       """.trimIndent())

    val m2 = createModulePom("p2",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m2</artifactId>
                                       <version>1</version>
                                       <dependencies>
                                         <dependency>
                                           <groupId>test</groupId>
                                           <artifactId>lib</artifactId>
                                           <version>1</version>
                                         </dependency>
                                       </dependencies>
                                       """.trimIndent())

    val m3 = createModulePom("p3",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m3</artifactId>
                                       <version>1</version>
                                       <dependencies>
                                         <dependency>
                                           <groupId>test</groupId>
                                           <artifactId>lib</artifactId>
                                           <version>1</version>
                                         </dependency>
                                       </dependencies>
                                       """.trimIndent())

    importProjects(m1, m2, m3)
    val nodes = projectsTree.findProject(m1)!!.dependencyTree
    assertDependenciesNodes(nodes, "test:m2:jar:1->(test:lib:jar:1->()),test:m3:jar:1->(test:lib:jar:1[DUPLICATE:test:lib:jar:1]->())")

    assertSame(nodes[0]!!.dependencies[0].artifact,
               nodes[1]!!.dependencies[0].relatedArtifact)
  }

  @Test
  fun testManagedDependencies()  = runBlocking{
    val p = createProjectPom("""
      <groupId>test</groupId>
      <artifactId>test</artifactId>
      <version>1</version>
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

    importProjectAsync()

    assertEquals("4.0", projectsTree.findProject(p)!!.findManagedDependencyVersion("junit", "junit")!!)
  }

  @Test
  fun testManagedDependenciesFromParent()  = runBlocking{
    val p = createProjectPom("""
      <groupId>test</groupId>
      <artifactId>test</artifactId>
      <version>1</version>
      <packaging>pom</packaging>
      <modules>
        <module>m1</module>
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

    val m1 = createModulePom("m1",
                             """
      <parent>
        <groupId>test</groupId>
        <artifactId>test</artifactId>
        <version>1</version>
      </parent>                         
      <artifactId>m1</artifactId>
""")

    importProjectAsync()

    assertEquals("4.0", projectsTree.findProject(m1)!!.findManagedDependencyVersion("junit", "junit")!!)
  }

  @Test
  fun testManagedDependenciesFromParentAndModule()  = runBlocking{
    val p = createProjectPom("""
      <groupId>test</groupId>
      <artifactId>test</artifactId>
      <version>1</version>
      <packaging>pom</packaging>
      <modules>
        <module>m1</module>
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

    val m1 = createModulePom("m1",
                             """
      <parent>
        <groupId>test</groupId>
        <artifactId>test</artifactId>
        <version>1</version>
      </parent>                         
      <artifactId>m1</artifactId>
      <dependencyManagement>
        <dependencies>
          <dependency>
            <groupId>another</groupId>
            <artifactId>dep</artifactId>
            <version>1.0</version>
          </dependency>
        </dependencies>
      </dependencyManagement>
""")

    importProjectAsync()

    assertEquals("4.0", projectsTree.findProject(m1)!!.findManagedDependencyVersion("junit", "junit")!!)
    assertEquals("1.0", projectsTree.findProject(m1)!!.findManagedDependencyVersion("another", "dep")!!)
  }

  protected fun assertDependenciesNodes(nodes: List<MavenArtifactNode?>?, expected: String?) {
    assertEquals(expected, StringUtil.join(nodes!!, ","))
  }

  private fun findPluginConfig(groupId: String, artifactId: String, path: String): String? {
    return MavenJDOMUtil.findChildValueByPath(
      mavenProject.getPluginConfiguration(groupId, artifactId), path)
  }

  private fun findPluginGoalConfig(groupId: String, artifactId: String, goal: String, path: String): String? {
    return MavenJDOMUtil.findChildValueByPath(
      mavenProject.getPluginGoalConfiguration(groupId, artifactId, goal), path)
  }

  private fun assertDeclaredPlugins(vararg expected: PluginInfo) {
    val defaultPlugins = listOf(
      p("org.apache.maven.plugins", "maven-site-plugin"),
      p("org.apache.maven.plugins", "maven-deploy-plugin"),
      p("org.apache.maven.plugins", "maven-compiler-plugin"),
      p("org.apache.maven.plugins", "maven-install-plugin"),
      p("org.apache.maven.plugins", "maven-jar-plugin"),
      p("org.apache.maven.plugins", "maven-clean-plugin"),
      p("org.apache.maven.plugins", "maven-resources-plugin"),
      p("org.apache.maven.plugins", "maven-surefire-plugin"))
    val expectedList: MutableList<PluginInfo> = ArrayList()
    expectedList.addAll(defaultPlugins)
    expectedList.addAll(listOf(*expected))
    val actualList = p(mavenProject.declaredPlugins)
    assertUnorderedElementsAreEqual(actualList.sortedBy { it.toString() }, expectedList.sortedBy { it.toString() })
  }

  private fun findPlugin(groupId: String, artifactId: String): MavenPlugin? {
    return mavenProject.findPlugin(groupId, artifactId)
  }

  private val mavenProject: MavenProject
    get() = projectsTree.rootProjects[0]

  private fun p(mavenPlugins: Collection<MavenPlugin>): List<PluginInfo> {
    val res: MutableList<PluginInfo> = ArrayList(mavenPlugins.size)
    for (mavenPlugin in mavenPlugins) {
      res.add(p(mavenPlugin))
    }

    return res
  }

  private class PluginInfo(groupId: String, artifactId: String) {
    var groupId: String? = groupId
    var artifactId: String? = artifactId

    override fun toString(): String {
      return "$groupId:$artifactId"
    }

    override fun equals(o: Any?): Boolean {
      if (this === o) return true
      if (o == null || javaClass != o.javaClass) return false

      val info = o as PluginInfo

      if (if (artifactId != null) artifactId != info.artifactId else info.artifactId != null) return false
      if (if (groupId != null) groupId != info.groupId else info.groupId != null) return false

      return true
    }

    override fun hashCode(): Int {
      var result = if (groupId != null) groupId.hashCode() else 0
      result = 31 * result + if (artifactId != null) artifactId.hashCode() else 0
      return result
    }
  }

  companion object {
    private fun p(groupId: String, artifactId: String): PluginInfo {
      return PluginInfo(groupId, artifactId)
    }

    private fun p(mavenPlugin: MavenPlugin?): PluginInfo {
      return PluginInfo(mavenPlugin!!.groupId, mavenPlugin.artifactId)
    }
  }
}
