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
package org.jetbrains.idea.maven.importing;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.MavenCustomRepositoryHelper;
import org.jetbrains.idea.maven.MavenImportingTestCase;
import org.jetbrains.idea.maven.project.MavenImportingSettings;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.server.MavenServerManager;
import org.jetbrains.idea.maven.utils.Path;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import java.io.File;
import java.io.IOException;

public class FoldersImportingTest extends MavenImportingTestCase {

  //public static Test suite() throws ClassNotFoundException {
  //  return new TestSuite(
  //    Class.forName("_FirstInSuiteTest"),
  //    FoldersImportingTest.class,
  //    Class.forName("_LastInSuiteTest")
  //  );
  //}

  public void testSimpleProjectStructure() {
    createStdProjectFolders();

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertModules("project");
    assertContentRoots("project", getProjectPath());

    assertSources("project", "src/main/java");
    assertResources("project", "src/main/resources");
    assertTestSources("project", "src/test/java");
    assertTestResources("project", "src/test/resources");
  }

  public void testInvalidProjectHasContentRoot() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1");

    assertModules("project");
    assertContentRoots("project", getProjectPath());
  }

  public void testDoNotResetFoldersAfterResolveIfProjectIsInvalid() {
    createStdProjectFolders();

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <extensions>" +
                  "    <extension>" +
                  "      <groupId>xxx</groupId>" +
                  "      <artifactId>xxx</artifactId>" +
                  "      <version>xxx</version>" +
                  "    </extension>" +
                  "  </extensions>" +
                  "</build>");

    assertModules("project");
    assertSources("project", "src/main/java");
    assertResources("project", "src/main/resources");
    assertTestSources("project", "src/test/java");
    assertTestResources("project", "src/test/resources");
  }

  public void testDoesNotResetUserFolders() {
    final VirtualFile dir1 = createProjectSubDir("userSourceFolder");
    final VirtualFile dir2 = createProjectSubDir("userExcludedFolder");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    ApplicationManager.getApplication().runWriteAction(() -> {
      MavenRootModelAdapter adapter = new MavenRootModelAdapter(myProjectsTree.findProject(myProjectPom),
                                                                getModule("project"),
                                                                new IdeModifiableModelsProviderImpl(myProject));
      adapter.addSourceFolder(dir1.getPath(), JavaSourceRootType.SOURCE);
      adapter.addExcludedFolder(dir2.getPath());
      adapter.getRootModel().commit();
    });


    assertSources("project", "userSourceFolder");
    assertExcludes("project", "target", "userExcludedFolder");

    importProject();

    assertSources("project", "userSourceFolder");
    assertExcludes("project", "target", "userExcludedFolder");

    resolveFoldersAndImport();

    assertSources("project", "userSourceFolder");
    assertExcludes("project", "target", "userExcludedFolder");
  }

  public void testClearParentAndSubFoldersOfNewlyImportedFolders() {
    createProjectSubDirs("src/main/java", "src/main/resources");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertSources("project", "src/main/java");
    assertResources("project", "src/main/resources");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <sourceDirectory>src</sourceDirectory>" +
                  "</build>");

    assertSources("project", "src");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertSources("project", "src/main/java");
    assertResources("project", "src/main/resources");
  }

  public void testSourceFoldersOnReimport() {
    createProjectSubDirs("src1", "src2");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <sourceDirectory>src1</sourceDirectory>" +
                  "</build>");

    assertSources("project", "src1");

    getMavenImporterSettings().setKeepSourceFolders(false);
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <sourceDirectory>src2</sourceDirectory>" +
                  "</build>");

    assertSources("project", "src2");

    getMavenImporterSettings().setKeepSourceFolders(true);
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <sourceDirectory>src1</sourceDirectory>" +
                  "</build>");

    assertSources("project", "src2", "src1");
  }

  public void testCustomSourceFolders() {
    createStdProjectFolders();
    createProjectSubDirs("src", "test", "res1", "res2", "testRes1", "testRes2");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <sourceDirectory>src</sourceDirectory>" +
                  "  <testSourceDirectory>test</testSourceDirectory>" +
                  "  <resources>" +
                  "    <resource><directory>res1</directory></resource>" +
                  "    <resource><directory>res2</directory></resource>" +
                  "  </resources>" +
                  "  <testResources>" +
                  "    <testResource><directory>testRes1</directory></testResource>" +
                  "    <testResource><directory>testRes2</directory></testResource>" +
                  "  </testResources>" +
                  "</build>");

    assertModules("project");
    assertContentRoots("project", getProjectPath());

    assertSources("project", "src");
    assertResources("project", "res1", "res2");
    assertTestSources("project", "test");
    assertTestResources("project", "testRes1", "testRes2");
  }

  public void testCustomSourceFoldersOutsideOfContentRoot() {
    createStdProjectFolders();
    createProjectSubDirs("m",
                         "src",
                         "test",
                         "res",
                         "testRes");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m</module>" +
                     "</modules>");

    createModulePom("m", "<groupId>test</groupId>" +
                         "<artifactId>m</artifactId>" +
                         "<version>1</version>" +

                         "<build>" +
                         "  <sourceDirectory>../src</sourceDirectory>" +
                         "  <testSourceDirectory>../test</testSourceDirectory>" +
                         "  <resources>" +
                         "    <resource><directory>../res</directory></resource>" +
                         "  </resources>" +
                         "  <testResources>" +
                         "    <testResource><directory>../testRes</directory></testResource>" +
                         "  </testResources>" +
                         "</build>");
    importProject();
    assertModules("project", "m");
    assertContentRoots("m",
                       getProjectPath() + "/m",
                       getProjectPath() + "/src",
                       getProjectPath() + "/test",
                       getProjectPath() + "/res",
                       getProjectPath() + "/testRes");
  }

  public void testPluginSources() {
    createStdProjectFolders();
    createProjectSubDirs("src1", "src2");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <plugins>" +
                  "    <plugin>" +
                  "      <groupId>org.codehaus.mojo</groupId>" +
                  "      <artifactId>build-helper-maven-plugin</artifactId>" +
                  "      <version>1.3</version>" +
                  "      <executions>" +
                  "        <execution>" +
                  "          <id>someId</id>" +
                  "          <phase>generate-sources</phase>" +
                  "          <goals>" +
                  "            <goal>add-source</goal>" +
                  "          </goals>" +
                  "          <configuration>" +
                  "            <sources>" +
                  "              <source>${basedir}/src1</source>" +
                  "              <source>${basedir}/src2</source>" +
                  "            </sources>" +
                  "          </configuration>" +
                  "        </execution>" +
                  "      </executions>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");
    resolveFoldersAndImport();
    assertModules("project");

    assertSources("project", "src/main/java", "src1", "src2");
    assertResources("project", "src/main/resources");
  }

  public void testPluginSourceDuringGenerateResourcesPhase() {
    createStdProjectFolders();
    createProjectSubDirs("extraResources");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <plugins>" +
                  "    <plugin>" +
                  "      <groupId>org.codehaus.mojo</groupId>" +
                  "      <artifactId>build-helper-maven-plugin</artifactId>" +
                  "      <version>1.3</version>" +
                  "      <executions>" +
                  "        <execution>" +
                  "          <id>someId</id>" +
                  "          <phase>generate-resources</phase>" +
                  "          <goals>" +
                  "            <goal>add-source</goal>" +
                  "          </goals>" +
                  "          <configuration>" +
                  "            <sources>" +
                  "              <source>${basedir}/extraResources</source>" +
                  "            </sources>" +
                  "          </configuration>" +
                  "        </execution>" +
                  "      </executions>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");
    resolveFoldersAndImport();
    assertModules("project");

    assertSources("project", "extraResources", "src/main/java");
    assertResources("project", "src/main/resources");
  }

  public void testPluginTestSourcesDuringGenerateTestResourcesPhase() {
    createStdProjectFolders();
    createProjectSubDirs("extraTestResources");

    getMavenImporterSettings().setUpdateFoldersOnImportPhase("generate-test-resources");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <plugins>" +
                  "    <plugin>" +
                  "      <groupId>org.codehaus.mojo</groupId>" +
                  "      <artifactId>build-helper-maven-plugin</artifactId>" +
                  "      <version>1.3</version>" +
                  "      <executions>" +
                  "        <execution>" +
                  "          <id>someId</id>" +
                  "          <phase>generate-test-resources</phase>" +
                  "          <goals>" +
                  "            <goal>add-test-source</goal>" +
                  "          </goals>" +
                  "          <configuration>" +
                  "            <sources>" +
                  "              <source>${basedir}/extraTestResources</source>" +
                  "            </sources>" +
                  "          </configuration>" +
                  "        </execution>" +
                  "      </executions>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");
    resolveFoldersAndImport();
    assertModules("project");

    assertTestSources("project", "extraTestResources", "src/test/java");
    assertTestResources("project", "src/test/resources");
  }

  public void testPluginSourcesWithRelativePath() {
    createStdProjectFolders();
    createProjectSubDirs("relativePath");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <plugins>" +
                  "    <plugin>" +
                  "      <groupId>org.codehaus.mojo</groupId>" +
                  "      <artifactId>build-helper-maven-plugin</artifactId>" +
                  "      <version>1.3</version>" +
                  "      <executions>" +
                  "        <execution>" +
                  "          <id>someId</id>" +
                  "          <phase>generate-sources</phase>" +
                  "          <goals>" +
                  "            <goal>add-source</goal>" +
                  "          </goals>" +
                  "          <configuration>" +
                  "            <sources>" +
                  "              <source>relativePath</source>" +
                  "            </sources>" +
                  "          </configuration>" +
                  "        </execution>" +
                  "      </executions>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");
    resolveFoldersAndImport();
    assertModules("project");

    assertSources("project", "relativePath", "src/main/java");
    assertResources("project", "src/main/resources");
  }

  public void testPluginSourcesWithVariables() {
    createStdProjectFolders();
    createProjectSubDirs("target/src");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <plugins>" +
                  "    <plugin>" +
                  "      <groupId>org.codehaus.mojo</groupId>" +
                  "      <artifactId>build-helper-maven-plugin</artifactId>" +
                  "      <version>1.3</version>" +
                  "      <executions>" +
                  "        <execution>" +
                  "          <id>someId</id>" +
                  "          <phase>generate-sources</phase>" +
                  "          <goals>" +
                  "            <goal>add-source</goal>" +
                  "          </goals>" +
                  "          <configuration>" +
                  "            <sources>" +
                  "              <source>${project.build.directory}/src</source>" +
                  "            </sources>" +
                  "          </configuration>" +
                  "        </execution>" +
                  "      </executions>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");
    resolveFoldersAndImport();
    assertModules("project");

    assertSources("project", "src/main/java", "target/src");
    assertResources("project", "src/main/resources");
  }

  public void testPluginSourcesWithIntermoduleDependency() {
    createProjectSubDirs("m1/src/main/java",
                         "m1/src/main/resources",
                         "m1/src/foo");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "</modules>");

    createModulePom("m1",
                    "<groupId>test</groupId>" +
                    "<artifactId>m1</artifactId>" +
                    "<version>1</version>" +

                    "<dependencies>" +
                    "  <dependency>" +
                    "    <groupId>test</groupId>" +
                    "    <artifactId>m2</artifactId>" +
                    "    <version>1</version>" +
                    "  </dependency>" +
                    "</dependencies>" +

                    "<build>" +
                    "  <plugins>" +
                    "    <plugin>" +
                    "      <groupId>org.codehaus.mojo</groupId>" +
                    "      <artifactId>build-helper-maven-plugin</artifactId>" +
                    "      <version>1.3</version>" +
                    "      <executions>" +
                    "        <execution>" +
                    "          <id>someId</id>" +
                    "          <phase>generate-sources</phase>" +
                    "          <goals>" +
                    "            <goal>add-source</goal>" +
                    "          </goals>" +
                    "          <configuration>" +
                    "            <sources>" +
                    "              <source>src/foo</source>" +
                    "            </sources>" +
                    "          </configuration>" +
                    "        </execution>" +
                    "      </executions>" +
                    "    </plugin>" +
                    "  </plugins>" +
                    "</build>");

    createModulePom("m2",
                    "<groupId>test</groupId>" +
                    "<artifactId>m2</artifactId>" +
                    "<version>1</version>");
    importProject();
    assertModules("project", "m1", "m2");

    resolveFoldersAndImport();
    assertSources("m1", "src/foo", "src/main/java");
    assertResources("m1", "src/main/resources");
  }

  public void testDownloadingNecessaryPlugins() throws Exception {
    try {
      MavenCustomRepositoryHelper helper = new MavenCustomRepositoryHelper(myDir, "local1");
      setRepositoryPath(helper.getTestDataPath("local1"));

      File pluginFile = new File(getRepositoryPath(),
                                 "org/codehaus/mojo/build-helper-maven-plugin/1.2/build-helper-maven-plugin-1.2.jar");
      assertFalse(pluginFile.exists());

      importProject("<groupId>test</groupId>" +
                    "<artifactId>project</artifactId>" +
                    "<version>1</version>" +

                    "<build>" +
                    "  <plugins>" +
                    "    <plugin>" +
                    "      <groupId>org.codehaus.mojo</groupId>" +
                    "      <artifactId>build-helper-maven-plugin</artifactId>" +
                    "      <version>1.2</version>" +
                    "      <executions>" +
                    "        <execution>" +
                    "          <id>someId</id>" +
                    "          <phase>generate-sources</phase>" +
                    "          <goals>" +
                    "            <goal>add-source</goal>" +
                    "          </goals>" +
                    "          <configuration>" +
                    "            <sources>" +
                    "              <source>src</source>" +
                    "            </sources>" +
                    "          </configuration>" +
                    "        </execution>" +
                    "      </executions>" +
                    "    </plugin>" +
                    "  </plugins>" +
                    "</build>");
      resolveDependenciesAndImport();
      resolveFoldersAndImport();

      assertTrue(pluginFile.exists());
    }
    finally {
      // do not lock files by maven process
      MavenServerManager.getInstance().shutdown(true);
    }
  }

  public void testAddingExistingGeneratedSources() throws Exception {
    createStdProjectFolders();

    createProjectSubFile("target/generated-sources/src1/com/A.java", "package com; class A {}");
    createProjectSubFile("target/generated-sources/src2/com/B.java", "package com; class B {}");
    createProjectSubFile("target/generated-test-sources/test1/com/test/A.java", "package com.test; class A {}");
    createProjectSubFile("target/generated-test-sources/test2/com/test/B.java", "package com.test; class B {}");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertSources("project",
                  "src/main/java",
                  "target/generated-sources/src1",
                  "target/generated-sources/src2");
    assertResources("project", "src/main/resources");

    assertTestSources("project",
                      "src/test/java",
                      "target/generated-test-sources/test1",
                      "target/generated-test-sources/test2");
    assertTestResources("project", "src/test/resources");
  }

  public void testAddingExistingGeneratedSources2() throws Exception {
    createStdProjectFolders();

    createProjectSubFile("target/generated-sources/com/A.java", "package com; class A {}");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertSources("project",
                  "src/main/java",
                  "target/generated-sources");
    assertResources("project", "src/main/resources");
  }

  public void testAddingExistingGeneratedSources3() throws Exception {
    createStdProjectFolders();

    MavenProjectsManager.getInstance(myProject).getImportingSettings().setGeneratedSourcesFolder(
      MavenImportingSettings.GeneratedSourcesFolder.SUBFOLDER);

    createProjectSubFile("target/generated-sources/com/A.java", "package com; class A {}");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertSources("project",
                  "src/main/java",
                  "target/generated-sources/com");
    assertResources("project", "src/main/resources");
  }

  public void testOverrideAnnotationSources() throws Exception {
    createStdProjectFolders();

    MavenProjectsManager.getInstance(myProject).getImportingSettings().setGeneratedSourcesFolder(
      MavenImportingSettings.GeneratedSourcesFolder.GENERATED_SOURCE_FOLDER);

    createProjectSubFile("target/generated-sources/com/A.java", "package com; class A {}");
    createProjectSubFile("target/generated-sources/annotations/com/B.java", "package com; class B {}");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertSources("project",
                  "src/main/java",
                  "target/generated-sources");
    assertResources("project", "src/main/resources");
  }

  public void testIgnoreGeneratedSources() throws Exception {
    createStdProjectFolders();

    MavenProjectsManager.getInstance(myProject).getImportingSettings().setGeneratedSourcesFolder(
      MavenImportingSettings.GeneratedSourcesFolder.IGNORE);

    createProjectSubFile("target/generated-sources/annotations/A.java", "package com; class A {}");
    createProjectSubFile("target/generated-sources/annotations/com/B.java", "package com; class B {}");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertSources("project", "src/main/java");
    assertResources("project", "src/main/resources");
  }


  public void testAddingExistingGeneratedSources4() throws Exception {
    createStdProjectFolders();

    createProjectSubFile("target/generated-sources/A1/B1/com/A1.java", "package com; class A1 {}");
    createProjectSubFile("target/generated-sources/A1/B2/com/A2.java", "package com; class A2 {}");
    createProjectSubFile("target/generated-sources/A2/com/A3.java", "package com; class A3 {}");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertSources("project",
                  "src/main/java",
                  "target/generated-sources/A1/B1",
                  "target/generated-sources/A1/B2",
                  "target/generated-sources/A2");
    assertResources("project", "src/main/resources");
  }

  public void testAddingExistingGeneratedSources5() throws Exception {
    createStdProjectFolders();

    createProjectSubFile("target/generated-sources/A1/B1/com/A1.java", "package com; class A1 {}");
    createProjectSubFile("target/generated-sources/A2.java", "class A2 {}");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertSources("project",
                  "src/main/java",
                  "target/generated-sources");
    assertResources("project", "src/main/resources");
  }


  public void testAddingExistingGeneratedSourcesWithCustomTargetDir() throws Exception {
    createStdProjectFolders();
    createProjectSubDirsWithFile("targetCustom/generated-sources/src",
                                 "targetCustom/generated-test-sources/test");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <directory>targetCustom</directory>" +
                  "</build>");

    assertSources("project",
                  "src/main/java",
                  "targetCustom/generated-sources/src");
    assertResources("project", "src/main/resources");

    assertTestSources("project",
                      "src/test/java",
                      "targetCustom/generated-test-sources/test");
    assertTestResources("project", "src/test/resources");
  }

  public void testDoesNotAddAlreadyRegisteredSourcesUnderGeneratedDir() {
    createStdProjectFolders();
    createProjectSubDirs("target/generated-sources/main/src",
                         "target/generated-test-sources/test/src");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <plugins>" +
                  "    <plugin>" +
                  "      <groupId>org.codehaus.mojo</groupId>" +
                  "      <artifactId>build-helper-maven-plugin</artifactId>" +
                  "      <version>1.3</version>" +
                  "      <executions>" +
                  "        <execution>" +
                  "          <id>id1</id>" +
                  "          <phase>generate-sources</phase>" +
                  "          <goals>" +
                  "            <goal>add-source</goal>" +
                  "          </goals>" +
                  "          <configuration>" +
                  "            <sources>" +
                  "              <source>target/generated-sources/main/src</source>" +
                  "            </sources>" +
                  "          </configuration>" +
                  "        </execution>" +
                  "        <execution>" +
                  "          <id>id2</id>" +
                  "          <phase>generate-sources</phase>" +
                  "          <goals>" +
                  "            <goal>add-test-source</goal>" +
                  "          </goals>" +
                  "          <configuration>" +
                  "            <sources>" +
                  "              <source>target/generated-test-sources/test/src</source>" +
                  "            </sources>" +
                  "          </configuration>" +
                  "        </execution>" +
                  "      </executions>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");
    resolveFoldersAndImport();

    assertSources("project",
                  "src/main/java",
                  "target/generated-sources/main/src");
    assertResources("project", "src/main/resources");

    assertTestSources("project",
                      "src/test/java",
                      "target/generated-test-sources/test/src");
    assertTestResources("project", "src/test/resources");
  }

  public void testIgnoringFilesRightUnderGeneratedSources() throws Exception {
    createStdProjectFolders();
    createProjectSubFile("target/generated-sources/f.txt");
    createProjectSubFile("target/generated-test-sources/f.txt");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertSources("project", "src/main/java");
    assertResources("project", "src/main/resources");
    assertTestSources("project", "src/test/java");
    assertTestResources("project", "src/test/resources");
  }

  public void testExcludingOutputDirectories() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");
    assertModules("project");

    assertExcludes("project", "target");
    assertModuleOutput("project",
                       getProjectPath() + "/target/classes",
                       getProjectPath() + "/target/test-classes");
  }

  public void testExcludingOutputDirectoriesIfProjectOutputIsUsed() {
    getMavenImporterSettings().setUseMavenOutput(false);

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <directory>foo</directory>" +
                  "</build>");
    assertModules("project");

    assertExcludes("project", "foo");
    assertProjectOutput("project");
  }

  public void testExcludingCustomOutputDirectories() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <directory>targetCustom</directory>" +
                  "  <outputDirectory>outputCustom</outputDirectory>" +
                  "  <testOutputDirectory>testCustom</testOutputDirectory>" +
                  "</build>");

    assertModules("project");

    assertExcludes("project",
                   "outputCustom",
                   "targetCustom",
                   "testCustom");
    assertModuleOutput("project",
                       getProjectPath() + "/outputCustom",
                       getProjectPath() + "/testCustom");
  }

  public void testExcludingCustomOutputUnderTargetUsingStandardVariable() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <outputDirectory>${project.build.directory}/outputCustom</outputDirectory>" +
                  "  <testOutputDirectory>${project.build.directory}/testCustom</testOutputDirectory>" +
                  "</build>");

    assertModules("project");

    assertExcludes("project", "target");
    assertModuleOutput("project",
                       getProjectPath() + "/target/outputCustom",
                       getProjectPath() + "/target/testCustom");
  }

  public void testDoNotExcludeExcludeOutputDirectoryWhenItPointstoRoot() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <outputDirectory>.</outputDirectory>" +
                  "  <testOutputDirectory>.</testOutputDirectory>" +
                  "</build>");

    assertModules("project");

    assertExcludes("project",
                   "target");
    assertModuleOutput("project",
                       getProjectPath(),
                       getProjectPath());
  }

  public void testOutputDirsOutsideOfContentRoot() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <directory>../target</directory>" +
                  "  <outputDirectory>../target/classes</outputDirectory>" +
                  "  <testOutputDirectory>../target/test-classes</testOutputDirectory>" +
                  "</build>");

    String targetPath = getParentPath() + "/target";
    String targetUrl = new Path(targetPath).toUrl().getUrl();

    assertContentRoots("project", getProjectPath());

    //ContentEntry targetEntry = null;
    //for (ContentEntry each : getContentRoots("project")) {
    //  if (each.getUrl().equals(targetUrl)) {
    //    targetEntry = each;
    //    break;
    //  }
    //}
    //ExcludeFolder[] excludedFolders = targetEntry.getExcludeFolders();
    //assertEquals(1, excludedFolders.length);
    //assertEquals(targetUrl, excludedFolders[0].getUrl());
    //
    assertModuleOutput("project",
                       getParentPath() + "/target/classes",
                       getParentPath() + "/target/test-classes");
  }

  public void testDoesNotExcludeGeneratedSourcesUnderTargetDir() throws Exception {
    createStdProjectFolders();
    createProjectSubDirsWithFile("target/foo",
                                 "target/bar",
                                 "target/generated-sources/baz",
                                 "target/generated-test-sources/bazz");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertExcludes("project", "target");

    assertSources("project",
                  "src/main/java",
                  "target/generated-sources/baz");
    assertResources("project", "src/main/resources");

    assertTestSources("project",
                      "src/test/java",
                      "target/generated-test-sources/bazz");
    assertTestResources("project", "src/test/resources");
  }

  public void testDoesNotExcludeSourcesUnderTargetDir() {
    createStdProjectFolders();
    createProjectSubDirs("target/src",
                         "target/test",
                         "target/xxx");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <sourceDirectory>target/src</sourceDirectory>" +
                  "  <testSourceDirectory>target/test</testSourceDirectory>" +
                  "</build>");

    assertModules("project");

    assertExcludes("project", "target");
  }

  public void testDoesNotExcludeSourcesUnderTargetDirWithProperties() {
    createProjectSubDirs("target/src", "target/xxx");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <sourceDirectory>${project.build.directory}/src</sourceDirectory>" +
                  "</build>");

    assertModules("project");

    assertSources("project", "target/src");
    assertExcludes("project", "target");
  }

  public void testDoesNotExcludeFoldersWithSourcesUnderTargetDir() {
    createStdProjectFolders();
    createProjectSubDirs("target/src/main",
                         "target/foo");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <sourceDirectory>target/src/main</sourceDirectory>" +
                  "</build>");

    assertModules("project");

    assertExcludes("project", "target");

    assertSources("project", "target/src/main");
    assertResources("project", "src/main/resources");
  }

  public void testDoesNotUnExcludeFoldersOnRemoval() throws Exception {
    createStdProjectFolders();

    final VirtualFile subDir = createProjectSubDir("target/foo");
    createProjectSubDirsWithFile("target/generated-sources/baz");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertExcludes("project", "target");
    assertSources("project",
                  "src/main/java",
                  "target/generated-sources/baz");
    assertResources("project", "src/main/resources");

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        try {
          subDir.delete(this);
        }
        catch (IOException e) {
          fail("Unable to delete the file: " + e.getMessage());
        }
      }
    });

    importProject();
    assertExcludes("project", "target");
  }

  public void testSourceFoldersOrder() throws Exception {
    createStdProjectFolders();

    final VirtualFile target = createProjectSubDir("target");
    createProjectSubDirsWithFile("anno",
                                 "target/generated-sources/foo",
                                 "target/generated-sources/annotations",
                                 "target/generated-sources/test-annotations",
                                 "target/generated-test-sources/foo");

    createProjectSubDir("test-anno");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  " <plugins>" +
                  "  <plugin>" +
                  "   <groupId>org.apache.maven.plugins</groupId>" +
                  "   <artifactId>maven-compiler-plugin</artifactId>" +
                  "   <version>2.3.2</version>" +
                  "   <configuration>" +
                  "     <generatedSourcesDirectory>${basedir}/anno</generatedSourcesDirectory>" +
                  "     <generatedTestSourcesDirectory>${basedir}/test-anno</generatedTestSourcesDirectory>" +
                  "   </configuration>" +
                  "  </plugin>" +
                  " </plugins>" +
                  "</build>");

    final Runnable testAssertions = () -> {
      assertSources("project",
                    "anno",
                    "src/main/java",
                    "target/generated-sources/annotations",
                    "target/generated-sources/foo",
                    "target/generated-sources/test-annotations");

      assertResources("project", "src/main/resources");
      assertTestSources("project",
                        "src/test/java",
                        "target/generated-test-sources/foo");
      assertTestResources("project", "src/test/resources");
    };

    testAssertions.run();

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        try {
          target.delete(this);
        }
        catch (IOException e) {
          fail("Unable to delete the file: " + e.getMessage());
        }
      }
    });

    testAssertions.run();
    importProject();
    testAssertions.run();
    resolveFoldersAndImport();
    testAssertions.run();
  }

  public void testUnexcludeNewSources() {
    createProjectSubDirs("target/foo");
    createProjectSubDirs("target/src");
    createProjectSubDirs("target/test/subFolder");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertExcludes("project", "target");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<build>" +
                     "  <sourceDirectory>target/src</sourceDirectory>" +
                     "  <testSourceDirectory>target/test/subFolder</testSourceDirectory>" +
                     "</build>");
    importProject();

    assertSources("project", "target/src");
    assertTestSources("project", "target/test/subFolder");
    assertExcludes("project", "target");
  }

  public void testUnexcludeNewSourcesUnderCompilerOutputDir() {
    createProjectSubDirs("target/classes/src");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertExcludes("project", "target");
    //assertTrue(getCompilerExtension("project").isExcludeOutput());

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<build>" +
                     "  <sourceDirectory>target/classes/src</sourceDirectory>" +
                     "</build>");
    importProject();

    assertSources("project", "target/classes/src");
    assertExcludes("project", "target");

    //assertFalse(getCompilerExtension("project").isExcludeOutput());
  }

  public void testAnnotationProcessorSources() throws Exception {
    createStdProjectFolders();
    createProjectSubDirsWithFile("target/generated-sources/foo",
                                 "target/generated-sources/annotations",
                                 "target/generated-test-sources/test-annotations",
                                 "target/generated-test-sources/foo");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertSources("project",
                  "src/main/java",
                  "target/generated-sources/annotations",
                  "target/generated-sources/foo");
    assertResources("project", "src/main/resources");

    assertTestSources("project",
                      "src/test/java",
                      "target/generated-test-sources/foo",
                      "target/generated-test-sources/test-annotations");
    assertTestResources("project", "src/test/resources");
  }

  public void testCustomAnnotationProcessorSources() throws Exception {
    createStdProjectFolders();
    createProjectSubDirsWithFile("anno",
                                 "target/generated-sources/foo",
                                 "target/generated-sources/annotations",
                                 "target/generated-sources/test-annotations",
                                 "target/generated-test-sources/foo");

    createProjectSubDir("test-anno");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  " <plugins>" +
                  "  <plugin>" +
                  "   <groupId>org.apache.maven.plugins</groupId>" +
                  "   <artifactId>maven-compiler-plugin</artifactId>" +
                  "   <version>2.3.2</version>" +
                  "   <configuration>" +
                  "     <generatedSourcesDirectory>${basedir}/anno</generatedSourcesDirectory>" +
                  "     <generatedTestSourcesDirectory>${basedir}/test-anno</generatedTestSourcesDirectory>" +
                  "   </configuration>" +
                  "  </plugin>" +
                  " </plugins>" +
                  "</build>");

    assertSources("project",
                  "anno",
                  "src/main/java",
                  "target/generated-sources/annotations",
                  "target/generated-sources/foo",
                  "target/generated-sources/test-annotations");
    assertResources("project", "src/main/resources");

    assertTestSources("project",
                      "src/test/java",
                      "target/generated-test-sources/foo");
    assertTestResources("project", "src/test/resources");
  }

  private void createProjectSubDirsWithFile(String ... dirs) throws IOException {
    for (String dir : dirs) {
      createProjectSubFile(dir + "/a.txt");
    }
  }
}
