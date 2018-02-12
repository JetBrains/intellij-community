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
package org.jetbrains.idea.maven.compiler;

import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;

import java.util.Arrays;

public class ResourceFilteringTest extends MavenCompilingTestCase {

  public void testBasic() throws Exception {
    createProjectSubFile("resources/file.properties", "value=${project.version}\n" +
                                                      "value2=@project.version@\n" +
                                                      "time=${time}");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +
                  "<properties>" +
                  "  <time>${maven.build.timestamp}</time>" +
                  "  <maven.build.timestamp.format>---</maven.build.timestamp.format>\n" +
                  "</properties>\n" +
                  "" +

                  "<build>" +
                  "  <resources>" +
                  "    <resource>" +
                  "      <directory>resources</directory>" +
                  "      <filtering>true</filtering>" +
                  "    </resource>" +
                  "  </resources>" +
                  "</build>");

    compileModules("project");

    assertResult("target/classes/file.properties", "value=1\n" +
                                                   "value2=1\n" +
                                                   "time=---");
  }

  public void testResolveSettingProperty() throws Exception {
    createProjectSubFile("resources/file.properties", "value=${settings.localRepository}");

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

    assert !loadResult(myProjectPom, "target/classes/file.properties").contains("settings.localRepository");
  }

  public void testCustomDelimiter() throws Exception {
    createProjectSubFile("resources/file.properties", "value1=${project.version}\n" +
                                                      "value2=@project.version@\n" +
                                                      "valueX=|\n" +
                                                      "value3=|project.version|\n" +
                                                      "value4=(project.version]");

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
                  "        <delimiters>" +
                  "          <delimiter>|</delimiter>" +
                  "          <delimiter>(*]</delimiter>" +
                  "        </delimiters>" +
                  "      </configuration>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");

    compileModules("project");

    assertResult("target/classes/file.properties", "value1=1\n" +
                                                   "value2=1\n" +
                                                   "valueX=|\n" +
                                                   "value3=1\n" +
                                                   "value4=1");
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
    createProjectSubFile("resources/file.properties", "value=@project.version@");

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

  public void testEscapingWindowsChars() throws Exception {
    createProjectSubFile("resources/file.txt", "value=${foo}\n" +
                                               "value2=@foo@\n" +
                                               "value3=${bar}");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<properties>" +
                  "  <foo>c:\\projects\\foo/bar</foo>" +
                  "  <bar>a\\b\\c</foo>" +
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

    assertResult("target/classes/file.txt", "value=c:\\\\projects\\\\foo/bar\n" +
                                            "value2=c:\\\\projects\\\\foo/bar\n" +
                                            "value3=a\\b\\c");
  }

  public void testDontEscapingWindowsChars() throws Exception {
    createProjectSubFile("resources/file.txt", "value=${foo}");

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
                  "" +
                  "    <plugins>" +
                  "      <plugin>" +
                  "        <artifactId>maven-resources-plugin</artifactId>" +
                  "        <configuration>" +
                  "          <escapeWindowsPaths>false</escapeWindowsPaths>" +
                  "        </configuration>" +
                  "      </plugin>" +
                  "    </plugins>" +
                  "</build>");
    compileModules("project");

    assertResult("target/classes/file.txt", "value=c:\\projects\\foo/bar");
  }

  public void testFilteringPropertiesWithEmptyValues() throws Exception {
    createProjectSubFile("resources/file.properties", "value1=${foo}\nvalue2=${bar}");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<properties>" +
                  "  <foo/>" +
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

    assertResult("target/classes/file.properties", "value1=\nvalue2=${bar}");
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
    final VirtualFile filter = createProjectSubFile("filters/filter.properties", "xxx=1");
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

    new WriteAction() {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        VfsUtil.saveText(filter, "xxx=2");
      }
    }.execute().throwException();
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
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

    myProjectsManager.setExplicitProfiles(new MavenExplicitProfiles(Arrays.asList("two")));
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

  public void testCustomFiltersViaPlugin() throws Exception {
    createProjectSubFile("filters/filter.properties", "xxx=value");
    createProjectSubFile("resources/file.properties", "value1=${xxx}");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <plugins>\n" +
                  "    <plugin>\n" +
                  "      <groupId>org.codehaus.mojo</groupId>\n" +
                  "      <artifactId>properties-maven-plugin</artifactId>\n" +
                  "      <executions>\n" +
                  "        <execution>\n" +
                  "          <id>common-properties</id>\n" +
                  "          <phase>initialize</phase>\n" +
                  "          <goals>\n" +
                  "            <goal>read-project-properties</goal>\n" +
                  "          </goals>\n" +
                  "          <configuration>\n" +
                  "            <files>\n" +
                  "              <file>filters/filter.properties</file>\n" +
                  "            </files>\n" +
                  "          </configuration>\n" +
                  "        </execution>\n" +
                  "      </executions>\n" +
                  "    </plugin>" +
                  "  </plugins>\n" +
                  "  <resources>" +
                  "    <resource>" +
                  "      <directory>resources</directory>" +
                  "      <filtering>true</filtering>" +
                  "    </resource>" +
                  "  </resources>" +
                  "</build>");
    compileModules("project");

    assertResult("target/classes/file.properties", "value1=value");
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
    if (!true) return;

    createProjectSubFile("filters/filter.properties", "xxx=value");
    createProjectSubFile("resources/file.properties",
                         "value1=\\${xxx}\n" +
                         "value2=\\\\${xxx}\n" +
                         "value3=\\\\\\${xxx}\n" +
                         "value3=\\\\\\\\${xxx}\n" +
                         "value4=.\\.\\\\.\\\\\\.");

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
                  "        <escapeString>\\</escapeString>" +
                  "      </configuration>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");

    compileModules("project");
    assertResult("target/classes/file.properties",
                 "value1=${xxx}\n" +
                 "value2=\\\\value\n" +
                 "value3=\\\\${xxx}\n" +
                 "value3=\\\\\\\\value\n" +
                 "value4=.\\.\\\\.\\\\\\.");
  }

  public void testPropertyPriority() throws Exception {
    createProjectSubFile("filters/filter.properties", "xxx=fromFilterFile\n" +
                                                      "yyy=fromFilterFile");
    createProjectSubFile("resources/file.properties","value1=${xxx}\n" +
                                                     "value2=${yyy}");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<properties>" +
                  "  <xxx>fromProperties</xxx>" +
                  "</properties>" +

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
                 "value1=fromProperties\n" +
                 "value2=fromFilterFile");
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

  public void testDoNotFilterButCopyBigFiles() {
    assertEquals(FileTypeManager.getInstance().getFileTypeByFileName("file.xyz"), FileTypes.UNKNOWN);

    new WriteAction() {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        createProjectSubFile("resources/file.xyz").setBinaryContent(new byte[1024 * 1024 * 20]);
      }
    }.execute().throwException();

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

  public void testResourcesOrdering1() throws Exception {
    createProjectSubFile("resources/file.properties", "value=${project.version}\n");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +
                  "" +

                  "<build>" +
                  "  <resources>" +
                  "    <resource>" +
                  "      <directory>resources</directory>" +
                  "      <filtering>false</filtering>" +
                  "    </resource>" +
                  "    <resource>" +
                  "      <directory>resources</directory>" +
                  "      <filtering>true</filtering>" +
                  "    </resource>" +
                  "  </resources>" +
                  "</build>");

    compileModules("project");

    assertResult("target/classes/file.properties", "value=1\n"); // Filtered file override non-filtered file
  }

  public void testResourcesOrdering2() throws Exception {
    if (!true) return;

    createProjectSubFile("resources/file.properties", "value=${project.version}\n");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +
                  "" +

                  "<build>" +
                  "  <resources>" +
                  "    <resource>" +
                  "      <directory>resources</directory>" +
                  "      <filtering>true</filtering>" +
                  "    </resource>" +
                  "    <resource>" +
                  "      <directory>resources</directory>" +
                  "      <filtering>false</filtering>" +
                  "    </resource>" +
                  "  </resources>" +
                  "</build>");

    compileModules("project");

    assertResult("target/classes/file.properties", "value=1\n"); // Filtered file override non-filtered file
  }

  public void testResourcesOrdering3() throws Exception {
    if (!true) return;

    createProjectSubFile("resources1/a.txt", "1");
    createProjectSubFile("resources2/a.txt", "2");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +
                  "" +

                  "<build>" +
                  "  <resources>" +
                  "    <resource>" +
                  "      <directory>resources1</directory>" +
                  "    </resource>" +
                  "    <resource>" +
                  "      <directory>resources2</directory>" +
                  "    </resource>" +
                  "  </resources>" +
                  "</build>");

    compileModules("project");

    assertResult("target/classes/a.txt", "1"); // First file was copied, second file was not override first file
  }

  public void testResourcesOrdering4() throws Exception {
    createProjectSubFile("resources1/a.txt", "1");
    createProjectSubFile("resources2/a.txt", "2");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +
                  "" +

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

    assertResult("target/classes/a.txt", "2"); // For the filtered files last file override other files.
  }

  public void testOverwriteParameter1() throws Exception {
    if (!true) return;

    createProjectSubFile("resources1/a.txt", "1");
    createProjectSubFile("resources2/a.txt", "2");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +
                  "" +

                  "<build>" +
                  "  <resources>" +
                  "    <resource>" +
                  "      <directory>resources1</directory>" +
                  "    </resource>" +
                  "    <resource>" +
                  "      <directory>resources2</directory>" +
                  "    </resource>" +
                  "  </resources>" +
                  "" +
                  "  <plugins>" +
                  "    <plugin>" +
                  "      <artifactId>maven-resources-plugin</artifactId>" +
                  "      <configuration>" +
                  "        <overwrite>true</overwrite>" +
                  "      </configuration>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");

    compileModules("project");

    assertResult("target/classes/a.txt", "2");
  }

  public void testOverwriteParameter2() throws Exception {
    if (!true) return;

    createProjectSubFile("resources1/a.txt", "1");
    createProjectSubFile("resources2/a.txt", "2");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +
                  "" +

                  "<build>" +
                  "  <resources>" +
                  "    <resource>" +
                  "      <directory>resources1</directory>" +
                  "      <filtering>true</filtering>" +
                  "    </resource>" +
                  "    <resource>" +
                  "      <directory>resources2</directory>" +
                  "    </resource>" +
                  "  </resources>" +
                  "" +
                  "  <plugins>" +
                  "    <plugin>" +
                  "      <artifactId>maven-resources-plugin</artifactId>" +
                  "      <configuration>" +
                  "        <overwrite>true</overwrite>" +
                  "      </configuration>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");

    compileModules("project");

    assertResult("target/classes/a.txt", "2");
  }

}
