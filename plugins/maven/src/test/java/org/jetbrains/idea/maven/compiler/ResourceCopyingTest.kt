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
import com.intellij.maven.testFramework.MavenCompilingTestCase
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.compiler.options.ExcludeEntryDescription
import com.intellij.openapi.module.ModuleManager.Companion.getInstance
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PsiTestUtil
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import kotlin.io.path.isWritable
import kotlin.io.path.setPosixFilePermissions
import kotlin.io.path.writeText

class ResourceCopyingTest : MavenCompilingTestCase() {

  override fun setUpInWriteAction() {
    super.setUpInWriteAction()

    CompilerConfiguration.getInstance(project).addResourceFilePattern("!*.xxx")
    CompilerConfiguration.getInstance(project).addResourceFilePattern("!*.yyy")
    CompilerConfiguration.getInstance(project).addResourceFilePattern("!*.zzz")
  }

  @Test
  fun testBasic() = runBlocking {
    createProjectSubFile("src/main/resources/dir/file.properties")

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    compileModules("project")

    assertCopied("target/classes/dir/file.properties")
  }

  @Test
  fun testCustomResources() = runBlocking {
    createProjectSubFile("res/dir1/file1.properties")
    createProjectSubFile("testRes/dir2/file2.properties")

    importProjectAsync("""
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

    compileModules("project")

    assertCopied("target/classes/dir1/file1.properties")
    assertCopied("target/test-classes/dir2/file2.properties")
  }

  @Test
  fun testCopyWithFilteringIntoReadonlyTarget() = runBlocking {
    val f = createProjectSubFile("res/dir1/file.properties",  /*"Hello world"*/"Hello \${name}")
    val srcFile = f.toNioPath()

    importProjectAsync("""
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

    compileModules("project")
    assertCopied("target/classes/dir1/file.properties", "Hello world")

    // make sure the output file is readonly
    val outFile = projectPath.resolve("target/classes/dir1/file.properties")
    outFile.setReadOnly()
    assertFalse(outFile.isWritable())

    srcFile.writeText("Hello, \${name}")

    compileModules("project")
    assertCopied("target/classes/dir1/file.properties", "Hello, world")
  }

  @Test
  fun testCustomTargetPath() = runBlocking {
    createProjectSubFile("res/dir/file.properties")

    importProjectAsync("""
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

    compileModules("project")
    assertCopied("target/classes/foo/dir/file.properties")
  }

  @Test
  fun testResourcesPluginCustomTargetPath() = runBlocking {
    createProjectSubFile("res/dir/file.properties")

    importProjectAsync("""
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

    compileModules("project")
    assertCopied("target/resourceOutput/foo/dir/file.properties")
  }

  @Test
  fun testResourcesPluginGoalAbsoluteCustomTargetPath() = runBlocking {
    createProjectSubFile("src/test/resources/dir/file.properties")

    importProjectAsync("""
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

    compileModules("project")
    assertCopied("target/test-classes/custom/dir/file.properties")
  }

  @Test
  fun testResourcesPluginGoalRelativeCustomTargetPath() = runBlocking {
    createProjectSubFile("src/test/resources/dir/file.properties")

    importProjectAsync("""
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

    compileModules("project")
    assertCopied("target/test-classes/custom/dir/file.properties")
  }

  @Test
  fun testAbsoluteCustomTargetPath() = runBlocking {
    createProjectSubFile("res/foo/file.properties")

    importProjectAsync("""
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

    compileModules("project")
    assertCopied("target/anotherDir/foo/file.properties")
  }

  @Test
  fun testMavenSpecifiedPattern() = runBlocking {
    createProjectSubFile("res/subdir/a.txt")
    createProjectSubFile("res/b.txt")

    importProjectAsync("""
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

    compileModules("project")

    assertCopied("target/classes/subdir/a.txt")
    assertCopied("target/classes/b.txt")
  }

  @Test
  fun testMavenSpecifiedPatternEndSlash() = runBlocking {
    createProjectSubFile("res/subdir/a.txt")
    createProjectSubFile("res/b.txt")

    importProjectAsync("""
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

    compileModules("project")

    assertCopied("target/classes/subdir/a.txt")
    assertCopied("target/classes/b.txt")
  }

  @Test
  fun testIncludesAndExcludes() = runBlocking {
    createProjectSubFile("res/dir/file.xxx")
    createProjectSubFile("res/dir/file.yyy")
    createProjectSubFile("res/file.xxx")
    createProjectSubFile("res/file.yyy")
    createProjectSubFile("res/file.zzz")

    importProjectAsync("""
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

    compileModules("project")

    assertCopied("target/classes/dir/file.xxx")
    assertNotCopied("target/classes/dir/file.yyy")
    assertNotCopied("target/classes/file.xxx")
    assertCopied("target/classes/file.yyy")
    assertNotCopied("target/classes/file.zzz")
  }

  @Test
  fun testDoNotCopyIgnoredFiles() = runBlocking {
    createProjectSubFile("res/CVS/file.properties")
    createProjectSubFile("res/.svn/file.properties")
    createProjectSubFile("res/zzz/file.properties")

    importProjectAsync("""
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

    compileModules("project")

    assertNotCopied("target/classes/CVS")
    assertNotCopied("target/classes/.svn")
    assertCopied("target/classes/zzz/file.properties")
  }

  @Test
  fun testDeletingFilesThatWasCopiedAndThenDeleted() = runBlocking {
    val file = createProjectSubFile("res/file.properties")

    importProjectAsync("""
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

    compileModules("project")
    assertCopied("target/classes/file.properties")

    WriteCommandAction.writeCommandAction(project).run<IOException> { file.delete(this) }

    compileModules("project")
    assertNotCopied("target/classes/file.properties")
  }

  @Test
  fun testDeletingFilesThatWasCopiedAndThenExcluded() = runBlocking {
    createProjectSubFile("res/file.properties")

    importProjectAsync("""
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

    compileModules("project")
    assertCopied("target/classes/file.properties")

    createProjectPom("""
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
    refreshFiles(listOf(projectPom))
    importProjectAsync()

    compileModules("project")
    assertNotCopied("target/classes/file.properties")
  }

  @Test
  fun testDoNotCopyExcludedStandardResources() = runBlocking {
    val configuration = CompilerConfiguration.getInstance(project) as CompilerConfigurationImpl
    configuration.addResourceFilePattern("*.zzz")

    createProjectSubFile("res/file.xxx")
    createProjectSubFile("res/file.zzz")

    importProjectAsync("""
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

    compileModules("project")
    assertCopied("target/classes/file.xxx")
    assertNotCopied("target/classes/file.zzz")
  }

  @Test
  fun testDoNotDeleteFilesFromOtherModulesOutput() = runBlocking {
    createProjectSubFile("m1/resources/file.xxx")
    createProjectSubFile("m2/resources/file.yyy")

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())

    createModulePom("m1",
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

    createModulePom("m2",
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
    importProjectAsync()

    compileModules("project", "m1", "m2")
    assertCopied("m1/target/classes/file.xxx")
    assertCopied("m2/target/classes/file.yyy")

    compileModules("m1")
    assertCopied("m1/target/classes/file.xxx")
    assertCopied("m2/target/classes/file.yyy")

    compileModules("m2")
    assertCopied("m1/target/classes/file.xxx")
    assertCopied("m2/target/classes/file.yyy")

    compileModules("project")
    assertCopied("m1/target/classes/file.xxx")
    assertCopied("m2/target/classes/file.yyy")
  }

  @Test
  fun testDoNotDeleteFilesFromOtherModulesOutputWhenOutputIsTheSame() = runBlocking {
    createProjectSubFile("resources/file.xxx")

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())

    createModulePom("m1",
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

    createModulePom("m2",
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
    importProjectAsync()

    WriteCommandAction.writeCommandAction(project)
      .run<IOException> { setModulesOutput(projectRoot.createChildDirectory(this, "output"), "project", "m1", "m2") }


    compileModules("project", "m1", "m2")
    assertCopied("output/file.xxx")

    compileModules("m1")
    assertCopied("output/file.xxx")

    compileModules("m2")
    assertCopied("output/file.xxx")

    compileModules("project")
    assertCopied("output/file.xxx")
  }

  private fun setModulesOutput(output: VirtualFile, vararg moduleNames: String) {
    WriteAction.run<RuntimeException> {
      for (each in moduleNames) {
        PsiTestUtil.setCompilerOutputPath(getModule(each), output.url, false)
        PsiTestUtil.setCompilerOutputPath(getModule(each), output.url, true)
      }
    }
  }

  @Test
  fun testCopingNonMavenResources() = runBlocking {
    createProjectSubFile("src/main/resources/a.txt", "a")

    val configDir = createProjectSubDir("src/config")
    createProjectSubFile("src/config/b.txt", "b")
    createProjectSubFile("src/config/JavaClass.java", "class JavaClass {}")
    createProjectSubFile("src/config/xxx.xxx", "xxx") // *.xxx is excluded from resource coping, see setUpInWriteAction()

    val excludedDir = createProjectSubDir("src/excluded")
    createProjectSubFile("src/excluded/c.txt", "c")

    importProjectAsync("""
                    <groupId>test</groupId><artifactId>project</artifactId><version>1</version><properties>
                            <maven.compiler.source>11</maven.compiler.source>
                            <maven.compiler.target>11</maven.compiler.target>
                        </properties>
                        """.trimIndent())

    val module = getInstance(project).findModuleByName("project")
    PsiTestUtil.addSourceRoot(module!!, configDir)
    PsiTestUtil.addSourceRoot(module, excludedDir)

    WriteCommandAction.writeCommandAction(project).run<IOException> {
      CompilerConfiguration.getInstance(project).getExcludedEntriesConfiguration()
        .addExcludeEntryDescription(ExcludeEntryDescription(excludedDir, true, false, getTestRootDisposable()))
      setModulesOutput(projectRoot.createChildDirectory(this, "output"), "project")
    }

    compileModules("project")

    assertCopied("output/a.txt")
    assertCopied("output/b.txt")

    assertNotCopied("output/JavaClass.java")
    assertCopied("output/xxx.xxx")
    assertNotCopied("output/c.txt")
  }

  @Test
  fun testCopyTestResourceWhenBuildingTestModule() = runBlocking {
    createProjectSubFile("src/test/resources/file.properties")

    importProjectAsync("""
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

    assertModules("project", "project.main", "project.test")
    compileModules("project.test")
    assertCopied("target/test-classes/file.properties")
  }


  @Test
  fun testCopyMainAndTestResourcesWhenBuilding() = runBlocking {
    createProjectSubFile("src/main/resources/file.properties")
    createProjectSubFile("src/test/resources/file-test.properties")

    importProjectAsync("""
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

    assertModules("project", "project.main", "project.test")
    assertDefaultResources("project.main")
    assertDefaultTestResources("project.test")
    compileModules("project", "project.main", "project.test")
    assertCopied("target/classes/file.properties")
    assertNotCopied("target/classes/file-test.properties")
    assertCopied("target/test-classes/file-test.properties")
    assertNotCopied("target/test-classes/file.properties")
  }

  @Test
  fun testAnnotationPathsInCompoundModules() = runBlocking {
    createProjectSubFile("src/main/java/Main.java", "class Main {}")
    createProjectSubFile("src/test/java/Test.java", "class Test {}")

    importProjectAsync("""
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

    assertModules("project", "project.main", "project.test")
    compileModules("project", "project.main", "project.test")

    assertCopied("target/generated-sources/annotations")
    assertCopied("target/generated-test-sources/test-annotations")
  }


  @Test
  fun testFileResourceRebuildShouldNotTouchOtherFiles() = runBlocking {
    val cssContent = """
      body {
          color: red;
      }
      """.trimIndent()
    val css = createProjectSubFile("src/main/resources/text.css", cssContent)
    val txt = createProjectSubFile("src/main/resources/text.txt", "hello 1")

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    compileModules("project")

    assertCopied("target/classes/text.css", cssContent)
    assertCopied("target/classes/text.txt", "hello 1")
    val content = "hello 2"
    Files.write(txt.toNioPath(), content.toByteArray(StandardCharsets.UTF_8))

    compileFile("project", txt)

    assertCopied("target/classes/text.css", cssContent)
    assertCopied("target/classes/text.txt", "hello 2")
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
