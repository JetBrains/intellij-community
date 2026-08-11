/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.compiler.CompilerConfiguration
import com.intellij.compiler.CompilerConfigurationImpl
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertDefaultResources
import com.intellij.maven.testFramework.fixtures.assertDefaultTestResources
import com.intellij.maven.testFramework.fixtures.assertModules
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.createProjectSubDir
import com.intellij.maven.testFramework.fixtures.createProjectSubFile
import com.intellij.maven.testFramework.fixtures.getModule
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.projectPath
import com.intellij.maven.testFramework.fixtures.projectRoot
import com.intellij.maven.testFramework.fixtures.refreshFiles
import com.intellij.maven.testFramework.fixtures.testRootDisposable
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.compiler.options.ExcludeEntryDescription
import com.intellij.openapi.module.ModuleManager.Companion.getInstance
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.fixtures.assertCopied
import org.jetbrains.idea.maven.fixtures.assertNotCopied
import org.jetbrains.idea.maven.fixtures.compileFile
import org.jetbrains.idea.maven.fixtures.compileModules
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import kotlin.io.path.isWritable
import kotlin.io.path.setPosixFilePermissions
import kotlin.io.path.writeText

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class ResourceCopyingTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  

  @BeforeEach
  fun setUp() {
    CompilerConfiguration.getInstance(maven.project).addResourceFilePattern("!*.xxx")
    CompilerConfiguration.getInstance(maven.project).addResourceFilePattern("!*.yyy")
    CompilerConfiguration.getInstance(maven.project).addResourceFilePattern("!*.zzz")
  }

  @Test
  fun testBasic() = runBlocking {
    maven.createProjectSubFile("src/main/resources/dir/file.properties")

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    maven.compileModules("project")

    maven.assertCopied("target/classes/dir/file.properties")
  }

  @Test
  fun testCustomResources() = runBlocking {
    maven.createProjectSubFile("res/dir1/file1.properties")
    maven.createProjectSubFile("testRes/dir2/file2.properties")

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <resources>
                        <resource><directory>res</directory></resource>
                      </resources>
                      <testResources>
                        <testResource><directory>testRes</directory></testResource>
                      </testResources>
                    </build>
                    """.trimIndent())

    maven.compileModules("project")

    maven.assertCopied("target/classes/dir1/file1.properties")
    maven.assertCopied("target/test-classes/dir2/file2.properties")
  }

  @Test
  fun testCopyWithFilteringIntoReadonlyTarget() = runBlocking {
    val f = maven.createProjectSubFile("res/dir1/file.properties",  /*"Hello world"*/"Hello \${name}")
    val srcFile = f.toNioPath()

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <properties>
                      <name>world</name>
                    </properties>
                    <build>
                      <resources>
                        <resource>
                           <directory>res</directory>
                           <filtering>true</filtering>
                        </resource>
                      </resources>
                    </build>
                    """.trimIndent())

    maven.compileModules("project")
    maven.assertCopied("target/classes/dir1/file.properties", "Hello world")

    // make sure the output file is readonly
    val outFile = maven.projectPath.resolve("target/classes/dir1/file.properties")
    outFile.setReadOnly()
    assertFalse(outFile.isWritable())

    srcFile.writeText("Hello, \${name}")

    maven.compileModules("project")
    maven.assertCopied("target/classes/dir1/file.properties", "Hello, world")
  }

  @Test
  fun testCustomTargetPath() = runBlocking {
    maven.createProjectSubFile("res/dir/file.properties")

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <resources>
                        <resource>
                          <directory>res</directory>
                          <targetPath>foo</targetPath>
                        </resource>
                      </resources>
                    </build>
                    """.trimIndent())

    maven.compileModules("project")
    maven.assertCopied("target/classes/foo/dir/file.properties")
  }

  @Test
  fun testResourcesPluginCustomTargetPath() = runBlocking {
    maven.createProjectSubFile("res/dir/file.properties")

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <artifactId>maven-resources-plugin</artifactId>
                          <version>2.6</version>
                          <configuration>
                            <outputDirectory>${'$'}{basedir}/target/resourceOutput</outputDirectory>
                          </configuration>
                        </plugin>
                      </plugins>
                      <resources>
                        <resource>
                          <directory>res</directory>
                          <targetPath>foo</targetPath>
                        </resource>
                      </resources>
                    </build>
                    """.trimIndent())

    maven.compileModules("project")
    maven.assertCopied("target/resourceOutput/foo/dir/file.properties")
  }

  @Test
  fun testResourcesPluginGoalAbsoluteCustomTargetPath() = runBlocking {
    maven.createProjectSubFile("src/test/resources/dir/file.properties")

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <artifactId>maven-resources-plugin</artifactId>
                          <version>2.6</version>
                          <executions>
                           <execution>
                             <id>default-testResources</id>
                             <phase>process-test-resources</phase>
                             <goals>
                               <goal>testResources</goal>
                             </goals>
                             <configuration>
                               <outputDirectory>${'$'}{project.build.testOutputDirectory}/custom</outputDirectory>
                             </configuration>
                           </execution>
                          </executions>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())

    maven.compileModules("project")
    maven.assertCopied("target/test-classes/custom/dir/file.properties")
  }

  @Test
  fun testResourcesPluginGoalRelativeCustomTargetPath() = runBlocking {
    maven.createProjectSubFile("src/test/resources/dir/file.properties")

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <artifactId>maven-resources-plugin</artifactId>
                          <version>2.6</version>
                          <executions>
                           <execution>
                             <id>default-testResources</id>
                             <phase>process-test-resources</phase>
                             <goals>
                               <goal>testResources</goal>
                             </goals>
                             <configuration>
                               <outputDirectory>target/test-classes/custom</outputDirectory>
                             </configuration>
                           </execution>
                          </executions>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())

    maven.compileModules("project")
    maven.assertCopied("target/test-classes/custom/dir/file.properties")
  }

  @Test
  fun testAbsoluteCustomTargetPath() = runBlocking {
    maven.createProjectSubFile("res/foo/file.properties")

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <resources>
                        <resource>
                          <directory>res</directory>
                          <targetPath>${'$'}{project.build.directory}/anotherDir</targetPath>
                        </resource>
                      </resources>
                    </build>
                    """.trimIndent())

    maven.compileModules("project")
    maven.assertCopied("target/anotherDir/foo/file.properties")
  }

  @Test
  fun testMavenSpecifiedPattern() = runBlocking {
    maven.createProjectSubFile("res/subdir/a.txt")
    maven.createProjectSubFile("res/b.txt")

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <resources>
                        <resource>
                          <directory>res</directory>
                          <includes>
                            <include>**/**</include>
                          </includes>
                        </resource>
                      </resources>
                    </build>
                    """.trimIndent())

    maven.compileModules("project")

    maven.assertCopied("target/classes/subdir/a.txt")
    maven.assertCopied("target/classes/b.txt")
  }

  @Test
  fun testMavenSpecifiedPatternEndSlash() = runBlocking {
    maven.createProjectSubFile("res/subdir/a.txt")
    maven.createProjectSubFile("res/b.txt")

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <resources>
                        <resource>
                          <directory>res</directory>
                          <includes>
                            <include>**/</include>
                          </includes>
                        </resource>
                      </resources>
                    </build>
                    """.trimIndent())

    maven.compileModules("project")

    maven.assertCopied("target/classes/subdir/a.txt")
    maven.assertCopied("target/classes/b.txt")
  }

  @Test
  fun testIncludesAndExcludes() = runBlocking {
    maven.createProjectSubFile("res/dir/file.xxx")
    maven.createProjectSubFile("res/dir/file.yyy")
    maven.createProjectSubFile("res/file.xxx")
    maven.createProjectSubFile("res/file.yyy")
    maven.createProjectSubFile("res/file.zzz")

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <resources>
                        <resource>
                          <directory>res</directory>
                          <includes>
                            <include>**/*.xxx</include>
                            <include>**/*.yyy</include>
                          </includes>
                          <excludes>
                            <exclude>*.xxx</exclude>
                            <exclude>dir/*.yyy</exclude>
                          </excludes>
                        </resource>
                      </resources>
                    </build>
                    """.trimIndent())

    maven.compileModules("project")

    maven.assertCopied("target/classes/dir/file.xxx")
    maven.assertNotCopied("target/classes/dir/file.yyy")
    maven.assertNotCopied("target/classes/file.xxx")
    maven.assertCopied("target/classes/file.yyy")
    maven.assertNotCopied("target/classes/file.zzz")
  }

  @Test
  fun testDoNotCopyIgnoredFiles() = runBlocking {
    maven.createProjectSubFile("res/CVS/file.properties")
    maven.createProjectSubFile("res/.svn/file.properties")
    maven.createProjectSubFile("res/zzz/file.properties")

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <resources>
                        <resource>
                          <directory>res</directory>
                          <includes>
                            <include>**/*.properties</include>
                          </includes>
                        </resource>
                      </resources>
                    </build>
                    """.trimIndent())

    maven.compileModules("project")

    maven.assertNotCopied("target/classes/CVS")
    maven.assertNotCopied("target/classes/.svn")
    maven.assertCopied("target/classes/zzz/file.properties")
  }

  @Test
  fun testDeletingFilesThatWasCopiedAndThenDeleted() = runBlocking {
    val file = maven.createProjectSubFile("res/file.properties")

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <resources>
                        <resource>
                          <directory>res</directory>
                        </resource>
                      </resources>
                    </build>
                    """.trimIndent())

    maven.compileModules("project")
    maven.assertCopied("target/classes/file.properties")

    WriteCommandAction.writeCommandAction(maven.project).run<IOException> { file.delete(this) }

    maven.compileModules("project")
    maven.assertNotCopied("target/classes/file.properties")
  }

  @Test
  fun testDeletingFilesThatWasCopiedAndThenExcluded() = runBlocking {
    maven.createProjectSubFile("res/file.properties")

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <resources>
                        <resource>
                          <directory>res</directory>
                        </resource>
                      </resources>
                    </build>
                    """.trimIndent())

    maven.compileModules("project")
    maven.assertCopied("target/classes/file.properties")

    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <resources>
                           <resource>
                             <directory>res</directory>
                             <excludes>
                               <exclude>**/*</exclude>
                             </excludes>
                           </resource>
                         </resources>
                       </build>
                       """.trimIndent())
    maven.refreshFiles(listOf(maven.projectPom))
    maven.importProjectAsync()

    maven.compileModules("project")
    maven.assertNotCopied("target/classes/file.properties")
  }

  @Test
  fun testDoNotCopyExcludedStandardResources() = runBlocking {
    val configuration = CompilerConfiguration.getInstance(maven.project) as CompilerConfigurationImpl
    configuration.addResourceFilePattern("*.zzz")

    maven.createProjectSubFile("res/file.xxx")
    maven.createProjectSubFile("res/file.zzz")

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <resources>
                        <resource>
                          <directory>res</directory>
                          <includes>
                            <include>**/*.xxx</include>
                          </includes>
                          <excludes>
                            <exclude>**/*.zzz</exclude>
                          </excludes>
                        </resource>
                      </resources>
                    </build>
                    """.trimIndent())

    maven.compileModules("project")
    maven.assertCopied("target/classes/file.xxx")
    maven.assertNotCopied("target/classes/file.zzz")
  }

  @Test
  fun testDoNotDeleteFilesFromOtherModulesOutput() = runBlocking {
    maven.createProjectSubFile("m1/resources/file.xxx")
    maven.createProjectSubFile("m2/resources/file.yyy")

    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())

    maven.createModulePom("m1",
                    """
                      <groupId>test</groupId>
                      <artifactId>m1</artifactId>
                      <version>1</version>
                      <build>
                        <resources>
                          <resource>
                            <directory>resources</directory>
                          </resource>
                        </resources>
                      </build>
                      """.trimIndent())

    maven.createModulePom("m2",
                    """
                      <groupId>test</groupId>
                      <artifactId>m2</artifactId>
                      <version>1</version>
                      <build>
                        <resources>
                          <resource>
                            <directory>resources</directory>
                          </resource>
                        </resources>
                      </build>
                      """.trimIndent())
    maven.importProjectAsync()

    maven.compileModules("project", "m1", "m2")
    maven.assertCopied("m1/target/classes/file.xxx")
    maven.assertCopied("m2/target/classes/file.yyy")

    maven.compileModules("m1")
    maven.assertCopied("m1/target/classes/file.xxx")
    maven.assertCopied("m2/target/classes/file.yyy")

    maven.compileModules("m2")
    maven.assertCopied("m1/target/classes/file.xxx")
    maven.assertCopied("m2/target/classes/file.yyy")

    maven.compileModules("project")
    maven.assertCopied("m1/target/classes/file.xxx")
    maven.assertCopied("m2/target/classes/file.yyy")
  }

  @Test
  fun testDoNotDeleteFilesFromOtherModulesOutputWhenOutputIsTheSame() = runBlocking {
    maven.createProjectSubFile("resources/file.xxx")

    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())

    maven.createModulePom("m1",
                    """
                      <groupId>test</groupId>
                      <artifactId>m1</artifactId>
                      <version>1</version>
                      <build>
                        <resources>
                          <resource>
                            <directory>../resources</directory>
                          </resource>
                        </resources>
                      </build>
                      """.trimIndent())

    maven.createModulePom("m2",
                    """
                      <groupId>test</groupId>
                      <artifactId>m2</artifactId>
                      <version>1</version>
                      <build>
                        <resources>
                          <resource>
                            <directory>../resources</directory>
                          </resource>
                        </resources>
                      </build>
                      """.trimIndent())
    maven.importProjectAsync()

    WriteCommandAction.writeCommandAction(maven.project)
      .run<IOException> { setModulesOutput(maven.projectRoot.createChildDirectory(this, "output"), "project", "m1", "m2") }


    maven.compileModules("project", "m1", "m2")
    maven.assertCopied("output/file.xxx")

    maven.compileModules("m1")
    maven.assertCopied("output/file.xxx")

    maven.compileModules("m2")
    maven.assertCopied("output/file.xxx")

    maven.compileModules("project")
    maven.assertCopied("output/file.xxx")
  }

  private fun setModulesOutput(output: VirtualFile, vararg moduleNames: String) {
    WriteAction.run<RuntimeException> {
      for (each in moduleNames) {
        PsiTestUtil.setCompilerOutputPath(maven.getModule(each), output.url, false)
        PsiTestUtil.setCompilerOutputPath(maven.getModule(each), output.url, true)
      }
    }
  }

  @Test
  fun testCopingNonMavenResources() = runBlocking {
    maven.createProjectSubFile("src/main/resources/a.txt", "a")

    val configDir = maven.createProjectSubDir("src/config")
    maven.createProjectSubFile("src/config/b.txt", "b")
    maven.createProjectSubFile("src/config/JavaClass.java", "class JavaClass {}")
    maven.createProjectSubFile("src/config/xxx.xxx", "xxx") // *.xxx is excluded from resource coping, see setUpInWriteAction()

    val excludedDir = maven.createProjectSubDir("src/excluded")
    maven.createProjectSubFile("src/excluded/c.txt", "c")

    maven.importProjectAsync("""
                    <groupId>test</groupId><artifactId>project</artifactId><version>1</version><properties>
                            <maven.compiler.source>11</maven.compiler.source>
                            <maven.compiler.target>11</maven.compiler.target>
                        </properties>
                        """.trimIndent())

    val module = getInstance(maven.project).findModuleByName("project")
    PsiTestUtil.addSourceRoot(module!!, configDir)
    PsiTestUtil.addSourceRoot(module, excludedDir)

    WriteCommandAction.writeCommandAction(maven.project).run<IOException> {
      CompilerConfiguration.getInstance(maven.project).getExcludedEntriesConfiguration()
        .addExcludeEntryDescription(ExcludeEntryDescription(excludedDir, true, false, maven.testRootDisposable))
      setModulesOutput(maven.projectRoot.createChildDirectory(this, "output"), "project")
    }

    maven.compileModules("project")

    maven.assertCopied("output/a.txt")
    maven.assertCopied("output/b.txt")

    maven.assertNotCopied("output/JavaClass.java")
    maven.assertCopied("output/xxx.xxx")
    maven.assertNotCopied("output/c.txt")
  }

  @Test
  fun testCopyTestResourceWhenBuildingTestModule() = runBlocking {
    maven.createProjectSubFile("src/test/resources/file.properties")

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <properties>
                      <maven.compiler.release>8</maven.compiler.release>
                      <maven.compiler.testRelease>11</maven.compiler.testRelease>
                    </properties>
                     <build>
                      <plugins>
                        <plugin>
                          <artifactId>maven-compiler-plugin</artifactId>
                          <version>3.11.0</version>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent()
    )

    maven.assertModules("project", "project.main", "project.test")
    maven.compileModules("project.test")
    maven.assertCopied("target/test-classes/file.properties")
  }


  @Test
  fun testCopyMainAndTestResourcesWhenBuilding() = runBlocking {
    maven.createProjectSubFile("src/main/resources/file.properties")
    maven.createProjectSubFile("src/test/resources/file-test.properties")

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <properties>
                      <maven.compiler.release>8</maven.compiler.release>
                      <maven.compiler.testRelease>11</maven.compiler.testRelease>
                    </properties>
                     <build>
                      <plugins>
                        <plugin>
                          <artifactId>maven-compiler-plugin</artifactId>
                          <version>3.11.0</version>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent()
    )

    maven.assertModules("project", "project.main", "project.test")
    maven.assertDefaultResources("project.main")
    maven.assertDefaultTestResources("project.test")
    maven.compileModules("project", "project.main", "project.test")
    maven.assertCopied("target/classes/file.properties")
    maven.assertNotCopied("target/classes/file-test.properties")
    maven.assertCopied("target/test-classes/file-test.properties")
    maven.assertNotCopied("target/test-classes/file.properties")
  }

  @Test
  fun testAnnotationPathsInCompoundModules() = runBlocking {
    maven.createProjectSubFile("src/main/java/Main.java", "class Main {}")
    maven.createProjectSubFile("src/test/java/Test.java", "class Test {}")

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <properties>
                      <maven.compiler.release>8</maven.compiler.release>
                      <maven.compiler.testRelease>11</maven.compiler.testRelease>
                    </properties>
                     <build>
                      <plugins>
                        <plugin>
                          <artifactId>maven-compiler-plugin</artifactId>
                          <version>3.11.0</version>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent()
    )

    maven.assertModules("project", "project.main", "project.test")
    maven.compileModules("project", "project.main", "project.test")

    maven.assertCopied("target/generated-sources/annotations")
    maven.assertCopied("target/generated-test-sources/test-annotations")
  }


  @Test
  fun testFileResourceRebuildShouldNotTouchOtherFiles() = runBlocking {
    val cssContent = """
      body {
          color: red;
      }
      """.trimIndent()
    val css = maven.createProjectSubFile("src/main/resources/text.css", cssContent)
    val txt = maven.createProjectSubFile("src/main/resources/text.txt", "hello 1")

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    maven.compileModules("project")

    maven.assertCopied("target/classes/text.css", cssContent)
    maven.assertCopied("target/classes/text.txt", "hello 1")
    val content = "hello 2"
    Files.write(txt.toNioPath(), content.toByteArray(StandardCharsets.UTF_8))

    maven.compileFile("project", txt)

    maven.assertCopied("target/classes/text.css", cssContent)
    maven.assertCopied("target/classes/text.txt", "hello 2")
  }

  private fun Path.setReadOnly() {
    if (SystemInfo.isWindows) {
      Files.setAttribute(this, "dos:readonly", true)
    }
    else {
      val readOnly = setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_READ)
      this.setPosixFilePermissions(readOnly)
    }
  }
}
