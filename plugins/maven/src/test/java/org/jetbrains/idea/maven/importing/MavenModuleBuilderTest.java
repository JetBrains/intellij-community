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

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.RunAll;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.model.MavenArchetype;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.wizards.AbstractMavenModuleBuilder;
import org.jetbrains.idea.maven.wizards.InternalMavenModuleBuilder;
import org.junit.Assume;
import org.junit.Test;

import java.util.List;

public class MavenModuleBuilderTest extends MavenMultiVersionImportingTestCase {
  private AbstractMavenModuleBuilder myBuilder;

  @Override
  protected void tearDown() throws Exception {
    RunAll.runAll(
      () -> stopMavenImportManager(),
      () -> super.tearDown()
    );
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myBuilder = new InternalMavenModuleBuilder();

    createJdk();
    setModuleNameAndRoot("module", getProjectPath());
  }

  @Test
  public void testModuleRecreation() throws Exception {
    MavenId id = new MavenId("org.foo", "module", "1.0");

    createNewModule(id);
    assertModules(id.getArtifactId());
    deleteModule(id.getArtifactId());
    createNewModule(id);
    assertModules(id.getArtifactId());
  }

  @Test
  public void testCreatingBlank() throws Exception {
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

  @Test
  public void testInheritJdkFromProject() throws Exception {
    if (!hasMavenInstallation()) return;

    createNewModule(new MavenId("org.foo", "module", "1.0"));
    ModuleRootManager manager = ModuleRootManager.getInstance(getModule("module"));
    assertTrue(manager.isSdkInherited());
  }

  @Test
  public void testCreatingFromArchetype() throws Exception {
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

  @Test
  public void testAddingNewlyCreatedModuleToTheAggregator() throws Exception {
    if (!hasMavenInstallation()) return;

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """);

    setModuleNameAndRoot("module", getProjectPath() + "/module");
    setAggregatorProject(myProjectPom);
    createNewModule(new MavenId("org.foo", "module", "1.0"));

    assertEquals(createPomXml("""
                                <groupId>test</groupId><artifactId>project</artifactId>
                                    <packaging>pom</packaging>
                                    <version>1</version>
                                    <modules>
                                        <module>module</module>
                                    </modules>
                                """),
                 StringUtil.convertLineSeparators(VfsUtil.loadText(myProjectPom)));
  }

  @Test
  public void testAddingManagedProjectIfNoArrgerator() throws Exception {
    if (!hasMavenInstallation()) return;

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """);

    assertEquals(1, myProjectsManager.getProjectsTreeForTests().getManagedFilesPaths().size());

    setModuleNameAndRoot("module", getProjectPath() + "/module");
    setAggregatorProject(null);
    createNewModule(new MavenId("org.foo", "module", "1.0"));
    myProjectRoot.findFileByRelativePath("module/pom.xml");

    assertEquals(2, myProjectsManager.getProjectsTreeForTests().getManagedFilesPaths().size());
  }

  @Test
  public void testDoNotAddManagedProjectIfAddingAsModuleToAggregator() throws Exception {
    if (!hasMavenInstallation()) return;

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """);

    assertEquals(1, myProjectsManager.getProjectsTreeForTests().getManagedFilesPaths().size());

    setModuleNameAndRoot("module", getProjectPath() + "/module");
    setAggregatorProject(myProjectPom);
    createNewModule(new MavenId("org.foo", "module", "1.0"));
    myProjectRoot.findFileByRelativePath("module/pom.xml");

    assertEquals(1, myProjectsManager.getProjectsTreeForTests().getManagedFilesPaths().size());
  }

  @Test
  public void testAddingParent() throws Exception {
    if (!hasMavenInstallation()) return;

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """);

    setModuleNameAndRoot("module", getProjectPath() + "/module");
    setParentProject(myProjectPom);
    createNewModule(new MavenId("org.foo", "module", "1.0"));

    assertEquals("""
                   <?xml version="1.0" encoding="UTF-8"?>
                   <project xmlns="http://maven.apache.org/POM/4.0.0"
                            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                            xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                       <parent>
                           <artifactId>project</artifactId>
                           <groupId>test</groupId>
                           <version>1</version>
                       </parent>
                       <modelVersion>4.0.0</modelVersion>

                       <groupId>org.foo</groupId>
                       <artifactId>module</artifactId>
                       <version>1.0</version>


                   </project>""",
                 VfsUtil.loadText(myProjectRoot.findFileByRelativePath("module/pom.xml")));
  }

  @Test
  public void testAddingParentWithInheritedProperties() throws Exception {
    if (!hasMavenInstallation()) return;

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """);

    setModuleNameAndRoot("module", getProjectPath() + "/module");
    setParentProject(myProjectPom);
    setInheritedOptions(true, true);
    createNewModule(new MavenId("org.foo", "module", "1.0"));

    assertEquals("""
                   <?xml version="1.0" encoding="UTF-8"?>
                   <project xmlns="http://maven.apache.org/POM/4.0.0"
                            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                            xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                       <parent>
                           <artifactId>project</artifactId>
                           <groupId>test</groupId>
                           <version>1</version>
                       </parent>
                       <modelVersion>4.0.0</modelVersion>

                       <artifactId>module</artifactId>


                   </project>""",
                 VfsUtil.loadText(myProjectRoot.findFileByRelativePath("module/pom.xml")));
  }

  @Test
  public void testAddingParentAndInheritWhenGeneratingFromArchetype() throws Exception {
    if (!hasMavenInstallation()) return;

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """);

    setModuleNameAndRoot("module", getProjectPath() + "/module");
    setParentProject(myProjectPom);
    setInheritedOptions(true, true);
    setArchetype(new MavenArchetype("org.apache.maven.archetypes", "maven-archetype-quickstart", "1.0", null, null));
    createNewModule(new MavenId("org.foo", "module", "1.0"));

    assertEquals("""
                   <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                            xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                       <parent>
                           <artifactId>project</artifactId>
                           <groupId>test</groupId>
                           <version>1</version>
                       </parent>
                       <modelVersion>4.0.0</modelVersion>

                       <artifactId>module</artifactId>
                       <packaging>jar</packaging>

                       <name>module</name>
                       <url>http://maven.apache.org</url>

                       <properties>
                           <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                       </properties>

                       <dependencies>
                           <dependency>
                               <groupId>junit</groupId>
                               <artifactId>junit</artifactId>
                               <version>3.8.1</version>
                               <scope>test</scope>
                           </dependency>
                       </dependencies>
                   </project>
                   """,
                 VfsUtil.loadText(myProjectRoot.findFileByRelativePath("module/pom.xml")));
  }

  @Test
  public void testAddingParentWithRelativePath() throws Exception {
    if (!hasMavenInstallation()) return;

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """);

    setModuleNameAndRoot("module", getProjectPath() + "/subDir/module");
    setParentProject(myProjectPom);
    createNewModule(new MavenId("org.foo", "module", "1.0"));

    assertEquals("""
                   <?xml version="1.0" encoding="UTF-8"?>
                   <project xmlns="http://maven.apache.org/POM/4.0.0"
                            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                            xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                       <parent>
                           <artifactId>project</artifactId>
                           <groupId>test</groupId>
                           <version>1</version>
                           <relativePath>../../pom.xml</relativePath>
                       </parent>
                       <modelVersion>4.0.0</modelVersion>

                       <groupId>org.foo</groupId>
                       <artifactId>module</artifactId>
                       <version>1.0</version>


                   </project>""",
                 VfsUtil.loadText(myProjectRoot.findFileByRelativePath("subDir/module/pom.xml")));
  }

  @Test
  public void testSameFolderAsParent() throws Exception {
    Assume.assumeFalse(Registry.is("maven.linear.import"));

    VirtualFile customPomXml = createProjectSubFile("custompom.xml", createPomXml(
      """
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
        """));
    importProject(customPomXml);
    assertModules("project");

    setModuleNameAndRoot("module", getProjectPath());
    setParentProject(customPomXml);

    createNewModule(new MavenId("org.foo", "module", "1.0"));

    if (supportsImportOfNonExistingFolders()) {
      assertContentRoots("project",
                         getProjectPath() + "/src/main/java",
                         getProjectPath() + "/src/main/resources",
                         getProjectPath() + "/src/test/java",
                         getProjectPath() + "/src/test/resources"
      );
    }
    else {
      assertContentRoots("project",
                         getProjectPath() + "/src/main/java",
                         getProjectPath() + "/src/main/resources",
                         getProjectPath() + "/src/test/java"
      );
    }
    assertContentRoots("module",
                       getProjectPath());

    MavenProject module = MavenProjectsManager.getInstance(myProject).findProject(getModule("module"));

    MavenDomProjectModel domProjectModel = MavenDomUtil.getMavenDomProjectModel(myProject, module.getFile());
    assertEquals("custompom.xml", domProjectModel.getMavenParent().getRelativePath().getRawText());
  }

  private void deleteModule(String name) {
    ModuleManager moduleManger = ModuleManager.getInstance(myProject);
    Module module = moduleManger.findModuleByName(name);
    ModifiableModuleModel modifiableModuleModel = moduleManger.getModifiableModel();
    WriteAction.runAndWait(() -> {
      try {
        modifiableModuleModel.disposeModule(module);
      }
      finally {
        modifiableModuleModel.commit();
      }
    });
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

  private void createNewModule(MavenId id) throws Exception {
    myBuilder.setProjectId(id);

    WriteAction.runAndWait(() -> {
      ModifiableModuleModel model = ModuleManager.getInstance(myProject).getModifiableModel();
      myBuilder.createModule(model);
      model.commit();
    });

    resolveDependenciesAndImport();
  }
}
