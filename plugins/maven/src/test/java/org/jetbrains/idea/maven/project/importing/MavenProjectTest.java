/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package org.jetbrains.idea.maven.project.importing;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.MavenImportingTestCase;
import org.jetbrains.idea.maven.model.MavenArtifactNode;
import org.jetbrains.idea.maven.model.MavenPlugin;
import org.jetbrains.idea.maven.model.MavenRemoteRepository;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.utils.MavenJDOMUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class MavenProjectTest extends MavenImportingTestCase {
  public void testCollectingPlugins() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +
                  "<build>" +
                  "  <plugins>" +
                  "    <plugin>" +
                  "      <groupId>group1</groupId>" +
                  "      <artifactId>id1</artifactId>" +
                  "      <version>1</version>" +
                  "    </plugin>" +
                  "    <plugin>" +
                  "      <groupId>group1</groupId>" +
                  "      <artifactId>id2</artifactId>" +
                  "    </plugin>" +
                  "    <plugin>" +
                  "      <groupId>group2</groupId>" +
                  "      <artifactId>id1</artifactId>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");

    assertModules("project");

    assertDeclaredPlugins(p("group1", "id1"), p("group1", "id2"), p("group2", "id1"));
  }
            
  public void testPluginsContainDefaultPlugins() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +
                  "<build>" +
                  "  <plugins>" +
                  "    <plugin>" +
                  "      <groupId>group1</groupId>" +
                  "      <artifactId>id1</artifactId>" +
                  "      <version>1</version>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");

    assertModules("project");

    assertContain(p(getMavenProject().getPlugins()), p("group1", "id1"), p("org.apache.maven.plugins", "maven-compiler-plugin"));
  }

  public void testDefaultPluginsAsDeclared() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +
                  "<build>" +
                  "  <plugins>" +
                  "    <plugin>" +
                  "      <groupId>org.apache.maven.plugins</groupId>" +
                  "      <artifactId>maven-compiler-plugin</artifactId>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");

    assertModules("project");

    assertDeclaredPlugins(p("org.apache.maven.plugins", "maven-compiler-plugin"));
  }

  public void testDoNotDuplicatePluginsFromBuildAndManagement() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +
                  "<build>" +
                  "  <plugins>" +
                  "    <plugin>" +
                  "      <groupId>org.apache.maven.plugins</groupId>" +
                  "      <artifactId>maven-compiler-plugin</artifactId>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "  <pluginManagement>" +
                  "    <plugins>" +
                  "      <plugin>" +
                  "        <groupId>org.apache.maven.plugins</groupId>" +
                  "        <artifactId>maven-compiler-plugin</artifactId>" +
                  "      </plugin>" +
                  "    </plugins>" +
                  "  </pluginManagement>" +
                  "</build>");

    assertModules("project");

    assertDeclaredPlugins(p("org.apache.maven.plugins", "maven-compiler-plugin"));
  }

  public void testCollectingPluginsFromProfilesAlso() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <plugins>" +
                  "    <plugin>" +
                  "      <groupId>group</groupId>" +
                  "      <artifactId>id</artifactId>" +
                  "      <version>1</version>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>" +

                  "<profiles>" +
                  "  <profile>" +
                  "    <id>profile1</id>" +
                  "    <build>" +
                  "      <plugins>" +
                  "        <plugin>" +
                  "          <groupId>group1</groupId>" +
                  "          <artifactId>id1</artifactId>" +
                  "        </plugin>" +
                  "      </plugins>" +
                  "    </build>" +
                  "  </profile>" +
                  "  <profile>" +
                  "    <id>profile2</id>" +
                  "    <build>" +
                  "      <plugins>" +
                  "        <plugin>" +
                  "          <groupId>group2</groupId>" +
                  "          <artifactId>id2</artifactId>" +
                  "        </plugin>" +
                  "      </plugins>" +
                  "    </build>" +
                  "  </profile>" +
                  "</profiles>");

    assertModules("project");

    assertDeclaredPlugins(p("group", "id"));

    importProjectWithProfiles("profile1");
    assertDeclaredPlugins(p("group", "id"), p("group1", "id1"));

    importProjectWithProfiles("profile2");
    assertDeclaredPlugins(p("group", "id"), p("group2", "id2"));

    importProjectWithProfiles("profile1", "profile2");
    assertDeclaredPlugins(p("group", "id"), p("group1", "id1"), p("group2", "id2"));
  }

  public void testFindingPlugin() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <plugins>" +
                  "    <plugin>" +
                  "      <groupId>group</groupId>" +
                  "      <artifactId>id</artifactId>" +
                  "      <version>1</version>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>" +

                  "<profiles>" +
                  "  <profile>" +
                  "    <id>profile1</id>" +
                  "    <build>" +
                  "      <plugins>" +
                  "        <plugin>" +
                  "          <groupId>group1</groupId>" +
                  "          <artifactId>id1</artifactId>" +
                  "        </plugin>" +
                  "      </plugins>" +
                  "    </build>" +
                  "  </profile>" +
                  "  <profile>" +
                  "    <id>profile2</id>" +
                  "    <build>" +
                  "      <plugins>" +
                  "        <plugin>" +
                  "          <groupId>group2</groupId>" +
                  "          <artifactId>id2</artifactId>" +
                  "        </plugin>" +
                  "      </plugins>" +
                  "    </build>" +
                  "  </profile>" +
                  "</profiles>");

    assertModules("project");

    assertEquals(p("group", "id"), p(findPlugin("group", "id")));
    assertNull(findPlugin("group1", "id1"));

    importProjectWithProfiles("profile1");
    assertEquals(p("group1", "id1"), p(findPlugin("group1", "id1")));
    assertNull(findPlugin("group2", "id2"));
  }

  public void testFindingDefaultPlugin() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <plugins>" +
                  "    <plugin>" +
                  "      <groupId>group</groupId>" +
                  "      <artifactId>id</artifactId>" +
                  "      <version>1</version>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");

    assertModules("project");

    assertNotNull(findPlugin("group", "id"));
    assertNotNull(findPlugin("org.apache.maven.plugins", "maven-compiler-plugin"));
  }

  public void testFindingMavenGroupPluginWithDefaultPluginGroup() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <plugins>" +
                  "    <plugin>" +
                  "      <artifactId>some.plugin.id</artifactId>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");
    assertModules("project");

    assertEquals(p("org.apache.maven.plugins", "some.plugin.id"),
                 p(findPlugin("org.apache.maven.plugins", "some.plugin.id")));
    assertNull(findPlugin("some.other.group.id", "some.plugin.id"));
  }

  public void testPluginConfiguration() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <plugins>" +
                  "    <plugin>" +
                  "      <groupId>group</groupId>" +
                  "      <artifactId>id1</artifactId>" +
                  "      <version>1</version>" +
                  "    </plugin>" +
                  "    <plugin>" +
                  "      <groupId>group</groupId>" +
                  "      <artifactId>id2</artifactId>" +
                  "      <version>1</version>" +
                  "      <configuration>" +
                  "      </configuration>" +
                  "    </plugin>" +
                  "    <plugin>" +
                  "      <groupId>group</groupId>" +
                  "      <artifactId>id3</artifactId>" +
                  "      <version>1</version>" +
                  "      <configuration>" +
                  "        <one>" +
                  "          <two>foo</two>" +
                  "        </one>" +
                  "      </configuration>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");

    assertNull(findPluginConfig("group", "id1", "one.two"));
    assertNull(findPluginConfig("group", "id2", "one.two"));
    assertEquals("foo", findPluginConfig("group", "id3", "one.two"));
    assertNull(findPluginConfig("group", "id3", "one.two.three"));
  }

  public void testPluginGoalConfiguration() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <plugins>" +
                  "    <plugin>" +
                  "      <groupId>group</groupId>" +
                  "      <artifactId>id</artifactId>" +
                  "      <version>1</version>" +
                  "      <executions>" +
                  "        <execution>" +
                  "          <id>a</id>" +
                  "          <goals>" +
                  "            <goal>compile</goal>" +
                  "          </goals>" +
                  "          <configuration>" +
                  "            <one>" +
                  "              <two>a</two>" +
                  "            </one>" +
                  "          </configuration>" +
                  "        </execution>" +
                  "        <execution>" +
                  "          <id>b</id>" +
                  "          <goals>" +
                  "            <goal>testCompile</goal>" +
                  "          </goals>" +
                  "          <configuration>" +
                  "            <one>" +
                  "              <two>b</two>" +
                  "            </one>" +
                  "          </configuration>" +
                  "        </execution>" +
                  "      </executions>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");

    assertNull(findPluginGoalConfig("group", "id", "package", "one.two"));
    assertEquals("a", findPluginGoalConfig("group", "id", "compile", "one.two"));
    assertEquals("b", findPluginGoalConfig("group", "id", "testCompile", "one.two"));
  }

  public void testPluginConfigurationHasResolvedVariables() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<properties>" +
                  "  <some.path>somePath</some.path>" +
                  "</properties>" +

                  "<build>" +
                  "  <plugins>" +
                  "    <plugin>" +
                  "      <groupId>group</groupId>" +
                  "      <artifactId>id</artifactId>" +
                  "      <version>1</version>" +
                  "      <configuration>" +
                  "        <one>${some.path}</one>" +
                  "      </configuration>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");

    assertEquals("somePath", findPluginConfig("group", "id", "one"));
  }

  public void testPluginConfigurationWithStandardVariable() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <plugins>" +
                  "    <plugin>" +
                  "      <groupId>group</groupId>" +
                  "      <artifactId>id</artifactId>" +
                  "      <version>1</version>" +
                  "      <configuration>" +
                  "        <one>${project.build.directory}</one>" +
                  "      </configuration>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");

    assertEquals(getProjectPath() + "/target",
                 FileUtil.toSystemIndependentName(findPluginConfig("group", "id", "one")));
  }

  public void testPluginConfigurationWithColons() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +
                  "<build>" +
                  "  <plugins>" +
                  "    <plugin>" +
                  "      <groupId>group</groupId>" +
                  "      <artifactId>id</artifactId>" +
                  "      <version>1</version>" +
                  "      <configuration>" +
                  "        <two:three>xxx</two:three>" +
                  "      </configuration>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");

    assertNull(findPluginConfig("group", "id", "two:three"));
  }

  public void testMergingPluginConfigurationFromBuildAndProfiles() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<profiles>" +
                     "  <profile>" +
                     "    <id>one</id>" +
                     "    <build>" +
                     "      <plugins>" +
                     "        <plugin>" +
                     "          <groupId>org.apache.maven.plugins</groupId>" +
                     "          <artifactId>maven-compiler-plugin</artifactId>" +
                     "          <configuration>" +
                     "            <target>1.4</target>" +
                     "          </configuration>" +
                     "        </plugin>" +
                     "      </plugins>" +
                     "    </build>" +
                     "  </profile>" +
                     "  <profile>" +
                     "    <id>two</id>" +
                     "    <build>" +
                     "      <plugins>" +
                     "        <plugin>" +
                     "          <groupId>org.apache.maven.plugins</groupId>" +
                     "          <artifactId>maven-compiler-plugin</artifactId>" +
                     "          <configuration>" +
                     "            <source>1.4</source>" +
                     "          </configuration>" +
                     "        </plugin>" +
                     "      </plugins>" +
                     "    </build>" +
                     "  </profile>" +
                     "</profiles>" +

                     "<build>" +
                     "  <plugins>" +
                     "    <plugin>" +
                     "      <groupId>org.apache.maven.plugins</groupId>" +
                     "      <artifactId>maven-compiler-plugin</artifactId>" +
                     "      <configuration>" +
                     "        <debug>true</debug>" +
                     "      </configuration>" +
                     "    </plugin>" +
                     "  </plugins>" +
                     "</build>");
    importProjectWithProfiles("one", "two");

    MavenPlugin plugin = findPlugin("org.apache.maven.plugins", "maven-compiler-plugin");
    assertEquals("1.4", plugin.getConfigurationElement().getChildText("source"));
    assertEquals("1.4", plugin.getConfigurationElement().getChildText("target"));
    assertEquals("true", plugin.getConfigurationElement().getChildText("debug"));
  }

  public void testCompilerPluginConfigurationFromProperties() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<properties>\n" +
                     "        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>\n" +
                     "        <maven.compiler.source>1.7</maven.compiler.source>\n" +
                     "        <maven.compiler.target>1.7</maven.compiler.target>\n" +
                     "</properties>");

    importProject();

    assertEquals("1.7", getMavenProject().getSourceLevel());
    assertEquals("1.7", getMavenProject().getTargetLevel());
  }

  public void testCompilerPluginConfigurationFromPropertiesOverride() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<properties>\n" +
                     "        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>\n" +
                     "        <maven.compiler.source>1.7</maven.compiler.source>\n" +
                     "        <maven.compiler.target>1.7</maven.compiler.target>\n" +
                     "</properties>" +

                     "<build>" +
                     "  <plugins>" +
                     "    <plugin>" +
                     "      <groupId>org.apache.maven.plugins</groupId>" +
                     "      <artifactId>maven-compiler-plugin</artifactId>" +
                     "      <configuration>" +
                     "        <target>1.4</target>" +
                     "        <source>1.4</source>" +
                     "      </configuration>" +
                     "    </plugin>" +
                     "  </plugins>" +
                     "</build>");

    importProject();

    assertEquals("1.4", getMavenProject().getSourceLevel());
    assertEquals("1.4", getMavenProject().getTargetLevel());
  }

  public void testMergingPluginConfigurationFromBuildProfilesAndPluginsManagement() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<profiles>" +
                     "  <profile>" +
                     "    <id>one</id>" +
                     "    <build>" +
                     "      <plugins>" +
                     "        <plugin>" +
                     "          <groupId>org.apache.maven.plugins</groupId>" +
                     "          <artifactId>maven-compiler-plugin</artifactId>" +
                     "          <configuration>" +
                     "            <target>1.4</target>" +
                     "          </configuration>" +
                     "        </plugin>" +
                     "      </plugins>" +
                     "    </build>" +
                     "  </profile>" +
                     "</profiles>" +

                     "<build>" +
                     "  <plugins>" +
                     "    <plugin>" +
                     "      <groupId>org.apache.maven.plugins</groupId>" +
                     "      <artifactId>maven-compiler-plugin</artifactId>" +
                     "      <configuration>" +
                     "        <debug>true</debug>" +
                     "      </configuration>" +
                     "    </plugin>" +
                     "  </plugins>" +
                     "  <pluginManagement>" +
                     "    <plugins>" +
                     "      <plugin>" +
                     "        <groupId>org.apache.maven.plugins</groupId>" +
                     "        <artifactId>maven-compiler-plugin</artifactId>" +
                     "        <configuration>" +
                     "          <source>1.4</source>" +
                     "        </configuration>" +
                     "      </plugin>" +
                     "    </plugins>" +
                     "  </pluginManagement>" +
                     "</build>");
    importProjectWithProfiles("one");

    MavenPlugin plugin = findPlugin("org.apache.maven.plugins", "maven-compiler-plugin");
    assertEquals("1.4", plugin.getConfigurationElement().getChildText("source"));
    assertEquals("1.4", plugin.getConfigurationElement().getChildText("target"));
    assertEquals("true", plugin.getConfigurationElement().getChildText("debug"));
  }

  public void testDoesNotCollectProfilesWithoutId() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<profiles>" +
                  "  <profile>" +
                  "    <id>one</id>" +
                  "  </profile>" +
                  "  <profile>" +
                  "  </profile>" +
                  "</profiles>");

    assertOrderedElementsAreEqual(getMavenProject().getProfilesIds(), "one");
  }

  public void testCollectingRepositories() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<repositories>" +
                  "  <repository>" +
                  "    <id>one</id>" +
                  "    <url>http://repository.one.com</url>" +
                  "  </repository>" +
                  "  <repository>" +
                  "    <id>two</id>" +
                  "    <url>http://repository.two.com</url>" +
                  "  </repository>" +
                  "</repositories>");

    List<MavenRemoteRepository> result = getMavenProject().getRemoteRepositories();
    assertEquals(3, result.size());
    assertEquals("one", result.get(0).getId());
    assertEquals("two", result.get(1).getId());
    assertEquals("central", result.get(2).getId());
  }

  public void testOverridingCentralRepository() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<repositories>" +
                  "  <repository>" +
                  "    <id>central</id>" +
                  "    <url>http://my.repository.com</url>" +
                  "  </repository>" +
                  "</repositories>");

    List<MavenRemoteRepository> result = getMavenProject().getRemoteRepositories();
    assertEquals(1, result.size());
    assertEquals("central", result.get(0).getId());
    assertEquals("http://my.repository.com", result.get(0).getUrl());
  }

  public void testCollectingRepositoriesFromParent() throws Exception {
    VirtualFile m1 = createModulePom("p1",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>p1</artifactId>" +
                                     "<version>1</version>" +
                                     "<packaging>pom</packaging>" +

                                     "<repositories>" +
                                     "  <repository>" +
                                     "    <id>one</id>" +
                                     "    <url>http://repository.one.com</url>" +
                                     "  </repository>" +
                                     "  <repository>" +
                                     "    <id>two</id>" +
                                     "    <url>http://repository.two.com</url>" +
                                     "  </repository>" +
                                     "</repositories>");

    VirtualFile m2 = createModulePom("p2",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>p2</artifactId>" +
                                     "<version>1</version>" +

                                     "<parent>" +
                                     "  <groupId>test</groupId>" +
                                     "  <artifactId>p1</artifactId>" +
                                     "  <version>1</version>" +
                                     "</parent>");

    importProjects(m1, m2);

    List<MavenRemoteRepository> result = myProjectsTree.getRootProjects().get(0).getRemoteRepositories();
    assertEquals(3, result.size());
    assertEquals("one", result.get(0).getId());
    assertEquals("two", result.get(1).getId());
    assertEquals("central", result.get(2).getId());

    result = myProjectsTree.getRootProjects().get(1).getRemoteRepositories();
    assertEquals(3, result.size());
    assertEquals("one", result.get(0).getId());
    assertEquals("two", result.get(1).getId());
    assertEquals("central", result.get(2).getId());
  }

  public void testMavenModelMap() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +
                  "<build>" +
                  "  <finalName>foo</finalName>" +
                  "  <plugins>" +
                  "    <plugin>" +
                  "      <groupId>group1</groupId>" +
                  "      <artifactId>id1</artifactId>" +
                  "      <version>1</version>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");

    MavenProject p = getMavenProject();
    Map<String,String> map = p.getModelMap();

    assertEquals("test", map.get("groupId"));
    assertEquals("foo", map.get("build.finalName"));
    assertEquals(new File(p.getDirectory(), "target").toString(), map.get("build.directory"));
    assertEquals(null, map.get("build.plugins"));
    assertEquals(null, map.get("build.pluginMap"));
  }

  public void testDependenciesTree() throws Exception {
    VirtualFile m1 = createModulePom("p1",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>m1</artifactId>" +
                                     "<version>1</version>" +

                                     "<dependencies>" +
                                     "  <dependency>" +
                                     "    <groupId>test</groupId>" +
                                     "    <artifactId>m2</artifactId>" +
                                     "    <version>1</version>" +
                                     "  </dependency>" +
                                     "  <dependency>" +
                                     "    <groupId>test</groupId>" +
                                     "    <artifactId>lib1</artifactId>" +
                                     "    <version>1</version>" +
                                     "  </dependency>" +
                                     "</dependencies>");

    VirtualFile m2 = createModulePom("p2",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>m2</artifactId>" +
                                     "<version>1</version>" +

                                     "<dependencies>" +
                                     "  <dependency>" +
                                     "    <groupId>junit</groupId>" +
                                     "    <artifactId>junit</artifactId>" +
                                     "    <version>4.0</version>" +
                                     "  </dependency>" +
                                     "  <dependency>" +
                                     "    <groupId>test</groupId>" +
                                     "    <artifactId>lib2</artifactId>" +
                                     "    <version>1</version>" +
                                     "  </dependency>" +
                                     "</dependencies>");

    importProjects(m1, m2);
    resolveDependenciesAndImport();

    assertDependenciesNodes(myProjectsTree.getRootProjects().get(0).getDependencyTree(),
                            "test:m2:jar:1->(junit:junit:jar:4.0->(),test:lib2:jar:1->()),test:lib1:jar:1->()");
  }

  public void testDependenciesTreeWithTypesAndClassifiers() throws Exception {
    VirtualFile m1 = createModulePom("p1",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>m1</artifactId>" +
                                     "<version>1</version>" +

                                     "<dependencies>" +
                                     "  <dependency>" +
                                     "    <groupId>test</groupId>" +
                                     "    <artifactId>m2</artifactId>" +
                                     "    <version>1</version>" +
                                     "    <type>pom</type>" +
                                     "    <classifier>test</classifier>" +
                                     "  </dependency>" +
                                     "</dependencies>");

    VirtualFile m2 = createModulePom("p2",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>m2</artifactId>" +
                                     "<version>1</version>" +

                                     "<dependencies>" +
                                     "  <dependency>" +
                                     "    <groupId>test</groupId>" +
                                     "    <artifactId>lib</artifactId>" +
                                     "    <version>1</version>" +
                                     "  </dependency>" +
                                     "</dependencies>");

    importProjects(m1, m2);
    resolveDependenciesAndImport();

    assertDependenciesNodes(myProjectsTree.getRootProjects().get(0).getDependencyTree(),
                            "test:m2:pom:test:1->(test:lib:jar:1->())");
  }

  public void testDependenciesTreeWithConflict() throws Exception {
    VirtualFile m1 = createModulePom("p1",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>m1</artifactId>" +
                                     "<version>1</version>" +

                                     "<dependencies>" +
                                     "  <dependency>" +
                                     "    <groupId>test</groupId>" +
                                     "    <artifactId>m2</artifactId>" +
                                     "    <version>1</version>" +
                                     "  </dependency>" +
                                     "  <dependency>" +
                                     "    <groupId>test</groupId>" +
                                     "    <artifactId>lib</artifactId>" +
                                     "    <version>1</version>" +
                                     "  </dependency>" +
                                     "</dependencies>");

    VirtualFile m2 = createModulePom("p2",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>m2</artifactId>" +
                                     "<version>1</version>" +

                                     "<dependencies>" +
                                     "  <dependency>" +
                                     "    <groupId>test</groupId>" +
                                     "    <artifactId>lib</artifactId>" +
                                     "    <version>2</version>" +
                                     "  </dependency>" +
                                     "</dependencies>");

    importProjects(m1, m2);
    resolveDependenciesAndImport();

    List<MavenArtifactNode> nodes = myProjectsTree.getRootProjects().get(0).getDependencyTree();
    assertDependenciesNodes(nodes,
                            "test:m2:jar:1->(test:lib:jar:2[CONFLICT:test:lib:jar:1]->())," +
                            "test:lib:jar:1->()");
    assertSame(nodes.get(0).getDependencies().get(0).getRelatedArtifact(),
               nodes.get(1).getArtifact());
  }

  public void testDependencyTreeDuplicates() throws Exception {
    VirtualFile m1 = createModulePom("p1",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>m1</artifactId>" +
                                     "<version>1</version>" +

                                     "<dependencies>" +
                                     "  <dependency>" +
                                     "    <groupId>test</groupId>" +
                                     "    <artifactId>m2</artifactId>" +
                                     "    <version>1</version>" +
                                     "  </dependency>" +
                                     "  <dependency>" +
                                     "    <groupId>test</groupId>" +
                                     "    <artifactId>m3</artifactId>" +
                                     "    <version>1</version>" +
                                     "  </dependency>" +
                                     "</dependencies>");

    VirtualFile m2 = createModulePom("p2",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>m2</artifactId>" +
                                     "<version>1</version>" +

                                     "<dependencies>" +
                                     "  <dependency>" +
                                     "    <groupId>test</groupId>" +
                                     "    <artifactId>lib</artifactId>" +
                                     "    <version>1</version>" +
                                     "  </dependency>" +
                                     "</dependencies>");

    VirtualFile m3 = createModulePom("p3",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>m3</artifactId>" +
                                     "<version>1</version>" +

                                     "<dependencies>" +
                                     "  <dependency>" +
                                     "    <groupId>test</groupId>" +
                                     "    <artifactId>lib</artifactId>" +
                                     "    <version>1</version>" +
                                     "  </dependency>" +
                                     "</dependencies>");

    importProjects(m1, m2, m3);
    resolveDependenciesAndImport();

    List<MavenArtifactNode> nodes = myProjectsTree.findProject(m1).getDependencyTree();
    assertDependenciesNodes(nodes, "test:m2:jar:1->(test:lib:jar:1->()),test:m3:jar:1->(test:lib:jar:1[DUPLICATE:test:lib:jar:1]->())");

    assertSame(nodes.get(0).getDependencies().get(0).getArtifact(),
               nodes.get(1).getDependencies().get(0).getRelatedArtifact());
  }

  protected void assertDependenciesNodes(List<MavenArtifactNode> nodes, String expected) {
    assertEquals(expected, MavenArtifactNode.formatNodesList(nodes));
  }

  private String findPluginConfig(String groupId, String artifactId, String path) {
    return MavenJDOMUtil.findChildValueByPath(getMavenProject().getPluginConfiguration(groupId, artifactId), path);
  }

  private String findPluginGoalConfig(String groupId, String artifactId, String goal, String path) {
    return MavenJDOMUtil.findChildValueByPath(getMavenProject().getPluginGoalConfiguration(groupId, artifactId, goal), path);
  }

  private void assertDeclaredPlugins(PluginInfo... expected) {
    assertUnorderedElementsAreEqual(p(getMavenProject().getDeclaredPlugins()), expected);
  }

  private MavenPlugin findPlugin(String groupId, String artifactId) {
    return getMavenProject().findPlugin(groupId, artifactId);
  }

  private MavenProject getMavenProject() {
    return myProjectsTree.getRootProjects().get(0);
  }

  private static PluginInfo p(String groupId, String artifactId) {
    return new PluginInfo(groupId, artifactId);
  }

  private static PluginInfo p(MavenPlugin mavenPlugin) {
    return new PluginInfo(mavenPlugin.getGroupId(), mavenPlugin.getArtifactId());
  }

  private List<PluginInfo> p(Collection<MavenPlugin> mavenPlugins) {
    List<PluginInfo> res = new ArrayList<>(mavenPlugins.size());
    for (MavenPlugin mavenPlugin : mavenPlugins) {
      res.add(p(mavenPlugin));
    }

    return res;
  }

  private static class PluginInfo {
    String groupId;
    String artifactId;

    private PluginInfo(String groupId, String artifactId) {
      this.groupId = groupId;
      this.artifactId = artifactId;
    }

    @Override
    public String toString() {
      return groupId + ":" + artifactId;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      PluginInfo info = (PluginInfo)o;

      if (artifactId != null ? !artifactId.equals(info.artifactId) : info.artifactId != null) return false;
      if (groupId != null ? !groupId.equals(info.groupId) : info.groupId != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = groupId != null ? groupId.hashCode() : 0;
      result = 31 * result + (artifactId != null ? artifactId.hashCode() : 0);
      return result;
    }
  }
}
