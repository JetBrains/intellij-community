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
package org.jetbrains.idea.maven.compiler;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerConfigurationImpl;
import com.intellij.maven.testFramework.MavenCompilingTestCase;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.compiler.options.ExcludeEntryDescription;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PsiTestUtil;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;

public class ResourceCopyingTest extends MavenCompilingTestCase {

  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();

    CompilerConfiguration.getInstance(myProject).addResourceFilePattern("!*.xxx");
    CompilerConfiguration.getInstance(myProject).addResourceFilePattern("!*.yyy");
    CompilerConfiguration.getInstance(myProject).addResourceFilePattern("!*.zzz");
  }

  @Test
  public void testBasic() throws Exception {
    createProjectSubFile("src/main/resources/dir/file.properties");

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """);
    compileModules("project");

    assertCopied("target/classes/dir/file.properties");
  }

  @Test
  public void testCustomResources() throws Exception {
    createProjectSubFile("res/dir1/file1.properties");
    createProjectSubFile("testRes/dir2/file2.properties");

    importProject("""
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
                    """);

    compileModules("project");

    assertCopied("target/classes/dir1/file1.properties");
    assertCopied("target/test-classes/dir2/file2.properties");
  }

  @Test
  public void testCopyWithFilteringIntoReadonlyTarget() throws Exception {
    final VirtualFile f = createProjectSubFile("res/dir1/file.properties", /*"Hello world"*/"Hello ${name}");
    final File srcFile = new File(f.getPath());

    importProject("""
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
                    """);

    compileModules("project");
    assertCopied("target/classes/dir1/file.properties", "Hello world");

    // make sure the output file is readonly
    final File outFile = new File(getProjectPath(), "target/classes/dir1/file.properties");
    outFile.setWritable(false);
    assertFalse(outFile.canWrite());

    FileUtil.writeToFile(srcFile, "Hello, ${name}");

    compileModules("project");
    assertCopied("target/classes/dir1/file.properties", "Hello, world");
  }

  @Test
  public void testCustomTargetPath() throws Exception {
    createProjectSubFile("res/dir/file.properties");

    importProject("""
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
                    """);

    compileModules("project");
    assertCopied("target/classes/foo/dir/file.properties");
  }

  @Test
  public void testResourcesPluginCustomTargetPath() throws Exception {
    createProjectSubFile("res/dir/file.properties");

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <artifactId>maven-resources-plugin</artifactId>
                          <version>2.6</version>
                          <configuration>
                            <outputDirectory>${basedir}/target/resourceOutput</outputDirectory>
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
                    """);

    compileModules("project");
    assertCopied("target/resourceOutput/foo/dir/file.properties");
  }

  @Test
  public void testResourcesPluginGoalAbsoluteCustomTargetPath() throws Exception {
    createProjectSubFile("src/test/resources/dir/file.properties");

    importProject("""
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
                               <outputDirectory>${project.build.testOutputDirectory}/custom</outputDirectory>
                             </configuration>
                           </execution>
                          </executions>
                        </plugin>
                      </plugins>
                    </build>
                    """);

    compileModules("project");
    assertCopied("target/test-classes/custom/dir/file.properties");
  }

  @Test
  public void testResourcesPluginGoalRelativeCustomTargetPath() throws Exception {
    createProjectSubFile("src/test/resources/dir/file.properties");

    importProject("""
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
                    """);

    compileModules("project");
    assertCopied("target/test-classes/custom/dir/file.properties");
  }

  @Test
  public void testAbsoluteCustomTargetPath() throws Exception {
    createProjectSubFile("res/foo/file.properties");

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <resources>
                        <resource>
                          <directory>res</directory>
                          <targetPath>${build.directory}/anotherDir</targetPath>
                        </resource>
                      </resources>
                    </build>
                    """);

    compileModules("project");
    assertCopied("target/anotherDir/foo/file.properties");
  }

  @Test
  public void testMavenSpecifiedPattern() throws Exception {
    createProjectSubFile("res/subdir/a.txt");
    createProjectSubFile("res/b.txt");

    importProject("""
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
                    """);

    compileModules("project");

    assertCopied("target/classes/subdir/a.txt");
    assertCopied("target/classes/b.txt");
  }

  @Test
  public void testMavenSpecifiedPatternEndSlash() throws Exception {
    createProjectSubFile("res/subdir/a.txt");
    createProjectSubFile("res/b.txt");

    importProject("""
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
                    """);

    compileModules("project");

    assertCopied("target/classes/subdir/a.txt");
    assertCopied("target/classes/b.txt");
  }

  @Test
  public void testIncludesAndExcludes() throws Exception {
    createProjectSubFile("res/dir/file.xxx");
    createProjectSubFile("res/dir/file.yyy");
    createProjectSubFile("res/file.xxx");
    createProjectSubFile("res/file.yyy");
    createProjectSubFile("res/file.zzz");

    importProject("""
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
                    """);

    compileModules("project");

    assertCopied("target/classes/dir/file.xxx");
    assertNotCopied("target/classes/dir/file.yyy");
    assertNotCopied("target/classes/file.xxx");
    assertCopied("target/classes/file.yyy");
    assertNotCopied("target/classes/file.zzz");
  }

  @Test
  public void testDoNotCopyIgnoredFiles() throws Exception {
    createProjectSubFile("res/CVS/file.properties");
    createProjectSubFile("res/.svn/file.properties");
    createProjectSubFile("res/zzz/file.properties");

    importProject("""
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
                    """);

    compileModules("project");

    assertNotCopied("target/classes/CVS");
    assertNotCopied("target/classes/.svn");
    assertCopied("target/classes/zzz/file.properties");
  }

  @Test
  public void testDeletingFilesThatWasCopiedAndThenDeleted() throws Exception {
    final VirtualFile file = createProjectSubFile("res/file.properties");

    importProject("""
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
                    """);

    compileModules("project");
    assertCopied("target/classes/file.properties");

    WriteCommandAction.writeCommandAction(myProject).run(() -> file.delete(this));

    compileModules("project");
    assertNotCopied("target/classes/file.properties");
  }

  @Test
  public void testDeletingFilesThatWasCopiedAndThenExcluded() throws Exception {
    createProjectSubFile("res/file.properties");

    importProject("""
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
                    """);

    compileModules("project");
    assertCopied("target/classes/file.properties");

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
                       """);
    importProject();

    compileModules("project");
    assertNotCopied("target/classes/file.properties");
  }

  @Test
  public void testDoNotCopyExcludedStandardResources() throws Exception {

    CompilerConfigurationImpl configuration = (CompilerConfigurationImpl)CompilerConfiguration.getInstance(myProject);
    configuration.addResourceFilePattern("*.zzz");

    createProjectSubFile("res/file.xxx");
    createProjectSubFile("res/file.zzz");

    importProject("""
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
                    """);

    compileModules("project");
    assertCopied("target/classes/file.xxx");
    assertNotCopied("target/classes/file.zzz");
  }

  @Test
  public void testDoNotDeleteFilesFromOtherModulesOutput() throws Exception {
    createProjectSubFile("m1/resources/file.xxx");
    createProjectSubFile("m2/resources/file.yyy");

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """);

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
                      """);

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
                      """);
    importProject();

    compileModules("project", "m1", "m2");
    assertCopied("m1/target/classes/file.xxx");
    assertCopied("m2/target/classes/file.yyy");

    compileModules("m1");
    assertCopied("m1/target/classes/file.xxx");
    assertCopied("m2/target/classes/file.yyy");

    compileModules("m2");
    assertCopied("m1/target/classes/file.xxx");
    assertCopied("m2/target/classes/file.yyy");

    compileModules("project");
    assertCopied("m1/target/classes/file.xxx");
    assertCopied("m2/target/classes/file.yyy");
  }

  @Test
  public void testDoNotDeleteFilesFromOtherModulesOutputWhenOutputIsTheSame() throws Exception {
    createProjectSubFile("resources/file.xxx");

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """);

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
                      """);

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
                      """);
    importProject();

    WriteCommandAction.writeCommandAction(myProject)
      .run(() -> setModulesOutput(myProjectRoot.createChildDirectory(this, "output"), "project", "m1", "m2"));


    compileModules("project", "m1", "m2");
    assertCopied("output/file.xxx");

    compileModules("m1");
    assertCopied("output/file.xxx");

    compileModules("m2");
    assertCopied("output/file.xxx");

    compileModules("project");
    assertCopied("output/file.xxx");
  }

  private void setModulesOutput(final VirtualFile output, final String... moduleNames) {
    WriteAction.run(() -> {
      for (String each : moduleNames) {
        PsiTestUtil.setCompilerOutputPath(getModule(each), output.getUrl(), false);
        PsiTestUtil.setCompilerOutputPath(getModule(each), output.getUrl(), true);
      }
    });
  }

  @Test
  public void testCopingNonMavenResources() throws Exception {
    if (ignore()) return;

    createProjectSubFile("src/main/resources/a.txt", "a");

    VirtualFile configDir = createProjectSubDir("src/config");
    createProjectSubFile("src/config/b.txt", "b");
    createProjectSubFile("src/config/JavaClass.java", "class JavaClass {}");
    createProjectSubFile("src/config/xxx.xxx", "xxx"); // *.xxx is excluded from resource coping, see setUpInWriteAction()

    final VirtualFile excludedDir = createProjectSubDir("src/excluded");
    createProjectSubFile("src/excluded/c.txt", "c");

    importProject("""
                    <groupId>test</groupId><artifactId>project</artifactId><version>1</version><properties>
                            <maven.compiler.source>11</maven.compiler.source>
                            <maven.compiler.target>11</maven.compiler.target>
                        </properties>""");

    Module module = ModuleManager.getInstance(myProject).findModuleByName("project");
    PsiTestUtil.addSourceRoot(module, configDir);
    PsiTestUtil.addSourceRoot(module, excludedDir);

    WriteCommandAction.writeCommandAction(myProject).run(() -> {
      CompilerConfiguration.getInstance(myProject).getExcludedEntriesConfiguration()
        .addExcludeEntryDescription(new ExcludeEntryDescription(excludedDir, true, false, getTestRootDisposable()));

      setModulesOutput(myProjectRoot.createChildDirectory(this, "output"), "project");
    });

    compileModules("project");

    assertCopied("output/a.txt");
    assertCopied("output/b.txt");

    assertNotCopied("output/JavaClass.java");
    assertCopied("output/xxx.xxx");
    assertNotCopied("output/c.txt");
  }

  @Test
  public void testCopyTestResourceWhenBuildingTestModule() throws Exception {
    Assume.assumeTrue(isWorkspaceImport());

    createProjectSubFile("src/test/resources/file.properties");

    importProject("""
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
                    </build>"""
    );

    assertModules("project", "project.main", "project.test");
    compileModules("project.test");
    assertCopied("target/test-classes/file.properties");
  }

  @Test
  public void testAnnotationPathsInCompoundModules() throws Exception {
    Assume.assumeTrue(isWorkspaceImport());

    createProjectSubFile("src/main/java/Main.java", "class Main {}");
    createProjectSubFile("src/test/java/Test.java", "class Test {}");

    importProject("""
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
                    </build>"""
    );

    assertModules("project", "project.main", "project.test");
    compileModules("project", "project.main", "project.test");

    assertCopied("target/generated-sources/annotations");
    assertCopied("target/generated-test-sources/test-annotations");
  }

}
