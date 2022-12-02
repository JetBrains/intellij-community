// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.idea.maven.importing;

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.LocalFileSystem;
import org.jetbrains.idea.maven.server.MavenServerManager;
import org.jetbrains.plugins.groovy.compiler.GreclipseIdeaCompilerSettings;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class GroovyImporterTest extends MavenMultiVersionImportingTestCase {
  private String repoPath;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    repoPath = new File(myDir, "repo").getPath();
    setRepositoryPath(repoPath);
  }

  @Test
  public void testConfiguringFacetWithoutLibrary() {
    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.codehaus.groovy.maven</groupId>
                          <artifactId>gmaven-plugin</artifactId>
                        </plugin>
                      </plugins>
                    </build>
                    """);

    assertModules("project");

    assertUnorderedElementsAreEqual(GroovyConfigUtils.getInstance().getSDKLibrariesByModule(getModule("project")));
  }

  @Test
  public void testConfiguringFacetWithLibrary() {
    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <dependencies>
                      <dependency>
                        <groupId>org.codehaus.groovy.maven.runtime</groupId>
                        <artifactId>gmaven-runtime-default</artifactId>
                        <version>1.0-rc-1</version>
                      </dependency>
                    </dependencies>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.codehaus.groovy.maven</groupId>
                          <artifactId>gmaven-plugin</artifactId>
                        </plugin>
                      </plugins>
                    </build>
                    """);

    assertModules("project");

    Library[] libraries = GroovyConfigUtils.getInstance().getSDKLibrariesByModule(getModule("project"));
    assertTrue("unexpected groovy libs configuration: " + libraries.length, libraries.length > 0);
    Library library = libraries[0];
    assertUnorderedPathsAreEqual(
      Arrays.asList(library.getUrls(OrderRootType.CLASSES)),
      Arrays.asList("jar://" + getRepositoryPath() + "/org/codehaus/groovy/groovy-all-minimal/1.5.6/groovy-all-minimal-1.5.6.jar!/"));
  }

  @Test
  public void testAddingGroovySpecificSources() {
    if (!supportsImportOfNonExistingFolders()) {
      createStdProjectFolders();
      createProjectSubDirs("src/main/groovy",
                           "src/test/groovy");
    }

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.codehaus.groovy.maven</groupId>
                          <artifactId>gmaven-plugin</artifactId>
                        </plugin>
                      </plugins>
                    </build>
                    """);

    assertModules("project");

    assertSources("project",
                  "src/main/groovy",
                  "src/main/java");
    assertResources("project", "src/main/resources");
    assertTestSources("project",
                      "src/test/groovy",
                      "src/test/java");
    assertTestResources("project", "src/test/resources");
  }

  @Test
  public void testAddingGroovySpecificSources2() {
    if (!supportsImportOfNonExistingFolders()) {
      createStdProjectFolders();
      createProjectSubDirs("src/main/groovy",
                           "src/test/groovy");
    }

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.codehaus.gmaven</groupId>
                          <artifactId>groovy-maven-plugin</artifactId>
                        </plugin>
                      </plugins>
                    </build>
                    """);

    assertModules("project");

    assertSources("project",
                  "src/main/groovy",
                  "src/main/java");
    assertResources("project", "src/main/resources");
    assertTestSources("project",
                      "src/test/groovy",
                      "src/test/java");
    assertTestResources("project", "src/test/resources");
  }

  @Test
  public void testAddingGroovySpecificSources3GmavenPlus() {
    if (!supportsImportOfNonExistingFolders()) {
      createStdProjectFolders();
      createProjectSubDirs("src/main/groovy",
                           "src/test/groovy");
    }

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.codehaus.gmavenplus</groupId>
                          <artifactId>gmavenplus-plugin</artifactId>
                        </plugin>
                      </plugins>
                    </build>
                    """);

    assertModules("project");

    assertSources("project",
                  "src/main/groovy",
                  "src/main/java");
    assertResources("project", "src/main/resources");
    assertTestSources("project",
                      "src/test/groovy",
                      "src/test/java");
    assertTestResources("project", "src/test/resources");
  }

  @Test
  public void testGroovyEclipsePlugin() throws IOException {

    File batchDir = new File(repoPath, "org/codehaus/groovy/groovy-eclipse-batch/2.1.3-01/");
    //noinspection ResultOfMethodCallIgnored
    batchDir.mkdirs();
    File batchJar = new File(batchDir, "groovy-eclipse-batch-2.1.3-01.jar");
    //noinspection ResultOfMethodCallIgnored
    if (!isNewImportingProcess) { // old import tests are not resolving anything
      batchJar.createNewFile();
    }

    if (!supportsImportOfNonExistingFolders()) {
      createStdProjectFolders();
      createProjectSubDirs("src/main/groovy",
                           "src/test/groovy");
    }

    importProject("""
                    <groupId>test</groupId><artifactId>project</artifactId><version>1</version><dependencies>
                      <dependency>
                        <groupId>org.codehaus.groovy</groupId>
                        <artifactId>groovy-all</artifactId>
                        <version>2.1.0</version>
                      </dependency>
                    </dependencies><build>
                      <pluginManagement>
                        <plugins>
                          <plugin>
                            <artifactId>maven-compiler-plugin</artifactId>
                            <configuration>
                              <compilerId>groovy-eclipse-compiler</compilerId>
                              <source>1.7</source>
                              <target>1.7</target>
                              <showWarnings>false</showWarnings>
                            </configuration>
                            <dependencies>
                              <dependency>
                                <groupId>org.codehaus.groovy</groupId>
                                <artifactId>groovy-eclipse-compiler</artifactId>
                                <version>2.8.0-01</version>
                              </dependency>
                              <dependency>
                                <groupId>org.codehaus.groovy</groupId>
                                <artifactId>groovy-eclipse-batch</artifactId>
                                <version>2.1.3-01</version>
                              </dependency>
                            </dependencies>
                          </plugin>
                          <plugin>
                            <groupId>org.codehaus.groovy</groupId>
                            <artifactId>groovy-eclipse-compiler</artifactId>
                            <version>2.8.0-01</version>
                            <extensions>true</extensions>
                          </plugin>
                        </plugins>
                      </pluginManagement>
                    </build>
                    """);

    assertModules("project");

    assertSources("project",
                  "src/main/groovy",
                  "src/main/java");
    assertResources("project", "src/main/resources");
    assertTestSources("project",
                      "src/test/groovy",
                      "src/test/java");
    assertTestResources("project", "src/test/resources");

    GreclipseIdeaCompilerSettings compilerSettings = myProject.getService(GreclipseIdeaCompilerSettings.class);
    assertEquals(LocalFileSystem.getInstance().findFileByIoFile(batchJar).getPath(), compilerSettings.getState().greclipsePath);
  }

  @Test
  public void testGroovyEclipsePluginWhenOnlyCompilerDependency() throws IOException {
    if (!supportsImportOfNonExistingFolders()) {
      createStdProjectFolders();
      createProjectSubDirs("src/main/groovy",
                           "src/test/groovy");
    }

    File batchDir = new File(repoPath, "org/codehaus/groovy/groovy-eclipse-batch/2.1.3-01/");
    //noinspection ResultOfMethodCallIgnored
    batchDir.mkdirs();
    File batchJar = new File(batchDir, "groovy-eclipse-batch-2.1.3-01.jar");
    //noinspection ResultOfMethodCallIgnored
    batchJar.createNewFile();


    importProject("""
                    <groupId>test</groupId><artifactId>project</artifactId><version>1</version><build>
                      <pluginManagement>
                        <plugins>
                          <plugin>
                            <artifactId>maven-compiler-plugin</artifactId>
                            <configuration>
                              <compilerId>groovy-eclipse-compiler</compilerId>
                              <source>1.7</source>
                              <target>1.7</target>
                              <showWarnings>false</showWarnings>
                            </configuration>
                            <dependencies>
                              <dependency>
                                <groupId>org.codehaus.groovy</groupId>
                                <artifactId>groovy-eclipse-compiler</artifactId>
                                <version>2.8.0-01</version>
                              </dependency>
                              <dependency>
                                <groupId>org.codehaus.groovy</groupId>
                                <artifactId>groovy-eclipse-batch</artifactId>
                                <version>2.1.3-01</version>
                              </dependency>
                            </dependencies>
                          </plugin>
                          <plugin>
                            <groupId>org.codehaus.groovy</groupId>
                            <artifactId>groovy-eclipse-compiler</artifactId>
                            <version>2.8.0-01</version>
                            <extensions>true</extensions>
                          </plugin>
                        </plugins>
                      </pluginManagement>
                    </build>
                    """);

    assertModules("project");

    assertSources("project",
                  "src/main/groovy",
                  "src/main/java");
    assertResources("project", "src/main/resources");
    assertTestSources("project",
                      "src/test/groovy",
                      "src/test/java");
    assertTestResources("project", "src/test/resources");

    GreclipseIdeaCompilerSettings compilerSettings = myProject.getService(GreclipseIdeaCompilerSettings.class);
    assertEquals(LocalFileSystem.getInstance().findFileByIoFile(batchJar).getPath(), compilerSettings.getState().greclipsePath);
  }

  @Test
  public void testAddingCustomGroovySpecificSources() {
    if (!supportsImportOfNonExistingFolders()) {
      createStdProjectFolders();
      createProjectSubDirs("src/main/groovy",
                           "src/foo1",
                           "src/foo2",
                           "src/test/groovy",
                           "src/test-foo1",
                           "src/test-foo2");
    }

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.codehaus.groovy.maven</groupId>
                          <artifactId>gmaven-plugin</artifactId>
                          <executions>
                            <execution>
                              <id>one</id>
                              <goals>
                                <goal>compile</goal>
                              </goals>
                              <configuration>
                                <sources>
                                  <fileset>
                                    <directory>${pom.basedir}/src/foo1</directory>
                                  </fileset>
                                  <fileset>
                                    <directory>${pom.basedir}/src/foo2</directory>
                                  </fileset>
                                </sources>
                              </configuration>
                            </execution>
                            <execution>
                              <id>two</id>
                              <goals>
                                <goal>testCompile</goal>
                              </goals>
                              <configuration>
                                <sources>
                                  <fileset>
                                    <directory>${pom.basedir}/src/test-foo1</directory>
                                  </fileset>
                                  <fileset>
                                    <directory>${pom.basedir}/src/test-foo2</directory>
                                  </fileset>
                                </sources>
                              </configuration>
                            </execution>
                          </executions>
                        </plugin>
                      </plugins>
                    </build>
                    """);

    assertModules("project");

    assertSources("project",
                  "src/foo1",
                  "src/foo2",
                  "src/main/java");
    assertResources("project", "src/main/resources");
    assertTestSources("project",
                      "src/test-foo1",
                      "src/test-foo2",
                      "src/test/java");
    assertTestResources("project", "src/test/resources");
  }

  @Test
  public void testAddingCustomGroovySpecificSources2GmavenPlus() {
    if (!supportsImportOfNonExistingFolders()) {
      createStdProjectFolders();
      createProjectSubDirs("src/main/groovy",
                           "src/foo1",
                           "src/foo2",
                           "src/test/groovy",
                           "src/test-foo1",
                           "src/test-foo2");
    }

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.codehaus.gmavenplus</groupId>
                          <artifactId>gmavenplus-plugin</artifactId>
                          <executions>
                            <execution>
                              <id>one</id>
                              <goals>
                                <goal>compile</goal>
                              </goals>
                              <configuration>
                                <sources>
                                  <fileset>
                                    <directory>${pom.basedir}/src/foo1</directory>
                                  </fileset>
                                  <fileset>
                                    <directory>${pom.basedir}/src/foo2</directory>
                                  </fileset>
                                </sources>
                              </configuration>
                            </execution>
                            <execution>
                              <id>two</id>
                              <goals>
                                <goal>testCompile</goal>
                              </goals>
                              <configuration>
                                <sources>
                                  <fileset>
                                    <directory>${pom.basedir}/src/test-foo1</directory>
                                  </fileset>
                                  <fileset>
                                    <directory>${pom.basedir}/src/test-foo2</directory>
                                  </fileset>
                                </sources>
                              </configuration>
                            </execution>
                          </executions>
                        </plugin>
                      </plugins>
                    </build>
                    """);

    assertModules("project");

    assertSources("project",
                  "src/foo1",
                  "src/foo2",
                  "src/main/java");
    assertResources("project", "src/main/resources");
    assertTestSources("project",
                      "src/test-foo1",
                      "src/test-foo2",
                      "src/test/java");
    assertTestResources("project", "src/test/resources");
  }

  @Test
  public void testAddingCustomGroovySpecificSourcesByRelativePath() {
    if (!supportsImportOfNonExistingFolders()) {
      createProjectSubDirs("src/foo",
                           "src/test-foo");
    }

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.codehaus.groovy.maven</groupId>
                          <artifactId>gmaven-plugin</artifactId>
                          <executions>
                            <execution>
                              <id>one</id>
                              <goals>
                                <goal>compile</goal>
                              </goals>
                              <configuration>
                                <sources>
                                  <fileset>
                                    <directory>src/foo</directory>
                                  </fileset>
                                </sources>
                              </configuration>
                            </execution>
                            <execution>
                              <id>two</id>
                              <goals>
                                <goal>testCompile</goal>
                              </goals>
                              <configuration>
                                <sources>
                                  <fileset>
                                    <directory>src/test-foo</directory>
                                  </fileset>
                                </sources>
                              </configuration>
                            </execution>
                          </executions>
                        </plugin>
                      </plugins>
                    </build>
                    """);

    assertModules("project");

    if (supportsImportOfNonExistingFolders()) {
      assertSources("project", "src/foo", "src/main/java");
      assertTestSources("project", "src/test-foo", "src/test/java");
    }
    else {
      assertSources("project", "src/foo");
      assertTestSources("project", "src/test-foo");
    }
  }

  @Test
  public void testDoNotAddGroovySpecificGeneratedSources() {
    if (!supportsImportOfNonExistingFolders()) {
      createStdProjectFolders();
    }
    createProjectSubDirs("target/generated-sources/xxx/yyy",
                         "target/generated-sources/groovy-stubs/main/foo",
                         "target/generated-sources/groovy-stubs/test/bar");

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.codehaus.groovy.maven</groupId>
                          <artifactId>gmaven-plugin</artifactId>
                          <executions>
                            <execution>
                              <goals>
                                <goal>generateStubs</goal>
                                <goal>compile</goal>
                                <goal>generateTestStubs</goal>
                                <goal>testCompile</goal>
                              </goals>
                            </execution>
                          </executions>
                        </plugin>
                      </plugins>
                    </build>
                    """);

    assertModules("project");

    if (supportsImportOfNonExistingFolders()) {
      assertSources("project",
                    "src/main/groovy",
                    "src/main/java",
                    "target/generated-sources/xxx");
      assertTestSources("project", "src/test/groovy", "src/test/java");
    }
    else {
      assertSources("project",
                    "src/main/java",
                    "target/generated-sources/xxx");
      assertTestSources("project",
                        "src/test/java");
    }
    assertResources("project", "src/main/resources");
    assertTestResources("project", "src/test/resources");

    assertExcludes("project", "target");
  }

  @Test
  public void testDoNotAddCustomGroovySpecificGeneratedSources() {
    if (!supportsImportOfNonExistingFolders()) {
      createStdProjectFolders();
    }
    createProjectSubDirs("target/generated-sources/xxx/yyy",
                         "target/generated-sources/foo/aaa",
                         "target/generated-sources/bar/bbb");

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.codehaus.groovy.maven</groupId>
                          <artifactId>gmaven-plugin</artifactId>
                          <executions>
                            <execution>
                              <id>one</id>
                              <goals>
                                <goal>generateStubs</goal>
                              </goals>
                              <configuration>
                                <outputDirectory>${project.build.directory}/generated-sources/foo</outputDirectory>
                              </configuration>
                            </execution>
                            <execution>
                              <id>two</id>
                              <goals>
                                <goal>generateTestStubs</goal>
                              </goals>
                              <configuration>
                                <outputDirectory>${project.build.directory}/generated-sources/bar</outputDirectory>
                              </configuration>
                            </execution>
                          </executions>
                        </plugin>
                      </plugins>
                    </build>
                    """);

    assertModules("project");

    if (supportsImportOfNonExistingFolders()) {
      assertSources("project",
                    "src/main/groovy",
                    "src/main/java",
                    "target/generated-sources/xxx");
      assertTestSources("project", "src/test/groovy", "src/test/java");
    }
    else {
      assertSources("project",
                    "src/main/java",
                    "target/generated-sources/xxx");
      assertTestSources("project",
                        "src/test/java");
    }
    assertResources("project", "src/main/resources");
    assertTestResources("project", "src/test/resources");

    assertExcludes("project", "target");
  }

  @Test
  public void testDoNotAddCustomGroovySpecificGeneratedSourcesByRelativePath() {
    createProjectSubDirs("target/generated-sources/xxx/yyy",
                         "target/generated-sources/foo/aaa",
                         "target/generated-sources/bar/bbb");

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.codehaus.groovy.maven</groupId>
                          <artifactId>gmaven-plugin</artifactId>
                          <executions>
                            <execution>
                              <id>one</id>
                              <goals>
                                <goal>generateStubs</goal>
                              </goals>
                              <configuration>
                                <outputDirectory>target/generated-sources/foo</outputDirectory>
                              </configuration>
                            </execution>
                            <execution>
                              <id>two</id>
                              <goals>
                                <goal>generateTestStubs</goal>
                              </goals>
                              <configuration>
                                <outputDirectory>target/generated-sources/bar</outputDirectory>
                              </configuration>
                            </execution>
                          </executions>
                        </plugin>
                      </plugins>
                    </build>
                    """);

    assertModules("project");

    if (supportsImportOfNonExistingFolders()) {
      assertSources("project",
                    "src/main/groovy",
                    "src/main/java",
                    "target/generated-sources/xxx");
      assertTestSources("project",
                        "src/test/groovy",
                        "src/test/java");
    }
    else {
      assertSources("project",
                    "target/generated-sources/xxx");
      assertTestSources("project");
    }

    assertExcludes("project", "target");
  }

  @Test
  public void testUpdatingGroovySpecificGeneratedSourcesOnFoldersUpdate() {
    try {
      importProject("""
                      <groupId>test</groupId>
                      <artifactId>project</artifactId>
                      <version>1</version>
                      <build>
                        <plugins>
                          <plugin>
                            <groupId>org.codehaus.groovy.maven</groupId>
                            <artifactId>gmaven-plugin</artifactId>
                            <executions>
                              <execution>
                                <goals>
                                  <goal>generateStubs</goal>
                                  <goal>generateTestStubs</goal>
                                </goals>
                              </execution>
                            </executions>
                          </plugin>
                        </plugins>
                      </build>
                      """);

      ApplicationManager.getApplication().runWriteAction(() -> {
        MavenRootModelAdapter a = new MavenRootModelAdapter(new MavenRootModelAdapterLegacyImpl(getProjectsTree().findProject(myProjectPom),
                                                                                                getModule("project"),
                                                                                                new ModifiableModelsProviderProxyWrapper(
                                                                                                  myProject)));
        a.unregisterAll(getProjectPath() + "/target", true, true);
        a.getRootModel().commit();
      });

      if (supportsImportOfNonExistingFolders()) {
        assertSources("project", "src/main/groovy", "src/main/java");
        assertTestSources("project", "src/test/groovy", "src/test/java");
      }
      else {
        assertSources("project");
        assertTestSources("project");
      }
      assertExcludes("project");

      if (!supportsImportOfNonExistingFolders()) {
        createProjectSubDirs("src/main/groovy",
                             "src/test/groovy");
      }

      createProjectSubDirs("target/generated-sources/xxx/yyy",
                           "target/generated-sources/groovy-stubs/main/foo",
                           "target/generated-sources/groovy-stubs/test/bar");

      resolveFoldersAndImport();

      if (supportsImportOfNonExistingFolders()) {
        assertSources("project",
                      "src/main/groovy",
                      "src/main/java",
                      "target/generated-sources/xxx");
        assertTestSources("project",
                          "src/test/groovy",
                          "src/test/java");
      }
      else {
        assertSources("project",
                      "src/main/groovy",
                      "target/generated-sources/xxx");
        assertTestSources("project",
                          "src/test/groovy");
      }

      assertExcludes("project", "target");
    }
    finally {
      // do not lock files by maven process
      MavenServerManager.getInstance().shutdown(true);
    }
  }

  @Test
  public void testDoNotAddGroovySpecificGeneratedSourcesForGMaven_1_2() {
    if (!supportsImportOfNonExistingFolders()) {
      createStdProjectFolders();
    }

    createProjectSubDirs("target/generated-sources/xxx/yyy",
                         "target/generated-sources/groovy-stubs/main/foo",
                         "target/generated-sources/groovy-stubs/test/bar");

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.codehaus.gmaven</groupId>
                          <artifactId>gmaven-plugin</artifactId>
                          <version>1.2</version>
                          <executions>
                            <execution>
                              <goals>
                                <goal>generateStubs</goal>
                                <goal>compile</goal>
                                <goal>generateTestStubs</goal>
                                <goal>testCompile</goal>
                              </goals>
                            </execution>
                          </executions>
                        </plugin>
                      </plugins>
                    </build>
                    """);

    assertModules("project");

    if (supportsImportOfNonExistingFolders()) {
      assertSources("project",
                    "src/main/groovy",
                    "src/main/java",
                    "target/generated-sources/xxx");
      assertResources("project", "src/main/resources");
      assertTestSources("project", "src/test/groovy", "src/test/java");
      assertTestResources("project", "src/test/resources");
    }
    else {
      assertSources("project",
                    "src/main/java",
                    "target/generated-sources/xxx");
      assertResources("project", "src/main/resources");
      assertTestSources("project", "src/test/java");
      assertTestResources("project", "src/test/resources");
    }

    assertExcludes("project", "target");
  }
}
