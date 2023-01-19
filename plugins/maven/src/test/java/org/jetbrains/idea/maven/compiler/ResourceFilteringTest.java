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

import com.intellij.maven.testFramework.MavenCompilingTestCase;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;

public class ResourceFilteringTest extends MavenCompilingTestCase {

  @Test
  public void testBasic() throws Exception {
    createProjectSubFile("resources/file.properties", """
      value=${project.version}
      value2=@project.version@
      time=${time}""");

    importProject("""
                    <groupId>test</groupId><artifactId>project</artifactId><version>1</version><properties>  <time>${maven.build.timestamp}</time>  <maven.build.timestamp.format>---</maven.build.timestamp.format>
                    </properties>
                    <build>  <resources>    <resource>      <directory>resources</directory>      <filtering>true</filtering>    </resource>  </resources></build>""");

    compileModules("project");

    assertResult("target/classes/file.properties", """
      value=1
      value2=1
      time=---""");
  }

  @Test
  public void testResolveSettingProperty() throws Exception {
    createProjectSubFile("resources/file.properties", "value=${settings.localRepository}");

    importProject("""
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
                    """);

    compileModules("project");

    assert !loadResult(myProjectPom, "target/classes/file.properties").contains("settings.localRepository");
  }

  @Test
  public void testCustomDelimiter() throws Exception {
    createProjectSubFile("resources/file.properties", """
      value1=${project.version}
      value2=@project.version@
      valueX=|
      value3=|project.version|
      value4=(project.version]""");

    importProject("""
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
                    """);

    compileModules("project");

    assertResult("target/classes/file.properties", """
      value1=1
      value2=1
      valueX=|
      value3=1
      value4=1""");
  }

  @Test
  public void testPomArtifactId() throws Exception {
    createProjectSubFile("resources/file.properties", "value=${pom.artifactId}");

    importProject("""
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
                    """);
    compileModules("project");

    assertResult("target/classes/file.properties", "value=project");
  }

  @Test
  public void testPomVersionInModules() throws Exception {
    createProjectSubFile("m1/resources/file.properties", "value=${pom.version}");

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m1</module>
                       </modules>
                       """);

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
                      """);
    importProject();

    compileModules("project", "m1");

    assertResult("m1/target/classes/file.properties", "value=2");
  }

  @Test
  public void testDoNotFilterSomeFileByDefault() throws Exception {
    createProjectSubFile("resources/file.bmp", "value=${project.version}");

    importProject("""
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
                    """);
    compileModules("project");

    assertResult("target/classes/file.bmp", "value=${project.version}");
  }

  @Test
  public void testCustomNonFilteredExtensions() throws Exception {
    createProjectSubFile("resources/file.bmp", "value=${project.version}");
    createProjectSubFile("resources/file.xxx", "value=${project.version}");

    importProject("""
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
                    """);
    compileModules("project");

    assertResult("target/classes/file.bmp", "value=${project.version}");
    assertResult("target/classes/file.xxx", "value=${project.version}");
  }

  @Test
  public void testFilteringTestResources() throws Exception {
    createProjectSubFile("resources/file.properties", "value=@project.version@");

    importProject("""
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
                    """);
    compileModules("project");

    assertResult("target/test-classes/file.properties", "value=1");
  }

  @Test
  public void testExcludesAndIncludes() throws Exception {
    createProjectSubFile("src/main/resources/file1.properties", "value=${project.artifactId}");
    createProjectSubFile("src/main/resources/file2.properties", "value=${project.artifactId}");

    importProject("""
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
                    """);

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

  @Test
  public void testEscapingWindowsChars() throws Exception {
    createProjectSubFile("resources/file.txt", """
      value=${foo}
      value2=@foo@
      value3=${bar}""");

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <properties>
                      <foo>c:\\projects\\foo/bar</foo>
                      <bar>a\\b\\c</bar>
                    </properties>
                    <build>
                      <resources>
                        <resource>
                          <directory>resources</directory>
                          <filtering>true</filtering>
                        </resource>
                      </resources>
                    </build>
                    """);
    compileModules("project");

    assertResult("target/classes/file.txt", """
      value=c:\\\\projects\\\\foo/bar
      value2=c:\\\\projects\\\\foo/bar
      value3=a\\b\\c""");
  }

  @Test
  public void testDontEscapingWindowsChars() throws Exception {
    createProjectSubFile("resources/file.txt", "value=${foo}");

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <properties>
                      <foo>c:\\projects\\foo/bar</foo>
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
                    """);
    compileModules("project");

    assertResult("target/classes/file.txt", "value=c:\\projects\\foo/bar");
  }

  @Test
  public void testFilteringPropertiesWithEmptyValues() throws Exception {
    createProjectSubFile("resources/file.properties", "value1=${foo}\nvalue2=${bar}");

    importProject("""
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
                    """);
    compileModules("project");

    assertResult("target/classes/file.properties", "value1=\nvalue2=${bar}");
  }

  @Test
  public void testFilterWithSeveralResourceFolders() throws Exception {
    createProjectSubFile("resources1/file1.properties", "value=${project.version}");
    createProjectSubFile("resources2/file2.properties", "value=${project.version}");

    importProject("""
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
                    """);
    compileModules("project");

    assertResult("target/classes/file1.properties", "value=1");
    assertResult("target/classes/file2.properties", "value=1");
  }

  @Test
  public void testFilterWithSeveralModules() throws Exception {
    createProjectSubFile("module1/resources/file1.properties", "value=${project.version}");
    createProjectSubFile("module2/resources/file2.properties", "value=${project.version}");

    VirtualFile m1 = createModulePom("module1",
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
                                       """);

    VirtualFile m2 = createModulePom("module2",
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
                                       """);

    importProjects(m1, m2);
    compileModules("module1", "module2");

    assertResult(m1, "target/classes/file1.properties", "value=1");
    assertResult(m2, "target/classes/file2.properties", "value=2");
  }

  @Test
  public void testDoNotFilterIfNotRequested() throws Exception {
    createProjectSubFile("resources1/file1.properties", "value=${project.version}");
    createProjectSubFile("resources2/file2.properties", "value=${project.version}");

    importProject("""
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
                    """);
    compileModules("project");

    assertResult("target/classes/file1.properties", "value=1");
    assertResult("target/classes/file2.properties", "value=${project.version}");
  }

  @Test
  public void testDoNotChangeFileIfPropertyIsNotResolved() throws Exception {
    createProjectSubFile("resources/file.properties", "value=${foo.bar}");

    importProject("""
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
                    """);
    compileModules("project");

    assertResult("target/classes/file.properties", "value=${foo.bar}");
  }

  @Test
  public void testChangingResolvedPropsBackWhenSettingsIsChange() throws Exception {
    createProjectSubFile("resources/file.properties", "value=${project.version}");

    importProject("""
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
                    """);
    compileModules("project");
    assertResult("target/classes/file.properties", "value=1");

    createProjectPom("""
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
                       """);
    importProject();
    compileModules("project");

    assertResult("target/classes/file.properties", "value=${project.version}");
  }

  @Test
  public void testUpdatingWhenPropertiesInFiltersAreChanged() throws Exception {
    final VirtualFile filter = createProjectSubFile("filters/filter.properties", "xxx=1");
    createProjectSubFile("resources/file.properties", "value=${xxx}");

    importProject("""
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
                    """);
    compileModules("project");
    assertResult("target/classes/file.properties", "value=1");

    WriteAction.runAndWait(() -> VfsUtil.saveText(filter, "xxx=2"));
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    compileModules("project");
    assertResult("target/classes/file.properties", "value=2");
  }

  @Test
  public void testUpdatingWhenPropertiesAreChanged() throws Exception {
    createProjectSubFile("resources/file.properties", "value=${foo}");

    importProject("""
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
                    """);
    compileModules("project");
    assertResult("target/classes/file.properties", "value=val1");

    importProject("""
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
                    """);
    compileModules("project");
    assertResult("target/classes/file.properties", "value=val2");
  }

  @Test
  public void testUpdatingWhenPropertiesInModelAreChanged() throws Exception {
    createProjectSubFile("resources/file.properties", "value=${project.name}");

    importProject("""
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
                    """);
    compileModules("project");
    assertResult("target/classes/file.properties", "value=val1");

    importProject("""
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
                    """);
    compileModules("project");
    assertResult("target/classes/file.properties", "value=val2");
  }

  @Test
  public void testUpdatingWhenProfilesAreChanged() throws Exception {
    createProjectSubFile("resources/file.properties", "value=${foo}");

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
                       """);
    importProjectWithProfiles("one");
    compileModules("project");
    assertResult("target/classes/file.properties", "value=val1");

    if(isNewImportingProcess) {
      importProjectWithProfiles("two");
    } else {
      myProjectsManager.setExplicitProfiles(new MavenExplicitProfiles(Arrays.asList("two")));
      scheduleResolveAll();
      resolveDependenciesAndImport();
    }

    compileModules("project");
    assertResult("target/classes/file.properties", "value=val2");
  }

  @Test
  public void testSameFileInSourcesAndTestSources() throws Exception {
    createProjectSubFile("src/main/resources/file.properties", "foo=${foo.main}");
    createProjectSubFile("src/test/resources/file.properties", "foo=${foo.test}");

    importProject("""
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
                    """);
    compileModules("project");

    assertResult("target/classes/file.properties", "foo=main");
    assertResult("target/test-classes/file.properties", "foo=test");
  }

  @Test
  public void testCustomFilters() throws Exception {
    createProjectSubFile("filters/filter1.properties",
                         """
                           xxx=value
                           yyy=${project.version}
                           """);
    createProjectSubFile("filters/filter2.properties", "zzz=value2");
    createProjectSubFile("resources/file.properties",
                         """
                           value1=${xxx}
                           value2=${yyy}
                           value3=${zzz}
                           """);

    importProject("""
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
                    """);
    compileModules("project");

    assertResult("target/classes/file.properties", """
      value1=value
      value2=1
      value3=value2
      """);
  }

  @Test
  public void testCustomFiltersViaPlugin() throws Exception {
    createProjectSubFile("filters/filter.properties", "xxx=value");
    createProjectSubFile("resources/file.properties", "value1=${xxx}");

    importProject("""
                    <groupId>test</groupId><artifactId>project</artifactId><version>1</version><build>  <plugins>
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
                        </plugin>  </plugins>
                      <resources>    <resource>      <directory>resources</directory>      <filtering>true</filtering>    </resource>  </resources></build>""");
    compileModules("project");

    assertResult("target/classes/file.properties", "value1=value");
  }

  @Test
  public void testCustomFilterWithPropertyInThePath() throws Exception {
    createProjectSubFile("filters/filter.properties", "xxx=value");
    createProjectSubFile("resources/file.properties", "value=${xxx}");

    importProject("<groupId>test</groupId>\n" +
                  "<artifactId>project</artifactId>\n" +
                  "<version>1</version>\n" +

                  "<properties>\n" +
                  " <some.path>\n" + getProjectPath() + "/filters</some.path>\n" +
                  "</properties>\n" +

                  "<build>\n" +
                  "  <filters>\n" +
                  "    <filter>${some.path}/filter.properties</filter>\n" +
                  "  </filters>\n" +
                  "  <resources>\n" +
                  "    <resource>\n" +
                  "      <directory>resources</directory>\n" +
                  "      <filtering>true</filtering>\n" +
                  "    </resource>\n" +
                  "  </resources>\n" +
                  "</build>\n");
    compileModules("project");

    assertResult("target/classes/file.properties", "value=value");
  }

  @Test
  public void testCustomFiltersFromProfiles() throws Exception {
    createProjectSubFile("filters/filter1.properties", "xxx=value1");
    createProjectSubFile("filters/filter2.properties", "yyy=value2");
    createProjectSubFile("resources/file.properties",
                         """
                           value1=${xxx}
                           value2=${yyy}
                           """);

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
                       """);

    importProjectWithProfiles("one");
    compileModules("project");
    assertResult("target/classes/file.properties", """
      value1=value1
      value2=${yyy}
      """);

    importProjectWithProfiles("two");
    compileModules("project");
    assertResult("target/classes/file.properties", """
      value1=${xxx}
      value2=value2
      """);
  }

  @Test
  public void testEscapingFiltering() throws Exception {
    createProjectSubFile("filters/filter.properties", "xxx=value");
    createProjectSubFile("resources/file.properties",
                         """
                           value1=\\${xxx}
                           value2=\\\\${xxx}
                           value3=\\\\\\${xxx}
                           value3=\\\\\\\\${xxx}
                           value4=.\\.\\\\.\\\\\\.""");

    importProject("""
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
                            <escapeString>\\</escapeString>
                          </configuration>
                        </plugin>
                      </plugins>
                    </build>
                    """);

    compileModules("project");
    assertResult("target/classes/file.properties",
                 """
                   value1=${xxx}
                   value2=\\\\value
                   value3=\\\\${xxx}
                   value3=\\\\\\\\value
                   value4=.\\.\\\\.\\\\\\.""");
  }

  @Test
  public void testPropertyPriority() throws Exception {
    createProjectSubFile("filters/filter.properties", "xxx=fromFilterFile\n" +
                                                      "yyy=fromFilterFile");
    createProjectSubFile("resources/file.properties","value1=${xxx}\n" +
                                                     "value2=${yyy}");

    importProject("""
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
                    """);

    compileModules("project");
    assertResult("target/classes/file.properties",
                 "value1=fromProperties\n" +
                 "value2=fromFilterFile");
  }

  @Test
  public void testCustomEscapingFiltering() throws Exception {
    createProjectSubFile("filters/filter.properties", "xxx=value");
    createProjectSubFile("resources/file.properties",
                         """
                           value1=^${xxx}
                           value2=\\${xxx}
                           """);

    importProject("""
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
                    """);

    compileModules("project");
    assertResult("target/classes/file.properties",
                 """
                   value1=${xxx}
                   value2=\\value
                   """);
  }

  @Test
  public void testDoNotFilterButCopyBigFiles() throws IOException {
    assertEquals(FileTypes.UNKNOWN, FileTypeManager.getInstance().getFileTypeByFileName("file.xyz"));

    WriteAction.runAndWait(() -> createProjectSubFile("resources/file.xyz").setBinaryContent(new byte[1024 * 1024 * 20]));

    importProject("""
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
                    """);
    compileModules("project");

    assertNotNull(myProjectPom.getParent().findFileByRelativePath("target/classes/file.xyz"));
  }

  @Test
  public void testResourcesOrdering1() throws Exception {
    createProjectSubFile("resources/file.properties", "value=${project.version}\n");

    importProject("""
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
                    """);

    compileModules("project");

    assertResult("target/classes/file.properties", "value=1\n"); // Filtered file override non-filtered file
  }

  @Test
  public void testResourcesOrdering2() throws Exception {

    createProjectSubFile("resources/file.properties", "value=${project.version}\n");

    importProject("""
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
                    """);

    compileModules("project");

    assertResult("target/classes/file.properties", "value=1\n"); // Filtered file override non-filtered file
  }

  @Test
  public void testResourcesOrdering3() throws Exception {

    createProjectSubFile("resources1/a.txt", "1");
    createProjectSubFile("resources2/a.txt", "2");

    importProject("""
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
                    """);

    compileModules("project");

    assertResult("target/classes/a.txt", "1"); // First file was copied, second file was not override first file
  }

  @Test
  public void testResourcesOrdering4() throws Exception {
    createProjectSubFile("resources1/a.txt", "1");
    createProjectSubFile("resources2/a.txt", "2");

    importProject("""
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
                    """);

    compileModules("project");

    assertResult("target/classes/a.txt", "2"); // For the filtered files last file override other files.
  }

  @Test
  public void testOverwriteParameter1() throws Exception {

    createProjectSubFile("resources1/a.txt", "1");
    createProjectSubFile("resources2/a.txt", "2");

    importProject("""
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
                    """);

    compileModules("project");

    assertResult("target/classes/a.txt", "2");
  }

  @Test
  public void testOverwriteParameter2() throws Exception {

    createProjectSubFile("resources1/a.txt", "1");
    createProjectSubFile("resources2/a.txt", "2");

    importProject("""
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
                    """);

    compileModules("project");

    assertResult("target/classes/a.txt", "2");
  }

}
