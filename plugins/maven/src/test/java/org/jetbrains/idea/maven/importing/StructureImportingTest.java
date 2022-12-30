/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenProject;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class StructureImportingTest extends MavenMultiVersionImportingTestCase {
  @Test
  public void testInheritProjectJdkForModules() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertTrue(ModuleRootManager.getInstance(getModule("project")).isSdkInherited());
  }

  @Test
  public void testDoNotResetSomeSettingsAfterReimport() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    Sdk sdk = setupJdkForModule("project");

    importProject();

    if (supportsKeepingManualChanges()) {
      assertFalse(ModuleRootManager.getInstance(getModule("project")).isSdkInherited());
      assertEquals(sdk, ModuleRootManager.getInstance(getModule("project")).getSdk());
    }
    else {
      assertTrue(ModuleRootManager.getInstance(getModule("project")).isSdkInherited());
    }
  }

  @Test
  public void testImportWithAlreadyExistingModules() throws IOException {
    createModule("m1");
    createModule("m2");
    createModule("m3");

    PsiTestUtil.addSourceRoot(getModule("m1"), createProjectSubFile("m1/user-sources"));
    PsiTestUtil.addSourceRoot(getModule("m2"), createProjectSubFile("m2/user-sources"));
    PsiTestUtil.addSourceRoot(getModule("m3"), createProjectSubFile("m3/user-sources"));

    assertModules("m1", "m2", "m3");
    assertSources("m1", "user-sources");
    assertSources("m2", "user-sources");
    assertSources("m3", "user-sources");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "</modules>");

    createModulePom("m1", "<groupId>test</groupId>" +
                          "<artifactId>m1</artifactId>" +
                          "<version>1</version>");
    createModulePom("m2", "<groupId>test</groupId>" +
                          "<artifactId>m2</artifactId>" +
                          "<version>1</version>");

    createProjectSubDirs("m1/src/main/java",
                         "m2/src/main/java",
                         "m3/src/main/java");

    importProject();
    assertModules("project", "m1", "m2", "m3");

    if (supportsLegacyKeepingFoldersFromPreviousImport()) {
      assertSources("m1", "user-sources", "src/main/java");
      assertSources("m2", "user-sources", "src/main/java");
      assertSources("m3", "user-sources");
    }
    else {
      assertSources("m1", "src/main/java");
      assertSources("m2", "src/main/java");
      assertSources("m3", "user-sources");
    }
  }

  @Test
  public void testImportWithAlreadyExistingModuleWithDifferentNameButSameContentRoot() throws IOException {
    Assume.assumeTrue(isWorkspaceImport());

    Module userModuleWithConflictingRoot = createModule("userModuleWithConflictingRoot");
    PsiTestUtil.removeAllRoots(userModuleWithConflictingRoot, null);
    PsiTestUtil.addContentRoot(userModuleWithConflictingRoot, myProjectRoot);
    assertContentRoots(userModuleWithConflictingRoot.getName(), getProjectPath());

    Module userModuleWithUniqueRoot = createModule("userModuleWithUniqueRoot");
    assertContentRoots(userModuleWithUniqueRoot.getName(), getProjectPath() + "/userModuleWithUniqueRoot");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>");

    importProject();
    assertModules("project", userModuleWithUniqueRoot.getName());
    assertContentRoots("project", getProjectPath());
    assertContentRoots(userModuleWithUniqueRoot.getName(), getProjectPath() + "/userModuleWithUniqueRoot");
  }

  @Test
  public void testMarkModulesAsMavenized() {
    createModule("userModule");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "</modules>");

    createModulePom("m1", "<groupId>test</groupId>" +
                          "<artifactId>m1</artifactId>" +
                          "<version>1</version>");

    importProject();
    assertModules("project", "m1", "userModule");
    assertMavenizedModule("project");
    assertMavenizedModule("m1");
    assertNotMavenizedModule("userModule");

    configConfirmationForYesAnswer();
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m2</module>" +
                     "</modules>");

    createModulePom("m2", "<groupId>test</groupId>" +
                          "<artifactId>m2</artifactId>" +
                          "<version>1</version>");

    importProject();
    assertModules("project", "m2", "userModule");
    assertMavenizedModule("project");
    assertMavenizedModule("m2");
    assertNotMavenizedModule("userModule");
  }


  @Test
  public void testModulesWithSlashesRegularAndBack() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>dir\\m1</module>" +
                     "  <module>dir/m2</module>" +
                     "</modules>");

    createModulePom("dir/m1", "<groupId>test</groupId>" +
                              "<artifactId>m1</artifactId>" +
                              "<version>1</version>");

    createModulePom("dir/m2", "<groupId>test</groupId>" +
                              "<artifactId>m2</artifactId>" +
                              "<version>1</version>");

    importProject();
    assertModules("project", "m1", "m2");

    List<MavenProject> roots = getProjectsTree().getRootProjects();
    assertEquals(1, roots.size());
    assertEquals("project", roots.get(0).getMavenId().getArtifactId());

    List<MavenProject> modules = getProjectsTree().getModules(roots.get(0));
    assertEquals(2, modules.size());
    assertEquals("m1", modules.get(0).getMavenId().getArtifactId());
    assertEquals("m2", modules.get(1).getMavenId().getArtifactId());
  }

  @Test
  public void testModulesAreNamedAfterArtifactIds() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +
                     "<name>name</name>" +

                     "<modules>" +
                     "  <module>dir1</module>" +
                     "  <module>dir2</module>" +
                     "</modules>");

    createModulePom("dir1", "<groupId>test</groupId>" +
                            "<artifactId>m1</artifactId>" +
                            "<version>1</version>" +
                            "<name>name1</name>");

    createModulePom("dir2", "<groupId>test</groupId>" +
                            "<artifactId>m2</artifactId>" +
                            "<version>1</version>" +
                            "<name>name2</name>");
    importProject();
    assertModules("project", "m1", "m2");
  }

  @Test
  public void testModulesWithSlashesAtTheEnds() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1/</module>" +
                     "  <module>m2\\</module>" +
                     "  <module>m3//</module>" +
                     "</modules>");

    createModulePom("m1", "<groupId>test</groupId>" +
                          "<artifactId>m1</artifactId>" +
                          "<version>1</version>");

    createModulePom("m2", "<groupId>test</groupId>" +
                          "<artifactId>m2</artifactId>" +
                          "<version>1</version>");

    createModulePom("m3", "<groupId>test</groupId>" +
                          "<artifactId>m3</artifactId>" +
                          "<version>1</version>");

    importProject();
    assertModules("project", "m1", "m2", "m3");
  }

  @Test
  public void testModulesWithSameArtifactId() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>dir1/m</module>" +
                     "  <module>dir2/m</module>" +
                     "</modules>");

    createModulePom("dir1/m", "<groupId>test.group1</groupId>" +
                              "<artifactId>m</artifactId>" +
                              "<version>1</version>");

    createModulePom("dir2/m", "<groupId>test.group2</groupId>" +
                              "<artifactId>m</artifactId>" +
                              "<version>1</version>");

    importProject();
    assertModules("project", "m (1) (test.group1)", "m (2) (test.group2)");
  }

  @Test
  public void testModulesWithSameArtifactIdAndGroup() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>dir1/m</module>" +
                     "  <module>dir2/m</module>" +
                     "</modules>");

    createModulePom("dir1/m", "<groupId>test</groupId>" +
                              "<artifactId>m</artifactId>" +
                              "<version>1</version>");

    createModulePom("dir2/m", "<groupId>test</groupId>" +
                              "<artifactId>m</artifactId>" +
                              "<version>1</version>");

    importProject();
    assertModules("project", "m (1)", "m (2)");
  }

  @Test
  public void testModuleWithRelativePath() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>../m</module>" +
                     "</modules>");

    createModulePom("../m", "<groupId>test</groupId>" +
                            "<artifactId>m</artifactId>" +
                            "<version>1</version>");

    importProject();
    assertModules("project", "m");
  }

  @Test
  public void testModuleWithRelativeParent() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<parent>" +
                     "  <groupId>test</groupId>" +
                     "  <artifactId>parent</artifactId>" +
                     "  <version>1</version>" +
                     "  <relativePath>../parent</relativePath>" +
                     "</parent>");

    createModulePom("../parent", "<groupId>test</groupId>" +
                                 "<artifactId>parent</artifactId>" +
                                 "<version>1</version>" +
                                 "<packaging>pom</packaging>");

    importProject();
    assertModules("project");
  }

  @Test
  public void testModulePathsAsProperties() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<properties>" +
                     "  <module1>m1</module1>" +
                     "  <module2>m2</module2>" +
                     "</properties>" +

                     "<modules>" +
                     "  <module>${module1}</module>" +
                     "  <module>${module2}</module>" +
                     "</modules>");

    createModulePom("m1", "<groupId>test</groupId>" +
                          "<artifactId>m1</artifactId>" +
                          "<version>1</version>");

    createModulePom("m2", "<groupId>test</groupId>" +
                          "<artifactId>m2</artifactId>" +
                          "<version>1</version>");

    importProject();
    assertModules("project", "m1", "m2");

    List<MavenProject> roots = getProjectsTree().getRootProjects();
    assertEquals(1, roots.size());
    assertEquals("project", roots.get(0).getMavenId().getArtifactId());

    List<MavenProject> modules = getProjectsTree().getModules(roots.get(0));
    assertEquals(2, modules.size());
    assertEquals("m1", modules.get(0).getMavenId().getArtifactId());
    assertEquals("m2", modules.get(1).getMavenId().getArtifactId());
  }

  @Test
  public void testRecursiveParent() {
    createProjectPom("<parent>" +
                     "  <groupId>org.apache.maven.archetype.test</groupId>" +
                     "  <artifactId>test-create-2</artifactId>" +
                     "  <version>1.0-SNAPSHOT</version>" +
                     "</parent>" +

                     "<artifactId>test-create-2</artifactId>" +
                     "<name>Maven archetype Test create-2-subModule</name>" +
                     "<packaging>pom</packaging>");
    importProjectWithErrors();
  }

  @Test
  public void testParentWithoutARelativePath() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<properties>" +
                     "  <moduleName>m1</moduleName>" +
                     "</properties>" +

                     "<modules>" +
                     "  <module>modules/m</module>" +
                     "</modules>");

    createModulePom("modules/m", "<groupId>test</groupId>" +
                                 "<artifactId>${moduleName}</artifactId>" +
                                 "<version>1</version>" +

                                 "<parent>" +
                                 "  <groupId>test</groupId>" +
                                 "  <artifactId>project</artifactId>" +
                                 "  <version>1</version>" +
                                 "</parent>");

    importProject();
    assertModules("project", mn("project", "m1"));

    List<MavenProject> roots = getProjectsTree().getRootProjects();
    assertEquals(1, roots.size());
    assertEquals("project", roots.get(0).getMavenId().getArtifactId());

    List<MavenProject> modules = getProjectsTree().getModules(roots.get(0));
    assertEquals(1, modules.size());
    assertEquals("m1", modules.get(0).getMavenId().getArtifactId());
  }

  @Test
  public void testModuleWithPropertiesWithParentWithoutARelativePath() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<properties>" +
                     "  <moduleName>m1</moduleName>" +
                     "</properties>" +

                     "<modules>" +
                     "  <module>modules/m</module>" +
                     "</modules>");

    createModulePom("modules/m", "<groupId>test</groupId>" +
                                 "<artifactId>${moduleName}</artifactId>" +
                                 "<version>1</version>" +

                                 "<parent>" +
                                 "  <groupId>test</groupId>" +
                                 "  <artifactId>project</artifactId>" +
                                 "  <version>1</version>" +
                                 "</parent>");

    importProject();
    assertModules("project", mn("project", "m1"));

    List<MavenProject> roots = getProjectsTree().getRootProjects();
    assertEquals(1, roots.size());
    assertEquals("project", roots.get(0).getMavenId().getArtifactId());

    List<MavenProject> modules = getProjectsTree().getModules(roots.get(0));
    assertEquals(1, modules.size());
    assertEquals("m1", modules.get(0).getMavenId().getArtifactId());
  }

  @Test
  public void testParentInLocalRepository() throws Exception {
    if (!hasMavenInstallation()) return;

    final VirtualFile parent = createModulePom("parent",
                                               "<groupId>test</groupId>" +
                                               "<artifactId>parent</artifactId>" +
                                               "<version>1</version>" +
                                               "<packaging>pom</packaging>" +

                                               "<dependencies>" +
                                               "  <dependency>" +
                                               "    <groupId>junit</groupId>" +
                                               "    <artifactId>junit</artifactId>" +
                                               "    <version>4.0</version>" +
                                               "  </dependency>" +
                                               "</dependencies>");
    executeGoal("parent", "install");

    WriteAction.runAndWait(() -> parent.delete(null));


    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>m</artifactId>" +
                     "<version>1</version>" +

                     "<parent>" +
                     "  <groupId>test</groupId>" +
                     "  <artifactId>parent</artifactId>" +
                     "  <version>1</version>" +
                     "</parent>");

    importProject();
    assertModules("m");
    assertModuleLibDeps("m", "Maven: junit:junit:4.0");
  }

  @Test
  public void testParentInRemoteRepository() {
    String pathToJUnit = "asm/asm-parent/3.0";
    File parentDir = new File(getRepositoryPath(), pathToJUnit);

    removeFromLocalRepository(pathToJUnit);
    assertFalse(parentDir.exists());

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<parent>" +
                     "  <groupId>asm</groupId>" +
                     "  <artifactId>asm-parent</artifactId>" +
                     "  <version>3.0</version>" +
                     "</parent>");

    importProject();
    assertModules("project");

    assertTrue(parentDir.exists());

    assertEquals("asm-parent", getProjectsTree().getRootProjects().get(0).getParentId().getArtifactId());
    assertTrue(new File(parentDir, "asm-parent-3.0.pom").exists());
  }

  @Test
  public void testCreatingModuleGroups() {
    VirtualFile p1 = createModulePom("project1",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>project1</artifactId>" +
                                     "<version>1</version>" +
                                     "<packaging>pom</packaging>" +

                                     "<modules>" +
                                     "  <module>m1</module>" +
                                     "</modules>");

    createModulePom("project1/m1",
                    "<groupId>test</groupId>" +
                    "<artifactId>m1</artifactId>" +
                    "<version>1</version>");

    VirtualFile p2 = createModulePom("project2",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>project2</artifactId>" +
                                     "<version>1</version>" +
                                     "<packaging>pom</packaging>" +

                                     "<modules>" +
                                     "  <module>m2</module>" +
                                     "</modules>");

    createModulePom("project2/m2",
                    "<groupId>test</groupId>" +
                    "<artifactId>m2</artifactId>" +
                    "<version>1</version>" +
                    "<packaging>pom</packaging>" +

                    "<modules>" +
                    "  <module>m3</module>" +
                    "</modules>");

    createModulePom("project2/m2/m3",
                    "<groupId>test</groupId>" +
                    "<artifactId>m3</artifactId>" +
                    "<version>1</version>");

    getMavenImporterSettings().setCreateModuleGroups(true);
    importProjects(p1, p2);
    assertModules("project1", "project2", "m1", "m2", "m3");

    assertModuleGroupPath("project1", "project1 and modules");
    assertModuleGroupPath("m1", "project1 and modules");
    assertModuleGroupPath("project2", "project2 and modules");
    assertModuleGroupPath("m2", "project2 and modules", "m2 and modules");
    assertModuleGroupPath("m3", "project2 and modules", "m2 and modules");
  }

  @Test
  public void testDoesNotCreateUnnecessaryTopLevelModuleGroup() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "</modules>");

    createModulePom("m1",
                    "<groupId>test</groupId>" +
                    "<artifactId>m1</artifactId>" +
                    "<version>1</version>" +
                    "<packaging>pom</packaging>" +

                    "<modules>" +
                    "  <module>m2</module>" +
                    "</modules>");

    createModulePom("m1/m2",
                    "<groupId>test</groupId>" +
                    "<artifactId>m2</artifactId>" +
                    "<version>1</version>");

    getMavenImporterSettings().setCreateModuleGroups(true);
    importProject();
    assertModules("project", "m1", "m2");

    assertModuleGroupPath("project");
    assertModuleGroupPath("m1", "m1 and modules");
    assertModuleGroupPath("m2", "m1 and modules");
  }

  @Test
  public void testModuleGroupsWhenNotCreatingModulesForAggregatorProjects() {
    if (!supportsCreateAggregatorOption() || !supportModuleGroups()) return;

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>module1</module>" +
                     "</modules>");

    createModulePom("module1",
                    "<groupId>test</groupId>" +
                    "<artifactId>module1</artifactId>" +
                    "<version>1</version>" +
                    "<packaging>pom</packaging>" +

                    "<modules>" +
                    "  <module>module2</module>" +
                    "</modules>");

    createModulePom("module1/module2",
                    "<groupId>test</groupId>" +
                    "<artifactId>module2</artifactId>" +
                    "<version>1</version>");

    getMavenImporterSettings().setCreateModuleGroups(true);
    getMavenImporterSettings().setCreateModulesForAggregators(false);
    importProject();
    assertModules("module2");

    assertModuleGroupPath("module2", "module1 and modules");
  }

  @Test
  public void testReimportingProjectWhenCreatingModuleGroupsSettingChanged() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>module1</module>" +
                     "</modules>");

    createModulePom("module1",
                    "<groupId>test</groupId>" +
                    "<artifactId>module1</artifactId>" +
                    "<version>1</version>" +
                    "<packaging>pom</packaging>" +

                    "<modules>" +
                    "  <module>module2</module>" +
                    "</modules>");

    createModulePom("module1/module2",
                    "<groupId>test</groupId>" +
                    "<artifactId>module2</artifactId>" +
                    "<version>1</version>");
    importProject();
    assertModules("project", "module1", "module2");

    assertModuleGroupPath("module2");

    getMavenImporterSettings().setCreateModuleGroups(true);
    if (isNewImportingProcess) {
      importViaNewFlow(Collections.singletonList(myProjectPom), true, Collections.emptyList());
    }
    else {
      myProjectsManager.performScheduledImportInTests();
    }

    assertModuleGroupPath("module2", "module1 and modules");
  }

  @Test
  public void testModuleGroupsWhenProjectWithDuplicateNameEmerges() {
    VirtualFile p1 = createModulePom("project1",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>project1</artifactId>" +
                                     "<version>1</version>" +
                                     "<packaging>pom</packaging>" +

                                     "<modules>" +
                                     "  <module>m1</module>" +
                                     "</modules>");

    createModulePom("project1/m1",
                    "<groupId>test</groupId>" +
                    "<artifactId>module</artifactId>" +
                    "<version>1</version>");

    VirtualFile p2 = createModulePom("project2",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>project2</artifactId>" +
                                     "<version>1</version>" +
                                     "<packaging>pom</packaging>");

    //createModulePom("m2",
    //                "<groupId>test</groupId>" +
    //                "<artifactId>m2</artifactId>" +
    //                "<version>1</version>" +
    //                "<packaging>pom</packaging>");

    getMavenImporterSettings().setCreateModuleGroups(true);
    importProjects(p1, p2);
    assertModules("project1", "project2", "module");

    if (supportModuleGroups()) {
      assertModuleGroupPath("project1", "project1 and modules");
      assertModuleGroupPath("module", "project1 and modules");
    }

    p2 = createModulePom("project2",
                         "<groupId>test</groupId>" +
                         "<artifactId>project2</artifactId>" +
                         "<version>1</version>" +
                         "<packaging>pom</packaging>" +

                         "<modules>" +
                         "  <module>m2</module>" +
                         "</modules>");

    createModulePom("project2/m2",
                    "<groupId>test</groupId>" +
                    "<artifactId>module</artifactId>" +
                    "<version>1</version>");

    updateProjectsAndImport(p2); // should not fail to map module names. 

    if (supportsKeepingModulesFromPreviousImport()) {
      assertModules("project1", "project2", "module", "module (1)");
    }
    else {
      assertModules("project1", "project2", "module (1)", "module (2)");
    }

    if (supportModuleGroups()) {
      assertModuleGroupPath("project1", "project1 and modules");
      assertModuleGroupPath("module", "project1 and modules");
      assertModuleGroupPath("project2", "project2 and modules");
      assertModuleGroupPath("module (1)", "project2 and modules");
    }
  }

  @Test
  public void testReleaseCompilerPropertyInPerSourceTypeModules() {
    Assume.assumeTrue(isWorkspaceImport());

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<properties>" +
                  "  <maven.compiler.release>8</maven.compiler.release>" +
                  "  <maven.compiler.testRelease>11</maven.compiler.testRelease>" +
                  "</properties>" +
                  "" +
                  " <build>\n" +
                  "  <plugins>" +
                  "    <plugin>" +
                  "      <artifactId>maven-compiler-plugin</artifactId>" +
                  "      <version>3.10.0</version>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>"
    );

    assertModules("project", "project.main", "project.test");
  }

  @Test
  public void testProjectWithBuiltExtension() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  " <extensions>" +
                  "   <extension>" +
                  "     <groupId>org.apache.maven.wagon</groupId>" +
                  "     <artifactId>wagon-webdav</artifactId>" +
                  "     <version>1.0-beta-2</version>" +
                  "    </extension>" +
                  "  </extensions>" +
                  "</build>");
    assertModules("project");
  }

  @Test
  public void testUsingPropertyInBuildExtensionsOfChildModule() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<properties>" +
                     "  <xxx>1.0-beta-2</xxx>" +
                     "</properties>" +

                     "<modules>" +
                     "  <module>m</module>" +
                     "</modules>");

    createModulePom("m", "<groupId>test</groupId>" +
                         "<artifactId>m</artifactId>" +

                         "<parent>" +
                         "  <groupId>test</groupId>" +
                         "  <artifactId>project</artifactId>" +
                         "  <version>1</version>" +
                         "</parent>" +

                         "<build>" +
                         "  <extensions>" +
                         "    <extension>" +
                         "      <groupId>org.apache.maven.wagon</groupId>" +
                         "      <artifactId>wagon-webdav</artifactId>" +
                         "      <version>${xxx}</version>" +
                         "    </extension>" +
                         "  </extensions>" +
                         "</build>");

    importProject();
    assertModules("project", mn("project", "m"));
  }

  @Test
  public void testFileProfileActivationInParentPom() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "  <profiles>" +
                     "    <profile>" +
                     "      <id>xxx</id>" +
                     "      <dependencies>" +
                     "        <dependency>" +
                     "          <groupId>junit</groupId>" +
                     "          <artifactId>junit</artifactId>" +
                     "          <version>4.0</version>" +
                     "        </dependency>" +
                     "      </dependencies>" +
                     "      <activation>" +
                     "        <file>" +
                     "          <exists>src/io.properties</exists>" +
                     "        </file>" +
                     "      </activation>" +
                     "    </profile>" +
                     "  </profiles>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "</modules>");

    createModulePom("m1", "<groupId>test</groupId>" +
                          "<artifactId>m1</artifactId>" +

                          "<parent>" +
                          "  <groupId>test</groupId>" +
                          "  <artifactId>project</artifactId>" +
                          "  <version>1</version>" +
                          "</parent>");

    createModulePom("m2", "<groupId>test</groupId>" +
                          "<artifactId>m2</artifactId>" +

                          "<parent>" +
                          "  <groupId>test</groupId>" +
                          "  <artifactId>project</artifactId>" +
                          "  <version>1</version>" +
                          "</parent>");
    createProjectSubFile("m2/src/io.properties", "");

    importProject();

    assertModules("project", mn("project", "m1"), mn("project", "m2"));
    assertModuleLibDeps(mn("project", "m1"));
    assertModuleLibDeps(mn("project", "m2"), "Maven: junit:junit:4.0");
  }

  @Test
  public void testProjectWithProfiles() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<profiles>" +
                     "  <profile>" +
                     "    <id>one</id>" +
                     "    <activation>" +
                     "      <activeByDefault>false</activeByDefault>" +
                     "    </activation>" +
                     "    <properties>" +
                     "      <junit.version>4.0</junit.version>" +
                     "    </properties>" +
                     "  </profile>" +
                     "  <profile>" +
                     "    <id>two</id>" +
                     "    <activation>" +
                     "      <activeByDefault>false</activeByDefault>" +
                     "    </activation>" +
                     "    <properties>" +
                     "      <junit.version>3.8.1</junit.version>" +
                     "    </properties>" +
                     "  </profile>" +
                     "</profiles>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>junit</groupId>" +
                     "    <artifactId>junit</artifactId>" +
                     "    <version>${junit.version}</version>" +
                     "  </dependency>" +
                     "</dependencies>");

    importProjectWithProfiles("one");
    assertModules("project");

    assertModuleLibDeps("project", "Maven: junit:junit:4.0");

    importProjectWithProfiles("two");
    assertModules("project");

    assertModuleLibDeps("project", "Maven: junit:junit:3.8.1");
  }

  @Test
  public void testProjectWithOldProfilesXmlFile() {
    ignore(); // not supported by 2.2
  }

  @Test
  public void testProjectWithDefaultProfile() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<profiles>" +
                     "  <profile>" +
                     "    <id>one</id>" +
                     "    <activation>" +
                     "      <activeByDefault>true</activeByDefault>" +
                     "    </activation>" +
                     "    <properties>" +
                     "      <junit.version>4.0</junit.version>" +
                     "    </properties>" +
                     "  </profile>" +
                     "</profiles>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>junit</groupId>" +
                     "    <artifactId>junit</artifactId>" +
                     "    <version>${junit.version}</version>" +
                     "  </dependency>" +
                     "</dependencies>");

    importProject();
    assertModules("project");

    assertModuleLibDeps("project", "Maven: junit:junit:4.0");
  }

  @Test
  public void testRefreshFSAfterImport() {
    myProjectRoot.getChildren(); // make sure fs is cached
    new File(myProjectRoot.getPath(), "foo").mkdirs();

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");
    if (isNewImportingProcess) {
      PlatformTestUtil.waitForPromise(myImportingResult.getVfsRefreshPromise());
    }

    assertNotNull(myProjectRoot.findChild("foo"));
  }

  @Test
  public void testErrorImportArtifactVersionCannotBeEmpty() {
    assumeVersionMoreThan("3.0.5");
    createProjectPom("""
                       <groupId>test</groupId>
                         <artifactId>parent</artifactId>
                         <packaging>pom</packaging>
                         <version>1</version>
                         <modules>
                          <module>m1</module>
                         </modules>
                         <properties>
                          <junit.group.id>junit</junit.group.id>
                          <junit.artifact.id>junit</junit.artifact.id>
                         </properties>
                         <profiles>
                           <profile>
                             <id>profile-test</id>
                             <dependencies>
                               <dependency>
                                 <groupId>${junit.group.id}</groupId>
                                 <artifactId>${junit.artifact.id}</artifactId>
                               </dependency>
                             </dependencies>
                           </profile>
                         </profiles>
                        \s
                         <dependencyManagement>
                           <dependencies>
                             <dependency>
                               <groupId>junit</groupId>
                               <artifactId>junit</artifactId>
                               <version>4.0</version>\s
                             </dependency>
                           </dependencies>
                         </dependencyManagement>""");

    createModulePom("m1", """
      <parent>
      <groupId>test</groupId>
      <artifactId>parent</artifactId>
      <version>1</version>\t
      </parent>
      <artifactId>m1</artifactId>\t
      <dependencies>
        <dependency>
          <groupId>junit</groupId>
          <artifactId>junit</artifactId>
        </dependency>
      </dependencies>""");

    doImportProjects(Collections.singletonList(myProjectPom), false, "profile-test");
  }

  @Test
  public void testProjectWithMavenConfigCustomUserSettingsXml() throws IOException {
    createProjectSubFile(".mvn/maven.config", "-s .mvn/custom-settings.xml");
    createProjectSubFile(".mvn/custom-settings.xml",
                         """
                           <settings>
                               <profiles>
                                   <profile>
                                       <id>custom1</id>
                                       <properties>
                                           <projectName>customName</prop>
                                       </properties>
                                   </profile>
                               </profiles>
                               <activeProfiles>
                                   <activeProfile>custom1</activeProfile>
                               </activeProfiles></settings>""");
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>${projectName}</artifactId>" +
                     "<version>1</version>");

    MavenGeneralSettings settings = getMavenGeneralSettings();
    settings.setUserSettingsFile("");
    settings.setUseMavenConfig(true);
    importProject();
    assertModules("customName");
  }

  @Test
  public void testProjectWithActiveProfilesFromSettingsXml() throws IOException {
    updateSettingsXml("""
                        <activeProfiles>
                          <activeProfile>one</activeProfile>
                        </activeProfiles>""");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>${projectName}</artifactId>" +
                     "<version>1</version>" +

                     "<profiles>" +
                     "  <profile>" +
                     "    <id>one</id>" +
                     "    <properties>" +
                     "      <projectName>project-one</projectName>" +
                     "    </properties>" +
                     "  </profile>" +
                     "</profiles>");

    importProject();
    assertModules("project-one");
  }

  @Test
  public void testProjectWithActiveProfilesAndInnactiveFromSettingsXml() throws IOException {
    updateSettingsXml("""
                        <activeProfiles>
                          <activeProfile>one</activeProfile>
                          <activeProfile>two</activeProfile>
                        </activeProfiles>""");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>${projectName}</artifactId>" +
                     "<version>1</version>" +

                     "<profiles>" +
                     "  <profile>" +
                     "    <id>one</id>" +
                     "    <properties>" +
                     "      <projectName>project-one</projectName>" +
                     "    </properties>" +
                     "  </profile>" +
                     "  <profile>" +
                     "    <id>two</id>" +
                     "    <properties>" +
                     "      <projectName>project-two</projectName>" +
                     "    </properties>" +
                     "  </profile>" +
                     "</profiles>");

    List<String> disabledProfiles = Collections.singletonList("one");
    if (isNewImportingProcess) {
      importViaNewFlow(Collections.singletonList(myProjectPom), true, Collections.emptyList());
    }
    else {
      doImportProjectsLegacyWay(Collections.singletonList(myProjectPom), true, disabledProfiles);
    }
    assertModules("project-two");
  }
}
