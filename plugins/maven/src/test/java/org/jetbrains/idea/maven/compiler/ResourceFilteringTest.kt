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
package org.jetbrains.idea.maven.compiler

import com.intellij.maven.testFramework.MavenCompilingTestCase
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.module.ModuleManager.Companion.getInstance
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiDocumentManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.model.MavenExplicitProfiles
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.jps.maven.model.impl.MavenIdBean
import org.jetbrains.jps.maven.model.impl.MavenModuleResourceConfiguration
import org.junit.Test
import java.io.IOException

class ResourceFilteringTest : MavenCompilingTestCase() {
  @Test
  fun testBasic() = runBlocking {
    createProjectSubFile("resources/file.properties", """
      value=${'$'}{project.version}
      value2=@project.version@
      time=${'$'}{time}
      """.trimIndent())

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    
                    <version>1</version>
                    
                    <properties>
                      <time>${'$'}{maven.build.timestamp}</time>
                      <maven.build.timestamp.format>---</maven.build.timestamp.format>
                    </properties>
                    <build>
                      <resources>
                        <resource>      
                          <directory>resources</directory>      
                          <filtering>true</filtering>    
                        </resource>  
                      </resources>
                    </build>
                    """.trimIndent())

    compileModules("project")

    assertResult("target/classes/file.properties", """
      value=1
      value2=1
      time=---
      """.trimIndent())
  }

  @Test
  fun testResolveSettingProperty() = runBlocking {
    createProjectSubFile("resources/file.properties", "value=\${settings.localRepository}")

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <resources>
                        <resource>
                          <directory>resources</directory>
                          <filtering>true</filtering>
                        </resource>
                      </resources>
                    </build>
                    """.trimIndent())

    compileModules("project")

    assert(!loadResult(projectPom, "target/classes/file.properties").contains("settings.localRepository"))
  }

  @Test
  fun testCustomDelimiter() = runBlocking {
    createProjectSubFile("resources/file.properties", """
      value1=${'$'}{project.version}
      value2=@project.version@
      valueX=|
      value3=|project.version|
      """.trimIndent())

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <resources>
                        <resource>
                          <directory>resources</directory>
                          <filtering>true</filtering>
                        </resource>
                      </resources>
                      <plugins>
                        <plugin>
                          <groupId>org.apache.maven.plugins</groupId>
                          <artifactId>maven-resources-plugin</artifactId>
                          <configuration>
                            <delimiters>
                              <delimiter>|</delimiter>
                              <delimiter>(*]</delimiter>
                            </delimiters>
                          </configuration>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())

    compileModules("project")

    assertResult("target/classes/file.properties", """
      value1=1
      value2=1
      valueX=|
      value3=1
      """.trimIndent())
  }

  @Test
  fun testPomArtifactId() = runBlocking {
    createProjectSubFile("resources/file.properties", "value=\${pom.artifactId}")

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <resources>
                        <resource>
                          <directory>resources</directory>
                          <filtering>true</filtering>
                        </resource>
                      </resources>
                    </build>
                    """.trimIndent())
    compileModules("project")

    assertResult("target/classes/file.properties", "value=project")
  }

  @Test
  fun testPomVersionInModules() = runBlocking {
    createProjectSubFile("m1/resources/file.properties", "value=\${pom.version}")

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m1</module>
                       </modules>
                       """.trimIndent())

    createModulePom("m1",
                    """
                      <groupId>test</groupId>
                      <artifactId>m1</artifactId>
                      <version>2</version>
                      <build>
                        <resources>
                          <resource>
                            <directory>resources</directory>
                            <filtering>true</filtering>
                          </resource>
                        </resources>
                      </build>
                      """.trimIndent())
    importProjectAsync()

    compileModules("project", "m1")

    assertResult("m1/target/classes/file.properties", "value=2")
  }

  @Test
  fun testDoNotFilterSomeFileByDefault() = runBlocking {
    createProjectSubFile("resources/file.bmp", "value=\${project.version}")

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <resources>
                        <resource>
                          <directory>resources</directory>
                          <filtering>true</filtering>
                        </resource>
                      </resources>
                    </build>
                    """.trimIndent())
    compileModules("project")

    assertResult("target/classes/file.bmp", "value=\${project.version}")
  }

  @Test
  fun testCustomNonFilteredExtensions() = runBlocking {
    createProjectSubFile("resources/file.bmp", "value=\${project.version}")
    createProjectSubFile("resources/file.xxx", "value=\${project.version}")

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <resources>
                        <resource>
                          <directory>resources</directory>
                          <filtering>true</filtering>
                        </resource>
                      </resources>
                      <plugins>
                        <plugin>
                          <groupId>org.apache.maven.plugins</groupId>
                          <artifactId>maven-resources-plugin</artifactId>
                          <configuration>
                            <nonFilteredFileExtensions>
                              <nonFilteredFileExtension>xxx</nonFilteredFileExtension>
                            </nonFilteredFileExtensions>
                          </configuration>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())
    compileModules("project")

    assertResult("target/classes/file.bmp", "value=\${project.version}")
    assertResult("target/classes/file.xxx", "value=\${project.version}")
  }

  @Test
  fun testFilteringTestResources() = runBlocking {
    createProjectSubFile("resources/file.properties", "value=@project.version@")

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <testResources>
                        <testResource>
                          <directory>resources</directory>
                          <filtering>true</filtering>
                        </testResource>
                      </testResources>
                    </build>
                    """.trimIndent())
    compileModules("project")

    assertResult("target/test-classes/file.properties", "value=1")
  }

  @Test
  fun testExcludesAndIncludes() = runBlocking {
    createProjectSubFile("src/main/resources/file1.properties", "value=\${project.artifactId}")
    createProjectSubFile("src/main/resources/file2.properties", "value=\${project.artifactId}")

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <resources>
                        <resource>
                          <directory>src/main/resources</directory>
                          <excludes>
                            <exclude>file1.properties</exclude>
                          </excludes>
                          <filtering>true</filtering>
                        </resource>
                        <resource>
                          <directory>src/main/resources</directory>
                          <includes>
                            <include>file1.properties</include>
                          </includes>
                          <filtering>false</filtering>
                        </resource>
                      </resources>
                    </build>
                    """.trimIndent())

    compileModules("project")
    assertResult("target/classes/file1.properties", "value=\${project.artifactId}")
    assertResult("target/classes/file2.properties", "value=project")

    compileModules()
    assertResult("target/classes/file1.properties", "value=\${project.artifactId}")
    assertResult("target/classes/file2.properties", "value=project")

    compileModules("project")
    assertResult("target/classes/file1.properties", "value=\${project.artifactId}")
    assertResult("target/classes/file2.properties", "value=project")
  }

  @Test
  fun testEscapingWindowsChars() = runBlocking {
    createProjectSubFile("resources/file.txt", """
      value=${'$'}{foo}
      value2=@foo@
      value3=${'$'}{bar}
      """.trimIndent())

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <properties>
                      <foo>c:\projects\foo/bar</foo>
                      <bar>a\b\c</bar>
                    </properties>
                    <build>
                      <resources>
                        <resource>
                          <directory>resources</directory>
                          <filtering>true</filtering>
                        </resource>
                      </resources>
                    </build>
                    """.trimIndent())
    compileModules("project")

    assertResult("target/classes/file.txt", """
      value=c:\\projects\\foo/bar
      value2=c:\\projects\\foo/bar
      value3=a\b\c
      """.trimIndent())
  }

  @Test
  fun testDontEscapingWindowsChars() = runBlocking {
    createProjectSubFile("resources/file.txt", "value=\${foo}")

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <properties>
                      <foo>c:\projects\foo/bar</foo>
                    </properties>
                    <build>
                      <resources>
                        <resource>
                          <directory>resources</directory>
                          <filtering>true</filtering>
                        </resource>
                      </resources>
                        <plugins>
                          <plugin>
                            <artifactId>maven-resources-plugin</artifactId>
                            <configuration>
                              <escapeWindowsPaths>false</escapeWindowsPaths>
                            </configuration>
                          </plugin>
                        </plugins>
                    </build>
                    """.trimIndent())
    compileModules("project")

    assertResult("target/classes/file.txt", "value=c:\\projects\\foo/bar")
  }

  @Test
  fun testFilteringPropertiesWithEmptyValues() = runBlocking {
    createProjectSubFile("resources/file.properties", "value1=\${foo}\nvalue2=\${bar}")

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <properties>
                      <foo/>
                    </properties>
                    <build>
                      <resources>
                        <resource>
                          <directory>resources</directory>
                          <filtering>true</filtering>
                        </resource>
                      </resources>
                    </build>
                    """.trimIndent())
    compileModules("project")

    assertResult("target/classes/file.properties", "value1=\nvalue2=\${bar}")
  }

  @Test
  fun testFilterWithSeveralResourceFolders() = runBlocking {
    createProjectSubFile("resources1/file1.properties", "value=\${project.version}")
    createProjectSubFile("resources2/file2.properties", "value=\${project.version}")

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <resources>
                        <resource>
                          <directory>resources1</directory>
                          <filtering>true</filtering>
                        </resource>
                        <resource>
                          <directory>resources2</directory>
                          <filtering>true</filtering>
                        </resource>
                      </resources>
                    </build>
                    """.trimIndent())
    compileModules("project")

    assertResult("target/classes/file1.properties", "value=1")
    assertResult("target/classes/file2.properties", "value=1")
  }

  @Test
  fun testFilterWithSeveralModules() = runBlocking {
    createProjectSubFile("module1/resources/file1.properties", "value=\${project.version}")
    createProjectSubFile("module2/resources/file2.properties", "value=\${project.version}")

    val m1 = createModulePom("module1",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>module1</artifactId>
                                       <version>1</version>
                                       <build>
                                         <resources>
                                           <resource>
                                             <directory>resources</directory>
                                             <filtering>true</filtering>
                                           </resource>
                                         </resources>
                                       </build>
                                       """.trimIndent())

    val m2 = createModulePom("module2",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>module2</artifactId>
                                       <version>2</version>
                                       <build>
                                         <resources>
                                           <resource>
                                             <directory>resources</directory>
                                             <filtering>true</filtering>
                                           </resource>
                                         </resources>
                                       </build>
                                       """.trimIndent())

    importProjects(m1, m2)
    compileModules("module1", "module2")

    assertResult(m1, "target/classes/file1.properties", "value=1")
    assertResult(m2, "target/classes/file2.properties", "value=2")
  }

  @Test
  fun testDoNotFilterIfNotRequested() = runBlocking {
    createProjectSubFile("resources1/file1.properties", "value=\${project.version}")
    createProjectSubFile("resources2/file2.properties", "value=\${project.version}")

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <resources>
                        <resource>
                          <directory>resources1</directory>
                          <filtering>true</filtering>
                        </resource>
                        <resource>
                          <directory>resources2</directory>
                          <filtering>false</filtering>
                        </resource>
                      </resources>
                    </build>
                    """.trimIndent())
    compileModules("project")

    assertResult("target/classes/file1.properties", "value=1")
    assertResult("target/classes/file2.properties", "value=\${project.version}")
  }

  @Test
  fun testDoNotChangeFileIfPropertyIsNotResolved() = runBlocking {
    createProjectSubFile("resources/file.properties", "value=\${foo.bar}")

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <resources>
                        <resource>
                          <directory>resources</directory>
                          <filtering>true</filtering>
                        </resource>
                      </resources>
                    </build>
                    """.trimIndent())
    compileModules("project")

    assertResult("target/classes/file.properties", "value=\${foo.bar}")
  }

  @Test
  fun testChangingResolvedPropsBackWhenSettingsIsChange() = runBlocking {
    createProjectSubFile("resources/file.properties", "value=\${project.version}")

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <resources>
                        <resource>
                          <directory>resources</directory>
                          <filtering>true</filtering>
                        </resource>
                      </resources>
                    </build>
                    """.trimIndent())
    compileModules("project")
    assertResult("target/classes/file.properties", "value=1")

    updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <resources>
                           <resource>
                             <directory>resources</directory>
                             <filtering>false</filtering>
                           </resource>
                         </resources>
                       </build>
                       """.trimIndent())
    updateAllProjects()
    compileModules("project")

    assertResult("target/classes/file.properties", "value=\${project.version}")
  }

  @Test
  fun testUpdatingWhenPropertiesInFiltersAreChanged() = runBlocking {
    val filter = createProjectSubFile("filters/filter.properties", "xxx=1")
    createProjectSubFile("resources/file.properties", "value=\${xxx}")

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <filters>
                        <filter>filters/filter.properties</filter>
                      </filters>
                      <resources>
                        <resource>
                          <directory>resources</directory>
                          <filtering>true</filtering>
                        </resource>
                      </resources>
                    </build>
                    """.trimIndent())
    compileModules("project")
    assertResult("target/classes/file.properties", "value=1")

    WriteAction.runAndWait<IOException> { VfsUtil.saveText(filter, "xxx=2") }
    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        PsiDocumentManager.getInstance(project).commitAllDocuments()
      }
    }
    compileModules("project")
    assertResult("target/classes/file.properties", "value=2")
  }

  @Test
  fun testUpdatingWhenPropertiesAreChanged() = runBlocking {
    createProjectSubFile("resources/file.properties", "value=\${foo}")

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <properties>
                      <foo>val1</foo>
                    </properties>
                    <build>
                      <resources>
                        <resource>
                          <directory>resources</directory>
                          <filtering>true</filtering>
                        </resource>
                      </resources>
                    </build>
                    """.trimIndent())
    compileModules("project")
    assertResult("target/classes/file.properties", "value=val1")

    updateProjectPom("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <properties>
                      <foo>val2</foo>
                    </properties>
                    <build>
                      <resources>
                        <resource>
                          <directory>resources</directory>
                          <filtering>true</filtering>
                        </resource>
                      </resources>
                    </build>
                    """.trimIndent())
    updateAllProjects()
    compileModules("project")
    assertResult("target/classes/file.properties", "value=val2")
  }


  private fun newMavenModuleResourceConfiguration(modelMap: Map<String, String>): MavenModuleResourceConfiguration {
    val config = MavenModuleResourceConfiguration()
    config.id = MavenIdBean()
    config.directory = ""
    config.delimitersPattern = ""
    config.modelMap = modelMap
    return config
  }

  @Test
  fun testUpdatingWhenPropertiesInModelAreChanged() = runBlocking {
    needFixForMaven4()
    createProjectSubFile("resources/file.properties", "value=\${project.name}")

    val moduleManager = getInstance(project)
    val mavenProjectsManager = MavenProjectsManager.getInstance(project)

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <name>val1</name>
                    <build>
                      <resources>
                        <resource>
                          <directory>resources</directory>
                          <filtering>true</filtering>
                        </resource>
                      </resources>
                    </build>
                    """.trimIndent())
    val modelMap1 = mavenProjectsManager.findProject(
      moduleManager.findModuleByName("project")!!)!!.modelMap
    val config1 = newMavenModuleResourceConfiguration(modelMap1)

    assertResources("project", "resources")
    assertEquals("val1", modelMap1["name"])

    compileModules("project")
    assertResult("target/classes/file.properties", "value=val1")

    updateProjectPom("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <name>val2</name>
                    <build>
                      <resources>
                        <resource>
                          <directory>resources</directory>
                          <filtering>true</filtering>
                        </resource>
                      </resources>
                    </build>
                    """.trimIndent())
    updateAllProjects()
    val modelMap2 = mavenProjectsManager.findProject(
      moduleManager.findModuleByName("project")!!)!!.modelMap
    val config2 = newMavenModuleResourceConfiguration(modelMap2)

    assertResources("project", "resources")
    assertEquals("val2", modelMap2["name"])
    assertFalse("Config hash didn't change. Module may not be recompiled properly",
                config1.computeModuleConfigurationHash() == config2.computeModuleConfigurationHash())

    compileModules("project")
    assertResult("target/classes/file.properties", "value=val2")
  }

  @Test
  fun testUpdatingWhenProfilesAreChanged() = runBlocking {
    createProjectSubFile("resources/file.properties", "value=\${foo}")

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <profiles>
                         <profile>
                           <id>one</id>
                           <properties>
                             <foo>val1</foo>
                           </properties>
                         </profile>
                         <profile>
                           <id>two</id>
                           <properties>
                             <foo>val2</foo>
                           </properties>
                         </profile>
                       </profiles>
                       <build>
                         <resources>
                           <resource>
                             <directory>resources</directory>
                             <filtering>true</filtering>
                           </resource>
                         </resources>
                       </build>
                       """.trimIndent())
    importProjectWithProfiles("one")
    compileModules("project")
    assertResult("target/classes/file.properties", "value=val1")

    projectsManager.explicitProfiles = MavenExplicitProfiles(mutableListOf("two"))
    updateAllProjects()

    compileModules("project")
    assertResult("target/classes/file.properties", "value=val2")
  }

  @Test
  fun testSameFileInSourcesAndTestSources() = runBlocking {
    createProjectSubFile("src/main/resources/file.properties", "foo=\${foo.main}")
    createProjectSubFile("src/test/resources/file.properties", "foo=\${foo.test}")

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <properties>
                      <foo.main>main</foo.main>
                      <foo.test>test</foo.test>
                    </properties>
                    <build>
                      <resources>
                        <resource>
                          <directory>src/main/resources</directory>
                          <filtering>true</filtering>
                        </resource>
                      </resources>
                      <testResources>
                        <testResource>
                          <directory>src/test/resources</directory>
                          <filtering>true</filtering>
                        </testResource>
                      </testResources>
                    </build>
                    """.trimIndent())
    compileModules("project")

    assertResult("target/classes/file.properties", "foo=main")
    assertResult("target/test-classes/file.properties", "foo=test")
  }

  @Test
  fun testCustomFilters() = runBlocking {
    createProjectSubFile("filters/filter1.properties",
                         """
                           xxx=value
                           yyy=${'$'}{project.version}
                           """.trimIndent())
    createProjectSubFile("filters/filter2.properties", "zzz=value2")
    createProjectSubFile("resources/file.properties",
                         """
                           value1=${'$'}{xxx}
                           value2=${'$'}{yyy}
                           value3=${'$'}{zzz}
                           """.trimIndent())

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <filters>
                        <filter>filters/filter1.properties</filter>
                        <filter>filters/filter2.properties</filter>
                      </filters>
                      <resources>
                        <resource>
                          <directory>resources</directory>
                          <filtering>true</filtering>
                        </resource>
                      </resources>
                    </build>
                    """.trimIndent())
    compileModules("project")

    assertResult("target/classes/file.properties", """
      value1=value
      value2=1
      value3=value2
      """.trimIndent())
  }

  @Test
  fun testCustomFiltersViaPlugin() = runBlocking {
    createProjectSubFile("filters/filter.properties", "xxx=value")
    createProjectSubFile("resources/file.properties", "value1=\${xxx}")

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.codehaus.mojo</groupId>
                          <artifactId>properties-maven-plugin</artifactId>
                          <executions>
                            <execution>
                              <id>common-properties</id>
                              <phase>initialize</phase>
                              <goals>
                                <goal>read-project-properties</goal>
                              </goals>
                              <configuration>
                                <files>
                                  <file>filters/filter.properties</file>
                                </files>
                              </configuration>
                            </execution>
                          </executions>
                        </plugin>
                      </plugins>
                      <resources>
                        <resource>      
                        <directory>resources</directory>      
                        <filtering>true</filtering>    
                        </resource>  
                      </resources>
                    </build>
                      """.trimIndent())
    compileModules("project")

    assertResult("target/classes/file.properties", "value1=value")
  }

  @Test
  fun testCustomFilterWithPropertyInThePath() = runBlocking {
    createProjectSubFile("filters/filter.properties", "xxx=value")
    createProjectSubFile("resources/file.properties", "value=\${xxx}")

    importProjectAsync("""
                      <groupId>test</groupId>
                      <artifactId>project</artifactId>
                      <version>1</version>
                      <properties>
                       <some.path>
                      $projectPath/filters</some.path>
                      </properties>
                      <build>
                        <filters>
                          <filter>${"$"}{some.path}/filter.properties</filter>
                        </filters>
                        <resources>
                          <resource>
                            <directory>resources</directory>
                            <filtering>true</filtering>
                          </resource>
                        </resources>
                      </build>
                      """.trimIndent())
    compileModules("project")

    assertResult("target/classes/file.properties", "value=value")
  }

  @Test
  fun testCustomFiltersFromProfiles() = runBlocking {
    createProjectSubFile("filters/filter1.properties", "xxx=value1")
    createProjectSubFile("filters/filter2.properties", "yyy=value2")
    createProjectSubFile("resources/file.properties",
                         """
                           value1=${'$'}{xxx}
                           value2=${'$'}{yyy}
                           """.trimIndent())

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <profiles>
                         <profile>
                           <id>one</id>
                           <build>
                             <filters>
                               <filter>filters/filter1.properties</filter>
                             </filters>
                           </build>
                         </profile>
                         <profile>
                           <id>two</id>
                           <build>
                             <filters>
                               <filter>filters/filter2.properties</filter>
                             </filters>
                           </build>
                         </profile>
                       </profiles>
                       <build>
                         <resources>
                           <resource>
                             <directory>resources</directory>
                             <filtering>true</filtering>
                           </resource>
                         </resources>
                       </build>
                       """.trimIndent())

    importProjectWithProfiles("one")
    compileModules("project")
    assertResult("target/classes/file.properties", """
      value1=value1
      value2=${'$'}{yyy}
      """.trimIndent())

    importProjectWithProfiles("two")
    compileModules("project")
    assertResult("target/classes/file.properties", """
      value1=${'$'}{xxx}
      value2=value2
      """.trimIndent())
  }

  @Test
  fun testEscapingFiltering() = runBlocking {
    createProjectSubFile("filters/filter.properties", "xxx=value")
    createProjectSubFile("resources/file.properties",
                         """
                           value1=\${'$'}{xxx}
                           value2=\\${'$'}{xxx}
                           value3=\\\${'$'}{xxx}
                           value3=\\\\${'$'}{xxx}
                           value4=.\.\\.\\\.
                           """.trimIndent())

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <filters>
                        <filter>filters/filter.properties</filter>
                      </filters>
                      <resources>
                        <resource>
                          <directory>resources</directory>
                          <filtering>true</filtering>
                        </resource>
                      </resources>
                      <plugins>
                        <plugin>
                          <groupId>org.apache.maven.plugins</groupId>
                          <artifactId>maven-resources-plugin</artifactId>
                          <configuration>
                            <escapeString>\</escapeString>
                          </configuration>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())

    compileModules("project")
    assertResult("target/classes/file.properties",
                 """
                   value1=${'$'}{xxx}
                   value2=\\value
                   value3=\\${'$'}{xxx}
                   value3=\\\\value
                   value4=.\.\\.\\\.
                   """.trimIndent())
  }

  @Test
  fun testPropertyPriority() = runBlocking {
    createProjectSubFile("filters/filter.properties", """
   xxx=fromFilterFile
   yyy=fromFilterFile
   """.trimIndent())
    createProjectSubFile("resources/file.properties", """
   value1=${"$"}{xxx}
   value2=${"$"}{yyy}
   """.trimIndent())

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <properties>
                      <xxx>fromProperties</xxx>
                    </properties>
                    <build>
                      <filters>
                        <filter>filters/filter.properties</filter>
                      </filters>
                      <resources>
                        <resource>
                          <directory>resources</directory>
                          <filtering>true</filtering>
                        </resource>
                      </resources>
                    </build>
                    """.trimIndent())

    compileModules("project")
    assertResult("target/classes/file.properties",
                 """
                       value1=fromProperties
                       value2=fromFilterFile
                       """.trimIndent())
  }

  @Test
  fun testCustomEscapingFiltering() = runBlocking {
    createProjectSubFile("filters/filter.properties", "xxx=value")
    createProjectSubFile("resources/file.properties",
                         """
                           value1=^${'$'}{xxx}
                           value2=\${'$'}{xxx}
                           """.trimIndent())

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <filters>
                        <filter>filters/filter.properties</filter>
                      </filters>
                      <resources>
                        <resource>
                          <directory>resources</directory>
                          <filtering>true</filtering>
                        </resource>
                      </resources>
                      <plugins>
                        <plugin>
                          <groupId>org.apache.maven.plugins</groupId>
                          <artifactId>maven-resources-plugin</artifactId>
                          <configuration>
                            <escapeString>^</escapeString>
                          </configuration>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())

    compileModules("project")
    assertResult("target/classes/file.properties",
                 """
                   value1=${'$'}{xxx}
                   value2=\value
                   """.trimIndent())
  }

  @Test
  fun testDoNotFilterButCopyBigFiles() = runBlocking {
    assertEquals(FileTypes.UNKNOWN, FileTypeManager.getInstance().getFileTypeByFileName("file.xyz"))

    WriteAction.runAndWait<IOException> { createProjectSubFile("resources/file.xyz").setBinaryContent(ByteArray(1024 * 1024 * 20)) }

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <resources>
                        <resource>
                          <directory>resources</directory>
                          <filtering>true</filtering>
                        </resource>
                      </resources>
                    </build>
                    """.trimIndent())
    compileModules("project")

    assertNotNull(projectPom.getParent().findFileByRelativePath("target/classes/file.xyz"))
  }

  @Test
  fun testResourcesOrdering1() = runBlocking {
    createProjectSubFile("resources/file.properties", "value=\${project.version}\n")

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <resources>
                        <resource>
                          <directory>resources</directory>
                          <filtering>false</filtering>
                        </resource>
                        <resource>
                          <directory>resources</directory>
                          <filtering>true</filtering>
                        </resource>
                      </resources>
                    </build>
                    """.trimIndent())

    compileModules("project")

    assertResult("target/classes/file.properties", "value=1\n") // Filtered file override non-filtered file
  }

  @Test
  fun testResourcesOrdering2() = runBlocking {
    createProjectSubFile("resources/file.properties", "value=\${project.version}\n")

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <resources>
                        <resource>
                          <directory>resources</directory>
                          <filtering>true</filtering>
                        </resource>
                        <resource>
                          <directory>resources</directory>
                          <filtering>false</filtering>
                        </resource>
                      </resources>
                    </build>
                    """.trimIndent())

    compileModules("project")

    assertResult("target/classes/file.properties", "value=1\n") // Filtered file override non-filtered file
  }

  @Test
  fun testResourcesOrdering3() = runBlocking {
    createProjectSubFile("resources1/a.txt", "1")
    createProjectSubFile("resources2/a.txt", "2")

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <resources>
                        <resource>
                          <directory>resources1</directory>
                        </resource>
                        <resource>
                          <directory>resources2</directory>
                        </resource>
                      </resources>
                    </build>
                    """.trimIndent())

    compileModules("project")

    assertResult("target/classes/a.txt", "1") // First file was copied, second file was not override first file
  }

  @Test
  fun testResourcesOrdering4() = runBlocking {
    createProjectSubFile("resources1/a.txt", "1")
    createProjectSubFile("resources2/a.txt", "2")

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <resources>
                        <resource>
                          <directory>resources1</directory>
                          <filtering>true</filtering>
                        </resource>
                        <resource>
                          <directory>resources2</directory>
                          <filtering>true</filtering>
                        </resource>
                      </resources>
                    </build>
                    """.trimIndent())

    compileModules("project")

    assertResult("target/classes/a.txt", "2") // For the filtered files last file override other files.
  }

  @Test
  fun testOverwriteParameter1() = runBlocking {
    createProjectSubFile("resources1/a.txt", "1")
    createProjectSubFile("resources2/a.txt", "2")

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <resources>
                        <resource>
                          <directory>resources1</directory>
                        </resource>
                        <resource>
                          <directory>resources2</directory>
                        </resource>
                      </resources>
                      <plugins>
                        <plugin>
                          <artifactId>maven-resources-plugin</artifactId>
                          <configuration>
                            <overwrite>true</overwrite>
                          </configuration>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())

    compileModules("project")

    assertResult("target/classes/a.txt", "2")
  }

  @Test
  fun testOverwriteParameter2() = runBlocking {
    createProjectSubFile("resources1/a.txt", "1")
    createProjectSubFile("resources2/a.txt", "2")

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <resources>
                        <resource>
                          <directory>resources1</directory>
                          <filtering>true</filtering>
                        </resource>
                        <resource>
                          <directory>resources2</directory>
                        </resource>
                      </resources>
                      <plugins>
                        <plugin>
                          <artifactId>maven-resources-plugin</artifactId>
                          <configuration>
                            <overwrite>true</overwrite>
                          </configuration>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())

    compileModules("project")

    assertResult("target/classes/a.txt", "2")
  }
}
