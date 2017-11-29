/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.MavenImportingTestCase;
import org.jetbrains.idea.maven.model.MavenArchetype;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.wizards.MavenModuleBuilder;

import java.util.List;

public class MavenModuleBuilderTest extends MavenImportingTestCase {
  private MavenModuleBuilder myBuilder;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myBuilder = new MavenModuleBuilder();

    createJdk();
    setModuleNameAndRoot("module", getProjectPath());
  }

  public void testCreatingBlank() {
    if (!hasMavenInstallation()) return;

    MavenId id = new MavenId("org.foo", "module", "1.0");
    createNewModule(id);

    List<MavenProject> projects = MavenProjectsManager.getInstance(myProject).getProjects();
    assertEquals(1, projects.size());

    MavenProject project = projects.get(0);
    assertEquals(id, project.getMavenId());

    assertModules("module");
    MavenProjectsManager.getInstance(myProject).isMavenizedModule(getModule("module"));
    assertSame(project, MavenProjectsManager.getInstance(myProject).findProject(getModule("module")));

    assertNotNull(myProjectRoot.findFileByRelativePath("src/main/java"));
    assertNotNull(myProjectRoot.findFileByRelativePath("src/test/java"));

    assertSources("module", "src/main/java");
    assertTestSources("module", "src/test/java");
  }

  public void testInheritJdkFromProject() {
    if (!hasMavenInstallation()) return;

    createNewModule(new MavenId("org.foo", "module", "1.0"));
    ModuleRootManager manager = ModuleRootManager.getInstance(getModule("module"));
    assertTrue(manager.isSdkInherited());
  }

  public void testCreatingFromArchetype() {
    if (!hasMavenInstallation()) return;

    setArchetype(new MavenArchetype("org.apache.maven.archetypes", "maven-archetype-quickstart", "1.0", null, null));
    MavenId id = new MavenId("org.foo", "module", "1.0");
    createNewModule(id);

    List<MavenProject> projects = MavenProjectsManager.getInstance(myProject).getProjects();
    assertEquals(1, projects.size());

    MavenProject project = projects.get(0);
    assertEquals(id, project.getMavenId());

    assertNotNull(myProjectRoot.findFileByRelativePath("src/main/java/org/foo/App.java"));
    assertNotNull(myProjectRoot.findFileByRelativePath("src/test/java/org/foo/AppTest.java"));

    assertSources("module", "src/main/java");
    assertTestSources("module", "src/test/java");
  }

  public void testAddingNewlyCreatedModuleToTheAggregator() throws Exception {
    if (!hasMavenInstallation()) return;

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    setModuleNameAndRoot("module", getProjectPath() + "/module");
    setAggregatorProject(myProjectPom);
    createNewModule(new MavenId("org.foo", "module", "1.0"));

    assertEquals(createPomXml("<groupId>test</groupId>" +
                              "<artifactId>project</artifactId>\n" +
                              "    <packaging>pom</packaging>\n" +
                              "    <version>1</version>\n" +
                              "    <modules>\n" +
                              "        <module>module</module>\n" +
                              "    </modules>\n"),
                 StringUtil.convertLineSeparators(VfsUtil.loadText(myProjectPom)));
  }

  public void testAddingManagedProjectIfNoArrgerator() {
    if (!hasMavenInstallation()) return;

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertEquals(1, myProjectsManager.getProjectsTreeForTests().getManagedFilesPaths().size());

    setModuleNameAndRoot("module", getProjectPath() + "/module");
    setAggregatorProject(null);
    createNewModule(new MavenId("org.foo", "module", "1.0"));
    myProjectRoot.findFileByRelativePath("module/pom.xml");

    assertEquals(2, myProjectsManager.getProjectsTreeForTests().getManagedFilesPaths().size());
  }

  public void testDoNotAddManagedProjectIfAddingAsModuleToAggregator() {
    if (!hasMavenInstallation()) return;

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertEquals(1, myProjectsManager.getProjectsTreeForTests().getManagedFilesPaths().size());

    setModuleNameAndRoot("module", getProjectPath() + "/module");
    setAggregatorProject(myProjectPom);
    createNewModule(new MavenId("org.foo", "module", "1.0"));
    myProjectRoot.findFileByRelativePath("module/pom.xml");

    assertEquals(1, myProjectsManager.getProjectsTreeForTests().getManagedFilesPaths().size());
  }

  public void testAddingParent() throws Exception {
    if (!hasMavenInstallation()) return;

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    setModuleNameAndRoot("module", getProjectPath() + "/module");
    setParentProject(myProjectPom);
    createNewModule(new MavenId("org.foo", "module", "1.0"));

    assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                 "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" +
                 "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                 "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
                 "    <parent>\n" +
                 "        <artifactId>project</artifactId>\n" +
                 "        <groupId>test</groupId>\n" +
                 "        <version>1</version>\n" +
                 "    </parent>\n" +
                 "    <modelVersion>4.0.0</modelVersion>\n" +
                 "\n" +
                 "    <groupId>org.foo</groupId>\n" +
                 "    <artifactId>module</artifactId>\n" +
                 "    <version>1.0</version>\n" +
                 "\n" +
                 "\n" +
                 "</project>",
                 VfsUtil.loadText(myProjectRoot.findFileByRelativePath("module/pom.xml")));
  }

  public void testAddingParentWithInheritedProperties() throws Exception {
    if (!hasMavenInstallation()) return;

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    setModuleNameAndRoot("module", getProjectPath() + "/module");
    setParentProject(myProjectPom);
    setInheritedOptions(true, true);
    createNewModule(new MavenId("org.foo", "module", "1.0"));

    assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                 "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" +
                 "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                 "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
                 "    <parent>\n" +
                 "        <artifactId>project</artifactId>\n" +
                 "        <groupId>test</groupId>\n" +
                 "        <version>1</version>\n" +
                 "    </parent>\n" +
                 "    <modelVersion>4.0.0</modelVersion>\n" +
                 "\n" +
                 "    <artifactId>module</artifactId>\n" +
                 "\n" +
                 "\n" +
                 "</project>",
                 VfsUtil.loadText(myProjectRoot.findFileByRelativePath("module/pom.xml")));
  }

  public void testAddingParentAndInheritWhenGeneratingFromArchetype() throws Exception {
    if (!hasMavenInstallation()) return;

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    setModuleNameAndRoot("module", getProjectPath() + "/module");
    setParentProject(myProjectPom);
    setInheritedOptions(true, true);
    setArchetype(new MavenArchetype("org.apache.maven.archetypes", "maven-archetype-quickstart", "1.0", null, null));
    createNewModule(new MavenId("org.foo", "module", "1.0"));

    assertEquals("<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                 "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
                 "    <parent>\n" +
                 "        <artifactId>project</artifactId>\n" +
                 "        <groupId>test</groupId>\n" +
                 "        <version>1</version>\n" +
                 "    </parent>\n" +
                 "    <modelVersion>4.0.0</modelVersion>\n" +
                 "\n" +
                 "    <artifactId>module</artifactId>\n" +
                 "    <packaging>jar</packaging>\n" +
                 "\n" +
                 "    <name>module</name>\n" +
                 "    <url>http://maven.apache.org</url>\n" +
                 "\n" +
                 "    <properties>\n" +
                 "        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>\n" +
                 "    </properties>\n" +
                 "\n" +
                 "    <dependencies>\n" +
                 "        <dependency>\n" +
                 "            <groupId>junit</groupId>\n" +
                 "            <artifactId>junit</artifactId>\n" +
                 "            <version>3.8.1</version>\n" +
                 "            <scope>test</scope>\n" +
                 "        </dependency>\n" +
                 "    </dependencies>\n" +
                 "</project>\n",
                 VfsUtil.loadText(myProjectRoot.findFileByRelativePath("module/pom.xml")));
  }

  public void testAddingParentWithRelativePath() throws Exception {
    if (!hasMavenInstallation()) return;

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    setModuleNameAndRoot("module", getProjectPath() + "/subDir/module");
    setParentProject(myProjectPom);
    createNewModule(new MavenId("org.foo", "module", "1.0"));

    assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                 "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" +
                 "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                 "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
                 "    <parent>\n" +
                 "        <artifactId>project</artifactId>\n" +
                 "        <groupId>test</groupId>\n" +
                 "        <version>1</version>\n" +
                 "        <relativePath>../../pom.xml</relativePath>\n" +
                 "    </parent>\n" +
                 "    <modelVersion>4.0.0</modelVersion>\n" +
                 "\n" +
                 "    <groupId>org.foo</groupId>\n" +
                 "    <artifactId>module</artifactId>\n" +
                 "    <version>1.0</version>\n" +
                 "\n" +
                 "\n" +
                 "</project>",
                 VfsUtil.loadText(myProjectRoot.findFileByRelativePath("subDir/module/pom.xml")));
  }

  private void setModuleNameAndRoot(String name, String root) {
    myBuilder.setName(name);
    myBuilder.setModuleFilePath(root + "/" + name + ".iml");
    myBuilder.setContentEntryPath(root);
  }

  private void setAggregatorProject(VirtualFile pom) {
    myBuilder.setAggregatorProject(pom == null ? null : myProjectsManager.findProject(pom));
  }

  private void setParentProject(VirtualFile pom) {
    myBuilder.setParentProject(myProjectsManager.findProject(pom));
  }

  private void setInheritedOptions(boolean groupId, boolean version) {
    myBuilder.setInheritedOptions(groupId, version);
  }

  private void setArchetype(MavenArchetype archetype) {
    myBuilder.setArchetype(archetype);
  }

  private void createNewModule(MavenId id) {
    myBuilder.setProjectId(id);

    new WriteAction() {
      protected void run(@NotNull Result result) throws Throwable {
        ModifiableModuleModel model = ModuleManager.getInstance(myProject).getModifiableModel();
        myBuilder.createModule(model);
        model.commit();
      }
    }.execute();

    resolveDependenciesAndImport();
  }
}
