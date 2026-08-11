// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.idea.maven.compiler

import com.dynatrace.hash4j.hashing.Hashing
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertResources
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.createProjectSubFile
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.importProjectWithProfiles
import com.intellij.maven.testFramework.fixtures.importProjectsAsync
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.projectPath
import com.intellij.maven.testFramework.fixtures.updateAllProjects
import com.intellij.maven.testFramework.fixtures.updateProjectPom
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.module.ModuleManager.Companion.getInstance
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.idea.maven.fixtures.assertResult
import org.jetbrains.idea.maven.fixtures.compileModules
import org.jetbrains.idea.maven.fixtures.loadResult
import org.jetbrains.idea.maven.model.MavenExplicitProfiles
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.jps.maven.model.impl.MavenIdBean
import org.jetbrains.jps.maven.model.impl.MavenModuleResourceConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import java.io.IOException

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class ResourceFilteringTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  @Test
  fun testBasic() = runBlocking {
    maven.createProjectSubFile("resources/file.properties", """
      value=${'$'}{project.version}
      value2=@project.version@
      time=${'$'}{time}
      """.trimIndent())

    maven.importProjectAsync("""
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

    maven.compileModules("project")

    maven.assertResult("target/classes/file.properties", """
      value=1
      value2=1
      time=---
      """.trimIndent())
  }

  @Test
  fun testResolveSettingProperty() = runBlocking {
    maven.createProjectSubFile("resources/file.properties", "value=\${settings.localRepository}")

    maven.importProjectAsync("""
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

    maven.compileModules("project")

    assert(!maven.loadResult(maven.projectPom, "target/classes/file.properties").contains("settings.localRepository"))
  }

  @Test
  fun testCustomDelimiter() = runBlocking {
    maven.createProjectSubFile("resources/file.properties", """
      value1=${'$'}{project.version}
      value2=@project.version@
      valueX=|
      value3=|project.version|
      """.trimIndent())

    maven.importProjectAsync("""
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

    maven.compileModules("project")

    maven.assertResult("target/classes/file.properties", """
      value1=1
      value2=1
      valueX=|
      value3=1
      """.trimIndent())
  }

  @Test
  fun testPomArtifactId() = runBlocking {
    maven.createProjectSubFile("resources/file.properties", "value=\${pom.artifactId}")

    maven.importProjectAsync("""
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
    maven.compileModules("project")

    maven.assertResult("target/classes/file.properties", "value=project")
  }

  @Test
  fun testPomVersionInModules() = runBlocking {
    maven.createProjectSubFile("m1/resources/file.properties", "value=\${pom.version}")

    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m1</module>
                       </modules>
                       """.trimIndent())

    maven.createModulePom("m1",
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
    maven.importProjectAsync()

    maven.compileModules("project", "m1")

    maven.assertResult("m1/target/classes/file.properties", "value=2")
  }

  @Test
  fun testDoNotFilterSomeFileByDefault() = runBlocking {
    maven.createProjectSubFile("resources/file.bmp", "value=\${project.version}")

    maven.importProjectAsync("""
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
    maven.compileModules("project")

    maven.assertResult("target/classes/file.bmp", "value=\${project.version}")
  }

  @Test
  fun testCustomNonFilteredExtensions() = runBlocking {
    maven.createProjectSubFile("resources/file.bmp", "value=\${project.version}")
    maven.createProjectSubFile("resources/file.xxx", "value=\${project.version}")

    maven.importProjectAsync("""
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
    maven.compileModules("project")

    maven.assertResult("target/classes/file.bmp", "value=\${project.version}")
    maven.assertResult("target/classes/file.xxx", "value=\${project.version}")
  }

  @Test
  fun testFilteringTestResources() = runBlocking {
    maven.createProjectSubFile("resources/file.properties", "value=@project.version@")

    maven.importProjectAsync("""
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
    maven.compileModules("project")

    maven.assertResult("target/test-classes/file.properties", "value=1")
  }

  @Test
  fun testExcludesAndIncludes() = runBlocking {
    maven.createProjectSubFile("src/main/resources/file1.properties", "value=\${project.artifactId}")
    maven.createProjectSubFile("src/main/resources/file2.properties", "value=\${project.artifactId}")

    maven.importProjectAsync("""
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

    maven.compileModules("project")
    maven.assertResult("target/classes/file1.properties", "value=\${project.artifactId}")
    maven.assertResult("target/classes/file2.properties", "value=project")

    maven.compileModules()
    maven.assertResult("target/classes/file1.properties", "value=\${project.artifactId}")
    maven.assertResult("target/classes/file2.properties", "value=project")

    maven.compileModules("project")
    maven.assertResult("target/classes/file1.properties", "value=\${project.artifactId}")
    maven.assertResult("target/classes/file2.properties", "value=project")
  }

  @Test
  fun testEscapingWindowsChars() = runBlocking {
    maven.createProjectSubFile("resources/file.txt", """
      value=${'$'}{foo}
      value2=@foo@
      value3=${'$'}{bar}
      """.trimIndent())

    maven.importProjectAsync("""
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
    maven.compileModules("project")

    maven.assertResult("target/classes/file.txt", """
      value=c:\\projects\\foo/bar
      value2=c:\\projects\\foo/bar
      value3=a\b\c
      """.trimIndent())
  }

  @Test
  fun testDontEscapingWindowsChars() = runBlocking {
    maven.createProjectSubFile("resources/file.txt", "value=\${foo}")

    maven.importProjectAsync("""
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
    maven.compileModules("project")

    maven.assertResult("target/classes/file.txt", "value=c:\\projects\\foo/bar")
  }

  @Test
  fun testFilteringPropertiesWithEmptyValues() = runBlocking {
    maven.createProjectSubFile("resources/file.properties", "value1=\${foo}\nvalue2=\${bar}")

    maven.importProjectAsync("""
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
    maven.compileModules("project")

    maven.assertResult("target/classes/file.properties", "value1=\nvalue2=\${bar}")
  }

  @Test
  fun testFilterWithSeveralResourceFolders() = runBlocking {
    maven.createProjectSubFile("resources1/file1.properties", "value=\${project.version}")
    maven.createProjectSubFile("resources2/file2.properties", "value=\${project.version}")

    maven.importProjectAsync("""
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
    maven.compileModules("project")

    maven.assertResult("target/classes/file1.properties", "value=1")
    maven.assertResult("target/classes/file2.properties", "value=1")
  }

  @Test
  fun testFilterWithSeveralModules() = runBlocking {
    maven.createProjectSubFile("module1/resources/file1.properties", "value=\${project.version}")
    maven.createProjectSubFile("module2/resources/file2.properties", "value=\${project.version}")

    val m1 = maven.createModulePom("module1",
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

    val m2 = maven.createModulePom("module2",
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

    maven.importProjectsAsync(m1, m2)
    maven.compileModules("module1", "module2")

    maven.assertResult(m1, "target/classes/file1.properties", "value=1")
    maven.assertResult(m2, "target/classes/file2.properties", "value=2")
  }

  @Test
  fun testDoNotFilterIfNotRequested() = runBlocking {
    maven.createProjectSubFile("resources1/file1.properties", "value=\${project.version}")
    maven.createProjectSubFile("resources2/file2.properties", "value=\${project.version}")

    maven.importProjectAsync("""
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
    maven.compileModules("project")

    maven.assertResult("target/classes/file1.properties", "value=1")
    maven.assertResult("target/classes/file2.properties", "value=\${project.version}")
  }

  @Test
  fun testDoNotChangeFileIfPropertyIsNotResolved() = runBlocking {
    maven.createProjectSubFile("resources/file.properties", "value=\${foo.bar}")

    maven.importProjectAsync("""
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
    maven.compileModules("project")

    maven.assertResult("target/classes/file.properties", "value=\${foo.bar}")
  }

  @Test
  fun testChangingResolvedPropsBackWhenSettingsIsChange() = runBlocking {
    maven.createProjectSubFile("resources/file.properties", "value=\${project.version}")

    maven.importProjectAsync("""
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
    maven.compileModules("project")
    maven.assertResult("target/classes/file.properties", "value=1")

    maven.updateProjectPom("""
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
    maven.updateAllProjects()
    maven.compileModules("project")

    maven.assertResult("target/classes/file.properties", "value=\${project.version}")
  }

  @Test
  fun testUpdatingWhenPropertiesInFiltersAreChanged() = runBlocking {
    val filter = maven.createProjectSubFile("filters/filter.properties", "xxx=1")
    maven.createProjectSubFile("resources/file.properties", "value=\${xxx}")

    maven.importProjectAsync("""
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
    maven.compileModules("project")
    maven.assertResult("target/classes/file.properties", "value=1")

    WriteAction.runAndWait<IOException> { VfsUtil.saveText(filter, "xxx=2") }
    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        PsiDocumentManager.getInstance(maven.project).commitAllDocuments()
      }
    }
    maven.compileModules("project")
    maven.assertResult("target/classes/file.properties", "value=2")
  }

  @Test
  fun testUpdatingWhenPropertiesAreChanged() = runBlocking {
    maven.createProjectSubFile("resources/file.properties", "value=\${foo}")

    maven.importProjectAsync("""
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
    maven.compileModules("project")
    maven.assertResult("target/classes/file.properties", "value=val1")

    maven.updateProjectPom("""
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
    maven.updateAllProjects()
    maven.compileModules("project")
    maven.assertResult("target/classes/file.properties", "value=val2")
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
    maven.createProjectSubFile("resources/file.properties", "value=\${project.name}")

    val moduleManager = getInstance(maven.project)
    val mavenProjectsManager = MavenProjectsManager.getInstance(maven.project)

    maven.importProjectAsync("""
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

    maven.assertResources("project", "resources")
    assertEquals("val1", modelMap1["name"])

    maven.compileModules("project")
    maven.assertResult("target/classes/file.properties", "value=val1")

    maven.updateProjectPom("""
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
    maven.updateAllProjects()
    val modelMap2 = mavenProjectsManager.findProject(
      moduleManager.findModuleByName("project")!!)!!.modelMap
    val config2 = newMavenModuleResourceConfiguration(modelMap2)

    maven.assertResources("project", "resources")
    assertEquals("val2", modelMap2.get("name"))
    assertThat(getHash(config1))
      .isNotEqualTo(getHash(config2))
      .describedAs("Config hash didn't change. Module may not be recompiled properly")

    maven.compileModules("project")
    maven.assertResult("target/classes/file.properties", "value=val2")
  }

  private fun getHash(config: MavenModuleResourceConfiguration): Long {
    val hash = Hashing.komihash5_0().hashStream()
    config.computeModuleConfigurationHash(hash)
    return hash.asLong
  }

  @Test
  fun testUpdatingWhenProfilesAreChanged() = runBlocking {
    maven.createProjectSubFile("resources/file.properties", "value=\${foo}")

    maven.createProjectPom("""
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
    maven.importProjectWithProfiles("one")
    maven.compileModules("project")
    maven.assertResult("target/classes/file.properties", "value=val1")

    maven.projectsManager.explicitProfiles = MavenExplicitProfiles(mutableListOf("two"))
    maven.updateAllProjects()

    maven.compileModules("project")
    maven.assertResult("target/classes/file.properties", "value=val2")
  }

  @Test
  fun testSameFileInSourcesAndTestSources() = runBlocking {
    maven.createProjectSubFile("src/main/resources/file.properties", "foo=\${foo.main}")
    maven.createProjectSubFile("src/test/resources/file.properties", "foo=\${foo.test}")

    maven.importProjectAsync("""
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
    maven.compileModules("project")

    maven.assertResult("target/classes/file.properties", "foo=main")
    maven.assertResult("target/test-classes/file.properties", "foo=test")
  }

  @Test
  fun testCustomFilters() = runBlocking {
    maven.createProjectSubFile("filters/filter1.properties",
                         """
                           xxx=value
                           yyy=${'$'}{project.version}
                           """.trimIndent())
    maven.createProjectSubFile("filters/filter2.properties", "zzz=value2")
    maven.createProjectSubFile("resources/file.properties",
                         """
                           value1=${'$'}{xxx}
                           value2=${'$'}{yyy}
                           value3=${'$'}{zzz}
                           """.trimIndent())

    maven.importProjectAsync("""
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
    maven.compileModules("project")

    maven.assertResult("target/classes/file.properties", """
      value1=value
      value2=1
      value3=value2
      """.trimIndent())
  }

  @Test
  fun testCustomFiltersViaPlugin() = runBlocking {
    maven.createProjectSubFile("filters/filter.properties", "xxx=value")
    maven.createProjectSubFile("resources/file.properties", "value1=\${xxx}")

    maven.importProjectAsync("""
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
    maven.compileModules("project")

    maven.assertResult("target/classes/file.properties", "value1=value")
  }

  @Test
  fun testCustomFilterWithPropertyInThePath() = runBlocking {
    maven.createProjectSubFile("filters/filter.properties", "xxx=value")
    maven.createProjectSubFile("resources/file.properties", "value=\${xxx}")

    maven.importProjectAsync("""
                      <groupId>test</groupId>
                      <artifactId>project</artifactId>
                      <version>1</version>
                      <properties>
                       <some.path>
                      ${maven.projectPath}/filters</some.path>
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
    maven.compileModules("project")

    maven.assertResult("target/classes/file.properties", "value=value")
  }

  @Test
  fun testCustomFiltersFromProfiles() = runBlocking {
    maven.createProjectSubFile("filters/filter1.properties", "xxx=value1")
    maven.createProjectSubFile("filters/filter2.properties", "yyy=value2")
    maven.createProjectSubFile("resources/file.properties",
                         """
                           value1=${'$'}{xxx}
                           value2=${'$'}{yyy}
                           """.trimIndent())

    maven.createProjectPom("""
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

    maven.importProjectWithProfiles("one")
    maven.compileModules("project")
    maven.assertResult("target/classes/file.properties", """
      value1=value1
      value2=${'$'}{yyy}
      """.trimIndent())

    maven.importProjectWithProfiles("two")
    maven.compileModules("project")
    maven.assertResult("target/classes/file.properties", """
      value1=${'$'}{xxx}
      value2=value2
      """.trimIndent())
  }

  @Test
  fun testEscapingFiltering() = runBlocking {
    maven.createProjectSubFile("filters/filter.properties", "xxx=value")
    maven.createProjectSubFile("resources/file.properties",
                         """
                           value1=\${'$'}{xxx}
                           value2=\\${'$'}{xxx}
                           value3=\\\${'$'}{xxx}
                           value3=\\\\${'$'}{xxx}
                           value4=.\.\\.\\\.
                           """.trimIndent())

    maven.importProjectAsync("""
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

    maven.compileModules("project")
    maven.assertResult("target/classes/file.properties",
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
    maven.createProjectSubFile("filters/filter.properties", """
   xxx=fromFilterFile
   yyy=fromFilterFile
   """.trimIndent())
    maven.createProjectSubFile("resources/file.properties", """
   value1=${"$"}{xxx}
   value2=${"$"}{yyy}
   """.trimIndent())

    maven.importProjectAsync("""
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

    maven.compileModules("project")
    maven.assertResult("target/classes/file.properties",
                 """
                       value1=fromProperties
                       value2=fromFilterFile
                       """.trimIndent())
  }

  @Test
  fun testCustomEscapingFiltering() = runBlocking {
    maven.createProjectSubFile("filters/filter.properties", "xxx=value")
    maven.createProjectSubFile("resources/file.properties",
                         """
                           value1=^${'$'}{xxx}
                           value2=\${'$'}{xxx}
                           """.trimIndent())

    maven.importProjectAsync("""
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

    maven.compileModules("project")
    maven.assertResult("target/classes/file.properties",
                 """
                   value1=${'$'}{xxx}
                   value2=\value
                   """.trimIndent())
  }

  @Test
  fun testDoNotFilterButCopyBigFiles() = runBlocking {
    assertEquals(FileTypes.UNKNOWN, FileTypeManager.getInstance().getFileTypeByFileName("file.xyz"))

    WriteAction.runAndWait<IOException> { maven.createProjectSubFile("resources/file.xyz").setBinaryContent(ByteArray(1024 * 1024 * 20)) }

    maven.importProjectAsync("""
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
    maven.compileModules("project")

    assertNotNull(maven.projectPom.getParent().findFileByRelativePath("target/classes/file.xyz"))
  }

  @Test
  fun testResourcesOrdering1() = runBlocking {
    maven.createProjectSubFile("resources/file.properties", "value=\${project.version}\n")

    maven.importProjectAsync("""
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

    maven.compileModules("project")

    maven.assertResult("target/classes/file.properties", "value=1\n") // Filtered file override non-filtered file
  }

  @Test
  fun testResourcesOrdering2() = runBlocking {
    maven.createProjectSubFile("resources/file.properties", "value=\${project.version}\n")

    maven.importProjectAsync("""
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

    maven.compileModules("project")

    maven.assertResult("target/classes/file.properties", "value=1\n") // Filtered file override non-filtered file
  }

  @Test
  fun testResourcesOrdering3() = runBlocking {
    maven.createProjectSubFile("resources1/a.txt", "1")
    maven.createProjectSubFile("resources2/a.txt", "2")

    maven.importProjectAsync("""
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

    maven.compileModules("project")

    maven.assertResult("target/classes/a.txt", "1") // First file was copied, second file was not override first file
  }

  @Test
  fun testResourcesOrdering4() = runBlocking {
    maven.createProjectSubFile("resources1/a.txt", "1")
    maven.createProjectSubFile("resources2/a.txt", "2")

    maven.importProjectAsync("""
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

    maven.compileModules("project")

    maven.assertResult("target/classes/a.txt", "2") // For the filtered files last file override other files.
  }

  @Test
  fun testOverwriteParameter1() = runBlocking {
    maven.createProjectSubFile("resources1/a.txt", "1")
    maven.createProjectSubFile("resources2/a.txt", "2")

    maven.importProjectAsync("""
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

    maven.compileModules("project")

    maven.assertResult("target/classes/a.txt", "2")
  }

  @Test
  fun testOverwriteParameter2() = runBlocking {
    maven.createProjectSubFile("resources1/a.txt", "1")
    maven.createProjectSubFile("resources2/a.txt", "2")

    maven.importProjectAsync("""
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

    maven.compileModules("project")

    maven.assertResult("target/classes/a.txt", "2")
  }
}
