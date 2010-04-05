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
import com.intellij.compiler.CompilerManagerImpl;
import com.intellij.compiler.CompilerWorkspaceConfiguration;
import com.intellij.compiler.impl.ModuleCompileScope;
import com.intellij.compiler.impl.TranslatingCompilerFilesMonitor;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.MavenImportingTestCase;
import org.jetbrains.idea.maven.importing.MavenDefaultModifiableModelsProvider;
import org.jetbrains.idea.maven.importing.MavenRootModelAdapter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ResourceFilteringTest extends MavenImportingTestCase {
  public void testBasic() throws Exception {
    createProjectSubFile("resources/file.properties", "value=${project.version}");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <resources>" +
                  "    <resource>" +
                  "      <directory>resources</directory>" +
                  "      <filtering>true</filtering>" +
                  "    </resource>" +
                  "  </resources>" +
                  "</build>");
    compileModules("project");

    assertResult("target/classes/file.properties", "value=1");
  }

  public void testPomArtifactId() throws Exception {
    createProjectSubFile("resources/file.properties", "value=${pom.artifactId}");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <resources>" +
                  "    <resource>" +
                  "      <directory>resources</directory>" +
                  "      <filtering>true</filtering>" +
                  "    </resource>" +
                  "  </resources>" +
                  "</build>");
    compileModules("project");

    assertResult("target/classes/file.properties", "value=project");
  }

  public void testPomVersionInModules() throws Exception {
    createProjectSubFile("m1/resources/file.properties", "value=${pom.version}");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "</modules>");

    createModulePom("m1",
                    "<groupId>test</groupId>" +
                    "<artifactId>m1</artifactId>" +
                    "<version>2</version>" +

                    "<build>" +
                    "  <resources>" +
                    "    <resource>" +
                    "      <directory>resources</directory>" +
                    "      <filtering>true</filtering>" +
                    "    </resource>" +
                    "  </resources>" +
                    "</build>");
    importProject();

    compileModules("project", "m1");

    assertResult("m1/target/classes/file.properties", "value=2");
  }

  public void testDoNotFilterSomeFileByDefault() throws Exception {
    createProjectSubFile("resources/file.bmp", "value=${project.version}");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <resources>" +
                  "    <resource>" +
                  "      <directory>resources</directory>" +
                  "      <filtering>true</filtering>" +
                  "    </resource>" +
                  "  </resources>" +
                  "</build>");
    compileModules("project");

    assertResult("target/classes/file.bmp", "value=${project.version}");
  }

  public void testCustomNonFilteredExtensions() throws Exception {
    createProjectSubFile("resources/file.bmp", "value=${project.version}");
    createProjectSubFile("resources/file.xxx", "value=${project.version}");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <resources>" +
                  "    <resource>" +
                  "      <directory>resources</directory>" +
                  "      <filtering>true</filtering>" +
                  "    </resource>" +
                  "  </resources>" +
                  "  <plugins>" +
                  "    <plugin>" +
                  "      <groupId>org.apache.maven.plugins</groupId>" +
                  "      <artifactId>maven-resources-plugin</artifactId>" +
                  "      <configuration>" +
                  "        <nonFilteredFileExtensions>" +
                  "          <nonFilteredFileExtension>xxx</nonFilteredFileExtension>" +
                  "        </nonFilteredFileExtensions>" +
                  "      </configuration>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");
    compileModules("project");

    assertResult("target/classes/file.bmp", "value=${project.version}");
    assertResult("target/classes/file.xxx", "value=${project.version}");
  }

  public void testFilteringTestResources() throws Exception {
    createProjectSubFile("resources/file.properties", "value=${project.version}");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <testResources>" +
                  "    <testResource>" +
                  "      <directory>resources</directory>" +
                  "      <filtering>true</filtering>" +
                  "    </testResource>" +
                  "  </testResources>" +
                  "</build>");
    compileModules("project");

    assertResult("target/test-classes/file.properties", "value=1");
  }

  public void testExcludesAndIncludes() throws Exception {
    createProjectSubFile("src/main/resources/file1.properties", "value=${project.artifactId}");
    createProjectSubFile("src/main/resources/file2.properties", "value=${project.artifactId}");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <resources>" +
                  "    <resource>" +
                  "      <directory>src/main/resources</directory>" +
                  "      <excludes>" +
                  "        <exclude>file1.properties</exclude>" +
                  "      </excludes>" +
                  "      <filtering>true</filtering>" +
                  "    </resource>" +
                  "    <resource>" +
                  "      <directory>src/main/resources</directory>" +
                  "      <includes>" +
                  "        <include>file1.properties</include>" +
                  "      </includes>" +
                  "      <filtering>false</filtering>" +
                  "    </resource>" +
                  "  </resources>" +
                  "</build>");

    compileModules("project");
    assertResult("target/classes/file1.properties", "value=${project.artifactId}");
    assertResult("target/classes/file2.properties", "value=project");

    compileModules();
    assertResult("target/classes/file1.properties", "value=${project.artifactId}");
    assertResult("target/classes/file2.properties", "value=project");

    compileModules("project");
    assertResult("target/classes/file1.properties", "value=${project.artifactId}");
    assertResult("target/classes/file2.properties", "value=project");
  }

  public void testWorkCorrectlyIfFoldersMarkedAsSource() throws Exception {
    createProjectSubFile("src/main/resources/file1.properties", "value=${project.artifactId}");
    createProjectSubFile("src/main/ideaRes/file2.properties", "value=${project.artifactId}");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <resources>" +
                  "    <resource>" +
                  "      <directory>src/main/resources</directory>" +
                  "      <filtering>true</filtering>" +
                  "    </resource>" +
                  "  </resources>" +
                  "</build>");

    MavenRootModelAdapter adapter = new MavenRootModelAdapter(myProjectsTree.findProject(myProjectPom),
                                                              getModule("project"),
                                                              new MavenDefaultModifiableModelsProvider(myProject));
    adapter.addSourceFolder(myProjectRoot.findFileByRelativePath("src/main/resources").getPath(), false);
    adapter.addSourceFolder(myProjectRoot.findFileByRelativePath("src/main/ideaRes").getPath(), false);
    adapter.getRootModel().commit();

    assertSources("project", "src/main/resources", "src/main/ideaRes");

    compileModules("project");

    assertResult("target/classes/file1.properties", "value=project");
    assertResult("target/classes/file2.properties", "value=${project.artifactId}");
  }


  public void testEscapingSpecialCharsInProperties() throws Exception {
    createProjectSubFile("resources/file.txt", "value=${foo}");
    createProjectSubFile("resources/file.properties", "value=${foo}");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<properties>" +
                  "  <foo>c:\\projects\\foo/bar</foo>" +
                  "</properties>" +

                  "<build>" +
                  "  <resources>" +
                  "    <resource>" +
                  "      <directory>resources</directory>" +
                  "      <filtering>true</filtering>" +
                  "    </resource>" +
                  "  </resources>" +
                  "</build>");
    compileModules("project");

    assertResult("target/classes/file.txt", "value=c:\\projects\\foo/bar");
    assertResult("target/classes/file.properties", "value=c:\\\\projects\\\\foo/bar");
  }

  public void testFilterWithSeveralResourceFolders() throws Exception {
    createProjectSubFile("resources1/file1.properties", "value=${project.version}");
    createProjectSubFile("resources2/file2.properties", "value=${project.version}");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <resources>" +
                  "    <resource>" +
                  "      <directory>resources1</directory>" +
                  "      <filtering>true</filtering>" +
                  "    </resource>" +
                  "    <resource>" +
                  "      <directory>resources2</directory>" +
                  "      <filtering>true</filtering>" +
                  "    </resource>" +
                  "  </resources>" +
                  "</build>");
    compileModules("project");

    assertResult("target/classes/file1.properties", "value=1");
    assertResult("target/classes/file2.properties", "value=1");
  }

  public void testFilterWithSeveralModules() throws Exception {
    createProjectSubFile("module1/resources/file1.properties", "value=${project.version}");
    createProjectSubFile("module2/resources/file2.properties", "value=${project.version}");

    VirtualFile m1 = createModulePom("module1",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>module1</artifactId>" +
                                     "<version>1</version>" +

                                     "<build>" +
                                     "  <resources>" +
                                     "    <resource>" +
                                     "      <directory>resources</directory>" +
                                     "      <filtering>true</filtering>" +
                                     "    </resource>" +
                                     "  </resources>" +
                                     "</build>");

    VirtualFile m2 = createModulePom("module2",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>module2</artifactId>" +
                                     "<version>2</version>" +

                                     "<build>" +
                                     "  <resources>" +
                                     "    <resource>" +
                                     "      <directory>resources</directory>" +
                                     "      <filtering>true</filtering>" +
                                     "    </resource>" +
                                     "  </resources>" +
                                     "</build>");

    importProjects(m1, m2);
    compileModules("module1", "module2");

    assertResult(m1, "target/classes/file1.properties", "value=1");
    assertResult(m2, "target/classes/file2.properties", "value=2");
  }

  public void testDoNotFilterIfNotRequested() throws Exception {
    createProjectSubFile("resources1/file1.properties", "value=${project.version}");
    createProjectSubFile("resources2/file2.properties", "value=${project.version}");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <resources>" +
                  "    <resource>" +
                  "      <directory>resources1</directory>" +
                  "      <filtering>true</filtering>" +
                  "    </resource>" +
                  "    <resource>" +
                  "      <directory>resources2</directory>" +
                  "      <filtering>false</filtering>" +
                  "    </resource>" +
                  "  </resources>" +
                  "</build>");
    compileModules("project");

    assertResult("target/classes/file1.properties", "value=1");
    assertResult("target/classes/file2.properties", "value=${project.version}");
  }

  public void testDoNotChangeFileIfPropertyIsNotResolved() throws Exception {
    createProjectSubFile("resources/file.properties", "value=${foo.bar}");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <resources>" +
                  "    <resource>" +
                  "      <directory>resources</directory>" +
                  "      <filtering>true</filtering>" +
                  "    </resource>" +
                  "  </resources>" +
                  "</build>");
    compileModules("project");

    assertResult("target/classes/file.properties", "value=${foo.bar}");
  }

  public void testChangingResolvedPropsBackWhenSettingsIsChange() throws Exception {
    createProjectSubFile("resources/file.properties", "value=${project.version}");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <resources>" +
                  "    <resource>" +
                  "      <directory>resources</directory>" +
                  "      <filtering>true</filtering>" +
                  "    </resource>" +
                  "  </resources>" +
                  "</build>");
    compileModules("project");
    assertResult("target/classes/file.properties", "value=1");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<build>" +
                     "  <resources>" +
                     "    <resource>" +
                     "      <directory>resources</directory>" +
                     "      <filtering>false</filtering>" +
                     "    </resource>" +
                     "  </resources>" +
                     "</build>");
    importProject();
    compileModules("project");

    assertResult("target/classes/file.properties", "value=${project.version}");
  }

  public void testUpdatingWhenPropertiesInFiltersAreChanged() throws Exception {
    VirtualFile filter = createProjectSubFile("filters/filter.properties", "xxx=1");
    createProjectSubFile("resources/file.properties", "value=${xxx}");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <filters>" +
                  "    <filter>filters/filter.properties</filter>" +
                  "  </filters>" +
                  "  <resources>" +
                  "    <resource>" +
                  "      <directory>resources</directory>" +
                  "      <filtering>true</filtering>" +
                  "    </resource>" +
                  "  </resources>" +
                  "</build>");
    compileModules("project");
    assertResult("target/classes/file.properties", "value=1");

    VfsUtil.saveText(filter, "xxx=2");
    compileModules("project");
    assertResult("target/classes/file.properties", "value=2");
  }

  public void testUpdatingWhenPropertiesAreChanged() throws Exception {
    createProjectSubFile("resources/file.properties", "value=${foo}");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<properties>" +
                  "  <foo>val1</foo>" +
                  "</properties>" +

                  "<build>" +
                  "  <resources>" +
                  "    <resource>" +
                  "      <directory>resources</directory>" +
                  "      <filtering>true</filtering>" +
                  "    </resource>" +
                  "  </resources>" +
                  "</build>");
    compileModules("project");
    assertResult("target/classes/file.properties", "value=val1");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<properties>" +
                  "  <foo>val2</foo>" +
                  "</properties>" +

                  "<build>" +
                  "  <resources>" +
                  "    <resource>" +
                  "      <directory>resources</directory>" +
                  "      <filtering>true</filtering>" +
                  "    </resource>" +
                  "  </resources>" +
                  "</build>");
    compileModules("project");
    assertResult("target/classes/file.properties", "value=val2");
  }

  public void testUpdatingWhenPropertiesInModelAreChanged() throws Exception {
    createProjectSubFile("resources/file.properties", "value=${project.name}");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<name>val1</name>" +

                  "<build>" +
                  "  <resources>" +
                  "    <resource>" +
                  "      <directory>resources</directory>" +
                  "      <filtering>true</filtering>" +
                  "    </resource>" +
                  "  </resources>" +
                  "</build>");
    compileModules("project");
    assertResult("target/classes/file.properties", "value=val1");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<name>val2</name>" +

                  "<build>" +
                  "  <resources>" +
                  "    <resource>" +
                  "      <directory>resources</directory>" +
                  "      <filtering>true</filtering>" +
                  "    </resource>" +
                  "  </resources>" +
                  "</build>");
    compileModules("project");
    assertResult("target/classes/file.properties", "value=val2");
  }

  public void testUpdatingWhenProfilesAreChanged() throws Exception {
    createProjectSubFile("resources/file.properties", "value=${foo}");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<profiles>" +
                     "  <profile>" +
                     "    <id>one</id>" +
                     "    <properties>" +
                     "      <foo>val1</foo>" +
                     "    </properties>" +
                     "  </profile>" +
                     "  <profile>" +
                     "    <id>two</id>" +
                     "    <properties>" +
                     "      <foo>val2</foo>" +
                     "    </properties>" +
                     "  </profile>" +
                     "</profiles>" +

                     "<build>" +
                     "  <resources>" +
                     "    <resource>" +
                     "      <directory>resources</directory>" +
                     "      <filtering>true</filtering>" +
                     "    </resource>" +
                     "  </resources>" +
                     "</build>");
    importProjectWithProfiles("one");
    compileModules("project");
    assertResult("target/classes/file.properties", "value=val1");

    myProjectsManager.setExplicitProfiles(Arrays.asList("two"));
    scheduleResolveAll();
    resolveDependenciesAndImport();
    compileModules("project");
    assertResult("target/classes/file.properties", "value=val2");
  }

  public void testSameFileInSourcesAndTestSources() throws Exception {
    createProjectSubFile("src/main/resources/file.properties", "foo=${foo.main}");
    createProjectSubFile("src/test/resources/file.properties", "foo=${foo.test}");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<properties>" +
                  "  <foo.main>main</foo.main>" +
                  "  <foo.test>test</foo.test>" +
                  "</properties>" +

                  "<build>" +
                  "  <resources>" +
                  "    <resource>" +
                  "      <directory>src/main/resources</directory>" +
                  "      <filtering>true</filtering>" +
                  "    </resource>" +
                  "  </resources>" +
                  "  <testResources>" +
                  "    <testResource>" +
                  "      <directory>src/test/resources</directory>" +
                  "      <filtering>true</filtering>" +
                  "    </testResource>" +
                  "  </testResources>" +
                  "</build>");
    compileModules("project");

    assertResult("target/classes/file.properties", "foo=main");
    assertResult("target/test-classes/file.properties", "foo=test");
  }

  public void testCustomFilters() throws Exception {
    createProjectSubFile("filters/filter1.properties",
                         "xxx=value\n" +
                         "yyy=${project.version}\n");
    createProjectSubFile("filters/filter2.properties", "zzz=value2");
    createProjectSubFile("resources/file.properties",
                         "value1=${xxx}\n" +
                         "value2=${yyy}\n" +
                         "value3=${zzz}\n");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <filters>" +
                  "    <filter>filters/filter1.properties</filter>" +
                  "    <filter>filters/filter2.properties</filter>" +
                  "  </filters>" +
                  "  <resources>" +
                  "    <resource>" +
                  "      <directory>resources</directory>" +
                  "      <filtering>true</filtering>" +
                  "    </resource>" +
                  "  </resources>" +
                  "</build>");
    compileModules("project");

    assertResult("target/classes/file.properties", "value1=value\n" +
                                                   "value2=1\n" +
                                                   "value3=value2\n");
  }

  public void testCustomFilterWithPropertyInThePath() throws Exception {
    createProjectSubFile("filters/filter.properties", "xxx=value");
    createProjectSubFile("resources/file.properties", "value=${xxx}");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<properties>" +
                  " <some.path>" + getProjectPath() + "/filters</some.path>" +
                  "</properties>" +

                  "<build>" +
                  "  <filters>" +
                  "    <filter>${some.path}/filter.properties</filter>" +
                  "  </filters>" +
                  "  <resources>" +
                  "    <resource>" +
                  "      <directory>resources</directory>" +
                  "      <filtering>true</filtering>" +
                  "    </resource>" +
                  "  </resources>" +
                  "</build>");
    compileModules("project");

    assertResult("target/classes/file.properties", "value=value");
  }

  public void testCustomFiltersFromProfiles() throws Exception {
    createProjectSubFile("filters/filter1.properties", "xxx=value1");
    createProjectSubFile("filters/filter2.properties", "yyy=value2");
    createProjectSubFile("resources/file.properties",
                         "value1=${xxx}\n" +
                         "value2=${yyy}\n");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<profiles>" +
                     "  <profile>" +
                     "    <id>one</id>" +
                     "    <build>" +
                     "      <filters>" +
                     "        <filter>filters/filter1.properties</filter>" +
                     "      </filters>" +
                     "    </build>" +
                     "  </profile>" +
                     "  <profile>" +
                     "    <id>two</id>" +
                     "    <build>" +
                     "      <filters>" +
                     "        <filter>filters/filter2.properties</filter>" +
                     "      </filters>" +
                     "    </build>" +
                     "  </profile>" +
                     "</profiles>" +

                     "<build>" +
                     "  <resources>" +
                     "    <resource>" +
                     "      <directory>resources</directory>" +
                     "      <filtering>true</filtering>" +
                     "    </resource>" +
                     "  </resources>" +
                     "</build>");

    importProjectWithProfiles("one");
    compileModules("project");
    assertResult("target/classes/file.properties", "value1=value1\n" +
                                                   "value2=${yyy}\n");

    importProjectWithProfiles("two");
    compileModules("project");
    assertResult("target/classes/file.properties", "value1=${xxx}\n" +
                                                   "value2=value2\n");
  }

  public void testPluginDirectoriesFiltering() throws Exception {
    if (ignore()) return;

    createProjectSubFile("filters/filter.properties", "xxx=value");
    createProjectSubFile("webdir1/file1.properties", "value=${xxx}");
    createProjectSubFile("webdir2/file2.properties", "value=${xxx}");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +
                  "<packaging>war</packaging>" +

                  "<build>" +
                  "  <filters>" +
                  "    <filter>filters/filter.properties</filter>" +
                  "  </filters>" +
                  "  <plugins>" +
                  "    <plugin>" +
                  "      <artifactId>maven-war-plugin</artifactId>\n" +
                  "      <configuration>" +
                  "        <webResources>" +
                  "          <resource>" +
                  "            <directory>webdir1</directory>" +
                  "            <filtering>true</filtering>" +
                  "          </resource>" +
                  "          <resource>" +
                  "            <directory>webdir2</directory>" +
                  "            <filtering>false</filtering>" +
                  "          </resource>" +
                  "        </webResources>" +
                  "      </configuration>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");

    compileModules("project");
    assertResult("target/classes/file1.properties", "value=value");
    assertResult("target/classes/file2.properties", "value=${xxx}");
  }

  public void testEscapingFiltering() throws Exception {
    createProjectSubFile("filters/filter.properties", "xxx=value");
    createProjectSubFile("resources/file.properties",
                         "value1=\\${xxx}\n" +
                         "value2=${xxx}\n");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <filters>" +
                  "    <filter>filters/filter.properties</filter>" +
                  "  </filters>" +
                  "  <resources>" +
                  "    <resource>" +
                  "      <directory>resources</directory>" +
                  "      <filtering>true</filtering>" +
                  "    </resource>" +
                  "  </resources>" +
                  "</build>");

    compileModules("project");
    assertResult("target/classes/file.properties",
                 "value1=${xxx}\n" +
                 "value2=value\n");
  }

  public void testCustomEscapingFiltering() throws Exception {
    createProjectSubFile("filters/filter.properties", "xxx=value");
    createProjectSubFile("resources/file.properties",
                         "value1=^${xxx}\n" +
                         "value2=\\${xxx}\n");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <filters>" +
                  "    <filter>filters/filter.properties</filter>" +
                  "  </filters>" +
                  "  <resources>" +
                  "    <resource>" +
                  "      <directory>resources</directory>" +
                  "      <filtering>true</filtering>" +
                  "    </resource>" +
                  "  </resources>" +
                  "  <plugins>" +
                  "    <plugin>" +
                  "      <groupId>org.apache.maven.plugins</groupId>" +
                  "      <artifactId>maven-resources-plugin</artifactId>" +
                  "      <configuration>" +
                  "        <escapeString>^</escapeString>" +
                  "      </configuration>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");

    compileModules("project");
    assertResult("target/classes/file.properties",
                 "value1=${xxx}\n" +
                 "value2=\\value\n");
  }

  public void testDoNotFilterButCopyBigFiles() throws Exception {
    assertFalse(CompilerConfiguration.getInstance(myProject).isResourceFile("file.xyz"));
    assertEquals(FileTypeManager.getInstance().getFileTypeByFileName("file.xyz"), StdFileTypes.UNKNOWN);
    
    createProjectSubFile("resources/file.xyz").setBinaryContent(new byte[1024 * 1024 * 20]);

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <resources>" +
                  "    <resource>" +
                  "      <directory>resources</directory>" +
                  "      <filtering>true</filtering>" +
                  "    </resource>" +
                  "  </resources>" +
                  "</build>");
    compileModules("project");

    assertNotNull(myProjectPom.getParent().findFileByRelativePath("target/classes/file.xyz"));
  }

  private void assertResult(String relativePath, String content) throws IOException {
    assertResult(myProjectPom, relativePath, content);
  }

  private void assertResult(VirtualFile pomFile, String relativePath, String content) throws IOException {
    VirtualFile file = pomFile.getParent().findFileByRelativePath(relativePath);
    assertNotNull("file not found: " + relativePath, file);
    assertEquals(content, VfsUtil.loadText(file));
  }
}
