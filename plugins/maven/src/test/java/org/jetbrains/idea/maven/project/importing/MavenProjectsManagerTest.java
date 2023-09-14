// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project.importing;

import com.intellij.ide.DataManager;
import com.intellij.ide.actions.DeleteAction;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase;
import com.intellij.maven.testFramework.utils.MavenImportingTestCaseKt;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleTypeId;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.ui.configuration.actions.ModuleDeleteProvider;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.util.FileContentUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.importing.MavenProjectLegacyImporter;
import org.jetbrains.idea.maven.importing.MavenRootModelAdapter;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectChanges;
import org.jetbrains.idea.maven.project.MavenProjectsTree;
import org.jetbrains.idea.maven.project.actions.MavenModuleDeleteProvider;
import org.jetbrains.idea.maven.project.actions.RemoveManagedFilesAction;
import org.jetbrains.idea.maven.project.projectRoot.MavenModuleStructureExtension;
import org.jetbrains.idea.maven.server.NativeMavenProjectHolder;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.junit.Assume;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assume.assumeTrue;

public class MavenProjectsManagerTest extends MavenMultiVersionImportingTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initProjectsManager(false);
    Assume.assumeFalse(MavenUtil.isLinearImportEnabled());
  }

  @Test
  public void testShouldReturnNullForUnprocessedFiles() {
    // this pom file doesn't belong to any of the modules, this is won't be processed
    // by MavenProjectProjectsManager and won't occur in its projects list.
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """);

    // shouldn't throw
    assertNull(getProjectsManager().findProject(myProjectPom));
  }

  @Test
  public void testShouldReturnNotNullForProcessedFiles() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """);
    importProject();

    // shouldn't throw
    assertNotNull(getProjectsManager().findProject(myProjectPom));
  }

  @Test
  public void testAddingAndRemovingManagedFiles() {
    //configConfirmationForYesAnswer();
    MavenProjectLegacyImporter.setAnswerToDeleteObsoleteModulesQuestion(true);


    VirtualFile m1 = createModulePom("m1",
                                     """
                                       <groupId>test</groupId>
                                       <artifactId>m1</artifactId>
                                       <version>1</version>
                                       """);

    VirtualFile m2 = createModulePom("m2",
                                     """
                                       <groupId>test</groupId>
                                       <artifactId>m2</artifactId>
                                       <version>1</version>
                                       """);
    importProject(m1);

    assertUnorderedElementsAreEqual(getProjectsTree().getRootProjectsFiles(), m1);

    getProjectsManager().addManagedFiles(Arrays.asList(m2));
    waitForReadingCompletion();

    assertUnorderedElementsAreEqual(getProjectsTree().getRootProjectsFiles(), m1, m2);

    getProjectsManager().removeManagedFiles(Arrays.asList(m2));
    waitForReadingCompletion();
    assertUnorderedElementsAreEqual(getProjectsTree().getRootProjectsFiles(), m1);
  }

  @Test
  public void testAddingAndRemovingManagedFilesAddsAndRemovesModules() {
    VirtualFile m1 = createModulePom("m1",
                                     """
                                       <groupId>test</groupId>
                                       <artifactId>m1</artifactId>
                                       <version>1</version>
                                       """);

    final VirtualFile m2 = createModulePom("m2",
                                           """
                                             <groupId>test</groupId>
                                             <artifactId>m2</artifactId>
                                             <version>1</version>
                                             """);
    importProject(m1);
    assertModules("m1");

    resolveDependenciesAndImport(); // ensure no pending imports

    getProjectsManager().addManagedFiles(Collections.singletonList(m2));
    waitForReadingCompletion();
    resolveDependenciesAndImport();

    assertModules("m1", "m2");

    //configConfirmationForYesAnswer();
    MavenProjectLegacyImporter.setAnswerToDeleteObsoleteModulesQuestion(true);

    getProjectsManager().removeManagedFiles(Collections.singletonList(m2));
    waitForReadingCompletion();
    resolveDependenciesAndImport();

    assertModules("m1");
  }

  @Test
  public void testDoNotScheduleResolveOfInvalidProjectsDeleted() {
    final boolean[] called = new boolean[1];
    getProjectsManager().addProjectsTreeListener(new MavenProjectsTree.Listener() {
      @Override
      public void projectResolved(@NotNull Pair<MavenProject, MavenProjectChanges> projectWithChanges,
                                  NativeMavenProjectHolder nativeMavenProject) {
        called[0] = true;
      }
    });

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1""");
    importProjectWithErrors();
    assertModules("project");
    assertFalse(called[0]); // on import

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>2""");

    waitForReadingCompletion();
    resolveDependenciesAndImport();

    assertFalse(called[0]); // on update
  }

  @Test
  public void testUpdatingFoldersAfterFoldersResolving() {
    createStdProjectFolders();
    createProjectSubDirs("src1", "src2", "test1", "test2", "res1", "res2", "testres1", "testres2");

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.codehaus.mojo</groupId>
                          <artifactId>build-helper-maven-plugin</artifactId>
                          <version>1.3</version>
                          <executions>
                            <execution>
                              <id>someId1</id>
                              <phase>generate-sources</phase>
                              <goals>
                                <goal>add-source</goal>
                              </goals>
                              <configuration>
                                <sources>
                                  <source>${basedir}/src1</source>
                                  <source>${basedir}/src2</source>
                                </sources>
                              </configuration>
                            </execution>
                            <execution>
                              <id>someId2</id>
                              <phase>generate-resources</phase>
                              <goals>
                                <goal>add-resource</goal>
                              </goals>
                              <configuration>
                                <resources>
                                  <resource>
                                     <directory>${basedir}/res1</directory>
                                  </resource>
                                  <resource>
                                     <directory>${basedir}/res2</directory>
                                  </resource>
                                </resources>
                              </configuration>
                            </execution>
                            <execution>
                              <id>someId3</id>
                              <phase>generate-test-sources</phase>
                              <goals>
                                <goal>add-test-source</goal>
                              </goals>
                              <configuration>
                                <sources>
                                  <source>${basedir}/test1</source>
                                  <source>${basedir}/test2</source>
                                </sources>
                              </configuration>
                            </execution>
                            <execution>
                              <id>someId4</id>
                              <phase>generate-test-resources</phase>
                              <goals>
                                <goal>add-test-resource</goal>
                              </goals>
                              <configuration>
                                <resources>
                                  <resource>
                                     <directory>${basedir}/testres1</directory>
                                  </resource>
                                  <resource>
                                     <directory>${basedir}/testres2</directory>
                                  </resource>
                                </resources>
                              </configuration>
                            </execution>
                          </executions>
                        </plugin>
                      </plugins>
                    </build>
                    """);

    assertSources("project", "src/main/java", "src1", "src2");
    assertDefaultResources("project", "res1", "res2");

    assertTestSources("project", "src/test/java", "test1", "test2");
    assertDefaultTestResources("project", "testres1", "testres2");
  }

  @Test
  public void testForceReimport() {
    createProjectSubDir("src/main/java");

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId>junit</groupId>
                           <artifactId>junit</artifactId>
                           <version>4.0</version>
                         </dependency>
                       </dependencies>
                       """);
    importProject();
    assertModules("project");
    assertSources("project", "src/main/java");
    assertModuleLibDeps("project", "Maven: junit:junit:4.0");

    ApplicationManager.getApplication().runWriteAction(() -> {
      ModifiableRootModel model = ModuleRootManager.getInstance(getModule("project")).getModifiableModel();

      ContentEntry contentRoot = model.getContentEntries()[0];
      for (SourceFolder eachSourceFolders : contentRoot.getSourceFolders()) {
        contentRoot.removeSourceFolder(eachSourceFolders);
      }

      for (OrderEntry each : model.getOrderEntries()) {
        if (each instanceof LibraryOrderEntry && MavenRootModelAdapter.isMavenLibrary(((LibraryOrderEntry)each).getLibrary())) {
          model.removeOrderEntry(each);
        }
      }
      model.commit();
    });


    assertSources("project");
    assertModuleLibDeps("project");

    getProjectsManager().forceUpdateAllProjectsOrFindAllAvailablePomFiles();
    waitForReadingCompletion();
    getProjectsManager().waitForReadingCompletion();
    MavenImportingTestCaseKt.importMavenProjects(getProjectsManager());
    //myProjectsManager.performScheduledImportInTests();

    assertSources("project", "src/main/java");
    assertModuleLibDeps("project", "Maven: junit:junit:4.0");
  }

  @Test
  public void testNotIgnoringProjectsForDeletedInBackgroundModules() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m</module>
                       </modules>
                       """);

    VirtualFile m = createModulePom("m",
                                    """
                                      <groupId>test</groupId>
                                      <artifactId>m</artifactId>
                                      <version>1</version>
                                      """);
    importProject();

    Module module = getModule("m");
    assertNotNull(module);
    assertFalse(getProjectsManager().isIgnored(getProjectsManager().findProject(m)));

    ModuleManager.getInstance(myProject).disposeModule(module);
    //myProjectsManager.performScheduledImportInTests();

    assertNull(ModuleManager.getInstance(myProject).findModuleByName("m"));
    assertFalse(getProjectsManager().isIgnored(getProjectsManager().findProject(m)));
  }

  @Test
  public void testIgnoringProjectsForRemovedInUiModules() throws ConfigurationException {
    MavenProjectLegacyImporter.setAnswerToDeleteObsoleteModulesQuestion(true);

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m</module>
                       </modules>
                       """);

    VirtualFile m = createModulePom("m",
                                    """
                                      <groupId>test</groupId>
                                      <artifactId>m</artifactId>
                                      <version>1</version>
                                      """);
    importProject();

    Module module = getModule("m");
    assertNotNull(module);
    assertFalse(getProjectsManager().isIgnored(getProjectsManager().findProject(m)));

    var moduleManager = ModuleManager.getInstance(myProject);
    ModifiableModuleModel moduleModel = moduleManager.getModifiableModel();
    ModuleDeleteProvider.removeModule(module, List.of(), moduleModel);
    var moduleStructureExtension = new MavenModuleStructureExtension();
    moduleStructureExtension.moduleRemoved(module);
    moduleStructureExtension.apply();
    moduleStructureExtension.disposeUIResources();
    updateAllProjects();

    assertNull(ModuleManager.getInstance(myProject).findModuleByName("m"));
    assertTrue(getProjectsManager().isIgnored(getProjectsManager().findProject(m)));
  }

  @Test
  public void testIgnoringProjectsForDetachedInUiModules() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m</module>
                       </modules>
                       """);

    VirtualFile m = createModulePom("m",
                                    """
                                      <groupId>test</groupId>
                                      <artifactId>m</artifactId>
                                      <version>1</version>
                                      """);
    importProject();

    Module module = getModule("m");
    assertNotNull(module);
    assertFalse(getProjectsManager().isIgnored(getProjectsManager().findProject(m)));

    CommandProcessor.getInstance().executeCommand(myProject, () -> {
      final Runnable action = () -> {
        ModuleDeleteProvider.detachModules(myProject, new Module[]{module});
      };
      ApplicationManager.getApplication().runWriteAction(action);
    }, ProjectBundle.message("module.remove.command"), null);
    //myProjectsManager.performScheduledImportInTests();
    MavenImportingTestCaseKt.importMavenProjects(getProjectsManager());

    assertNull(ModuleManager.getInstance(myProject).findModuleByName("m"));
    assertTrue(getProjectsManager().isIgnored(getProjectsManager().findProject(m)));
  }

  private static DataContext createTestModuleDataContext(Module... modules) {
    final DataContext defaultContext = DataManager.getInstance().getDataContext();
    return dataId -> {
      if (LangDataKeys.MODULE_CONTEXT_ARRAY.is(dataId)) {
        return modules;
      }
      if (ProjectView.UNLOADED_MODULES_CONTEXT_KEY.is(dataId)) {
        return List.of(); // UnloadedModuleDescription
      }
      if (PlatformDataKeys.DELETE_ELEMENT_PROVIDER.is(dataId)) {
        return new MavenModuleDeleteProvider();
      }
      return defaultContext.getData(dataId);
    };
  }

  @Test
  public void testWhenDeleteModuleThenChangeModuleDependencyToLibraryDependency() {
    assumeTrue(isWorkspaceImport());
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

    VirtualFile m1 = createModulePom("m1",
                                     """
                                       <groupId>test</groupId>
                                       <artifactId>m1</artifactId>
                                       <version>1</version>
                                       """);

    VirtualFile m2 = createModulePom("m2",
                                     """
                                       <groupId>test</groupId>
                                       <artifactId>m2</artifactId>
                                       <version>1</version>
                                       <dependencies>
                                           <dependency>
                                               <groupId>test</groupId>
                                               <artifactId>m1</artifactId>
                                               <version>1</version>
                                           </dependency>
                                       </dependencies>
                                       """);
    importProject();

    assertModuleModuleDeps("m2", "m1");

    Module module1 = getModule("m1");
    configConfirmationForYesAnswer();
    var action = new DeleteAction();
    action.actionPerformed(TestActionEvent.createTestEvent(action, createTestModuleDataContext(module1)));

    updateAllProjects();

    assertModuleModuleDeps("m2");
    assertModuleLibDep("m2", "Maven: test:m1:1");
  }

  @Test
  public void testWhenDeleteModuleInProjectStructureThenChangeModuleDependencyToLibraryDependency() throws ConfigurationException {
    assumeTrue(isWorkspaceImport());
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

    VirtualFile m1 = createModulePom("m1",
                                     """
                                       <groupId>test</groupId>
                                       <artifactId>m1</artifactId>
                                       <version>1</version>
                                       """);

    VirtualFile m2 = createModulePom("m2",
                                     """
                                       <groupId>test</groupId>
                                       <artifactId>m2</artifactId>
                                       <version>1</version>
                                       <dependencies>
                                           <dependency>
                                               <groupId>test</groupId>
                                               <artifactId>m1</artifactId>
                                               <version>1</version>
                                           </dependency>
                                       </dependencies>
                                       """);
    importProject();

    assertModuleModuleDeps("m2", "m1");

    Module module1 = getModule("m1");
    Module module2 = getModule("m2");
    var moduleManager = ModuleManager.getInstance(myProject);
    ModifiableModuleModel moduleModel = moduleManager.getModifiableModel();
    List<ModifiableRootModel> otherModuleRootModels = List.of(ModuleRootManager.getInstance(module2).getModifiableModel());
    ModuleDeleteProvider.removeModule(module1, otherModuleRootModels, moduleModel);
    var moduleStructureExtension = new MavenModuleStructureExtension();
    moduleStructureExtension.moduleRemoved(module1);
    moduleStructureExtension.apply();
    moduleStructureExtension.disposeUIResources();

    updateAllProjects();

    assertModuleModuleDeps("m2");
    assertModuleLibDep("m2", "Maven: test:m1:1");
  }

  @Test
  public void testDoNotIgnoreProjectWhenModuleDeletedDuringImport() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m</module>
                       </modules>
                       """);

    VirtualFile m = createModulePom("m",
                                    """
                                      <groupId>test</groupId>
                                      <artifactId>m</artifactId>
                                      <version>1</version>
                                      """);
    importProject();

    assertModules("project", "m");
    assertSize(1, getProjectsManager().getRootProjects());
    assertEmpty(getProjectsManager().getIgnoredFilesPaths());

    //configConfirmationForYesAnswer();
    MavenProjectLegacyImporter.setAnswerToDeleteObsoleteModulesQuestion(true);

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    """);

    assertModules("project");
    assertSize(1, getProjectsManager().getRootProjects());
    assertEmpty(getProjectsManager().getIgnoredFilesPaths());
  }

  @Test
  public void testDoNotIgnoreProjectWhenSeparateMainAndTestModulesDeletedDuringImport() {
    assumeTrue(isWorkspaceImport());

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
                          <version>3.10.0</version>
                        </plugin>
                      </plugins>
                    </build>"""
    );

    assertModules("project", "project.main", "project.test");
    assertSize(1, getProjectsManager().getRootProjects());
    assertEmpty(getProjectsManager().getIgnoredFilesPaths());

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """);

    assertModules("project");
    assertSize(1, getProjectsManager().getRootProjects());
    assertEmpty(getProjectsManager().getIgnoredFilesPaths());
  }

  @Test
  public void testDoNotRemoveMavenProjectsOnReparse() {
    // this pom file doesn't belong to any of the modules, this is won't be processed
    // by MavenProjectProjectsManager and won't occur in its projects list.
    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """);

    final StringBuilder log = new StringBuilder();
    //myProjectsManager.performScheduledImportInTests();
    getProjectsManager().addProjectsTreeListener(new MavenProjectsTree.Listener() {
      @Override
      public void projectsUpdated(@NotNull List<Pair<MavenProject, MavenProjectChanges>> updated, @NotNull List<MavenProject> deleted) {
        for (Pair<MavenProject, MavenProjectChanges> each : updated) {
          log.append("updated: ").append(each.first.getDisplayName()).append(" ");
        }
        for (MavenProject each : deleted) {
          log.append("deleted: ").append(each.getDisplayName()).append(" ");
        }
      }
    });

    FileContentUtil.reparseFiles(myProject, getProjectsManager().getProjectsFiles(), true);
    getProjectsManager().waitForReadingCompletion();

    assertTrue(log.toString(), log.length() == 0);
  }

  @Test
  public void testShouldRemoveMavenProjectsAndNotAddThemToIgnore() throws Exception {
    VirtualFile mavenParentPom = createProjectSubFile("maven-parent/pom.xml", """
      <?xml version="1.0" encoding="UTF-8"?>
      <project xmlns="http://maven.apache.org/POM/4.0.0"
               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
               xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
          <modelVersion>4.0.0</modelVersion>
          <groupId>test</groupId>
          <artifactId>parent-maven</artifactId>
          <packaging>pom</packaging>
          <version>1.0-SNAPSHOT</version>
          <modules>
              <module>child1</module>
          </modules></project>""");

    createProjectSubFile("maven-parent/child1/pom.xml", """
      <?xml version="1.0" encoding="UTF-8"?>
      <project xmlns="http://maven.apache.org/POM/4.0.0"
               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
               xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
          <parent>
              <artifactId>parent-maven</artifactId>
              <groupId>test</groupId>
              <version>1.0-SNAPSHOT</version>
          </parent>
          <modelVersion>4.0.0</modelVersion>    <artifactId>child1</artifactId></project>""");

    ApplicationManager.getApplication().runWriteAction(() -> {
      ModuleManager.getInstance(myProject).newModule("non-maven", ModuleTypeId.JAVA_MODULE);
    });
    importProject(mavenParentPom);
    assertEquals(3, ModuleManager.getInstance(myProject).getModules().length);

    configConfirmationForYesAnswer();

    RemoveManagedFilesAction action = new RemoveManagedFilesAction();
    action.actionPerformed(TestActionEvent.createTestEvent(action, createTestDataContext(mavenParentPom)));
    assertEquals(1, ModuleManager.getInstance(myProject).getModules().length);
    assertEquals("non-maven", ModuleManager.getInstance(myProject).getModules()[0].getName());
    assertEmpty(getProjectsManager().getIgnoredFilesPaths());

    //should then import project in non-ignored state again
    importProject(mavenParentPom);
    assertEquals(3, ModuleManager.getInstance(myProject).getModules().length);
    assertEmpty(getProjectsManager().getIgnoredFilesPaths());
  }


  @Test
  public void testSameArtifactIdDifferentTypeDependency() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                         <module>m3</module>
                       </modules>
                       """);

    VirtualFile m1 = createModulePom("m1",
                                     """
                                       <groupId>test</groupId>
                                       <artifactId>m1</artifactId>
                                       <version>1</version>
                                       """);

    VirtualFile m2 = createModulePom("m2",
                                     """
                                       <groupId>test</groupId>
                                       <artifactId>m2</artifactId>
                                       <version>1</version>
                                       <dependencies>
                                           <dependency>
                                               <groupId>test</groupId>
                                               <artifactId>m1</artifactId>
                                               <version>1</version>
                                           </dependency>
                                       </dependencies>
                                       """);

    VirtualFile m3 = createModulePom("m3",
                                     """
                                       <groupId>test</groupId>
                                       <artifactId>m3</artifactId>
                                       <version>1</version>
                                       <dependencies>
                                           <dependency>
                                               <groupId>test</groupId>
                                               <artifactId>m1</artifactId>
                                               <version>1</version>
                                               <type>ejb</type>
                                           </dependency>
                                       </dependencies>
                                       """);
    importProject();

    assertModuleModuleDeps("m2", "m1");
    assertModuleModuleDeps("m3", "m1");

    var mavenProject2 = getProjectsManager().findProject(m2);
    var m21dep = mavenProject2.findDependencies(new MavenId("test:m1:1")).get(0);
    assertEquals("jar", m21dep.getType());

    var mavenProject3 = getProjectsManager().findProject(m3);
    var m31dep = mavenProject3.findDependencies(new MavenId("test:m1:1")).get(0);
    assertEquals("ejb", m31dep.getType());
  }

  @Test
  public void shouldUnsetMavenizedIfManagedFilesWasRemoved(){
    //configConfirmationForYesAnswer();
    MavenProjectLegacyImporter.setAnswerToDeleteObsoleteModulesQuestion(true);


    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """);

    assertModules("project");
    assertSize(1, getProjectsManager().getRootProjects());

    getProjectsManager().removeManagedFiles(Collections.singletonList(myProjectPom));
    waitForImportCompletion();
    assertSize(0, getProjectsManager().getRootProjects());
  }

  @Test
  public void testShouldKeepModuleName() {
    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """);

    assertEquals("project", ModuleManager.getInstance(myProject).getModules()[0].getName());

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project1</artifactId>
                    <version>1</version>
                    """);

    assertEquals("project", ModuleManager.getInstance(myProject).getModules()[0].getName());
  }

  @Test
  public void testModuleNameTemplateArtifactId() {
    importProject("""
                    <groupId>test</groupId>
                    <artifactId>artifactId</artifactId>
                    <version>1</version>
                    """);

    assertEquals("artifactId", ModuleManager.getInstance(myProject).getModules()[0].getName());
  }

  @Test
  public void testModuleNameTemplateGroupIdArtifactId() {
    Registry.get("maven.import.module.name.template").setValue("groupId.artifactId", getTestRootDisposable());

    importProject("""
                    <groupId>myGroup</groupId>
                    <artifactId>artifactId</artifactId>
                    <version>1</version>
                    """);

    assertEquals("myGroup.artifactId", ModuleManager.getInstance(myProject).getModules()[0].getName());
  }

  @Test
  public void testModuleNameTemplateFolderName() {
    Registry.get("maven.import.module.name.template").setValue("folderName", getTestRootDisposable());

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>ignoredArtifactId</artifactId>
                    <version>1</version>
                    """);

    assertNotSame("ignoredArtifactId", myProjectRoot.getName());
    assertEquals(myProjectRoot.getName(), ModuleManager.getInstance(myProject).getModules()[0].getName());
  }
}
