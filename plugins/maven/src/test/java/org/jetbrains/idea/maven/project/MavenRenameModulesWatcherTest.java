// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project;

import com.intellij.ProjectTopics;
import com.intellij.maven.testFramework.MavenDomTestCase;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleWithNameAlreadyExists;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.List;

public class MavenRenameModulesWatcherTest extends MavenDomTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myProjectsManager.initForTests();
    myProjectsManager.listenForExternalChanges();
  }

  private void renameModule(@NotNull String oldName, @NotNull String newName) {
    var module = ModuleManager.getInstance(myProject).findModuleByName(oldName);
    var modifiableModel = ModuleManager.getInstance(myProject).getModifiableModel();
    try {
      modifiableModel.renameModule(module, newName);
    }
    catch (ModuleWithNameAlreadyExists e) {
      throw new RuntimeException(e);
    }
    CommandProcessor.getInstance().executeCommand(myProject, () -> ApplicationManager.getApplication().runWriteAction(() -> modifiableModel.commit()), "renaming model", null);
    myProject.getMessageBus().syncPublisher(ProjectTopics.MODULES).modulesRenamed(myProject, List.of(module), m -> oldName);
  }

  @Test
  public void testModuleRenameArtifactIdChanged() {
    importProject("""
                  <groupId>group</groupId>
                  <artifactId>module</artifactId>
                  <version>1</version>
                  """);

    var oldModuleName = "module";
    var newModuleName = "newModule";
    renameModule(oldModuleName, newModuleName);

    var tag = findTag("project.artifactId");
    assertEquals(newModuleName, tag.getValue().getText());
  }

  @Test
  public void testModuleRenameImplicitGroupIdArtifactIdChanged() {
    createProjectPom("""
                  <groupId>group</groupId>
                  <artifactId>parent</artifactId>
                  <version>1</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>m1</module>
                  </modules>
                  """);
    var m1File = createModulePom("m1", """
                  <artifactId>m1</artifactId>
                  <version>1</version>
                  <parent>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                  </parent>
                  """);
    importProject();

    var oldModuleName = "m1";
    var newModuleName = "m1new";
    renameModule(oldModuleName, newModuleName);

    var tag = findTag(m1File, "project.artifactId");
    assertEquals(newModuleName, tag.getValue().getText());
  }

  @Test
  public void testModuleRenameParentChanged() {
    createProjectPom("""
                  <groupId>group</groupId>
                  <artifactId>parent</artifactId>
                  <version>1</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>m1</module>
                  </modules>
                  """);
    var m1File = createModulePom("m1", """
                  <artifactId>m1</artifactId>
                  <version>1</version>
                  <parent>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                  </parent>
                  """);
    importProject();

    var oldModuleName = "parent";
    var newModuleName = "newParent";
    renameModule(oldModuleName, newModuleName);

    var tag = findTag(m1File, "project.parent.artifactId");
    assertEquals(newModuleName, tag.getValue().getText());
  }

  @Test
  public void testModuleRenameDependenciesChanged() {
    createProjectPom("""
                  <groupId>group</groupId>
                  <artifactId>parent</artifactId>
                  <version>1</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>m1</module>
                    <module>m2</module>
                  </modules>
                  """);
    var m1File = createModulePom("m1", """
                  <artifactId>m1</artifactId>
                  <version>1</version>
                  <parent>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                  </parent>
                  """);
    var m2File = createModulePom("m2", """
                  <artifactId>m2</artifactId>
                  <version>1</version>
                  <parent>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                  </parent>
                  <dependencies>
                    <dependency>
                      <version>1</version>
                      <groupId>group</groupId>
                      <artifactId>m1</artifactId>
                    </dependency>
                  </dependencies>
                  """);
    importProject();

    var oldModuleName = "m1";
    var newModuleName = "m1new";
    renameModule(oldModuleName, newModuleName);

    var tag = findTag(m2File, "project.dependencies.dependency.artifactId");
    assertEquals(newModuleName, tag.getValue().getText());
  }

  @Test
  public void testModuleRenameExclusionsChanged() {
    createProjectPom("""
                  <groupId>group</groupId>
                  <artifactId>parent</artifactId>
                  <version>1</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>m1</module>
                    <module>m2</module>
                  </modules>
                  """);
    var m1File = createModulePom("m1", """
                  <groupId>group</groupId>
                  <artifactId>m1</artifactId>
                  <version>1</version>
                  <parent>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                  </parent>
                  """);
    var m2File = createModulePom("m2", """
                  <groupId>group</groupId>
                  <artifactId>m2</artifactId>
                  <version>1</version>
                  <parent>
                    <version>1</version>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                  </parent>
                  <dependencies>
                    <dependency>
                      <version>1</version>
                      <groupId>group</groupId>
                      <artifactId>m1</artifactId>
                    </dependency>
                  </dependencies>
                  """);
    var m3File = createModulePom("m2", """
                  <groupId>group</groupId>
                  <artifactId>m3</artifactId>
                  <version>1</version>
                  <parent>
                    <version>1</version>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                  </parent>
                  <dependencies>
                    <dependency>
                      <version>1</version>
                      <groupId>group</groupId>
                      <artifactId>m2</artifactId>
                      <exclusions>
                        <exclusion>
                        <groupId>group</groupId>
                        <artifactId>m1</artifactId>
                        </exclusion>
                      </exclusions>
                    </dependency>
                  </dependencies>
                  """);
    importProject();

    var oldModuleName = "m1";
    var newModuleName = "m1new";
    renameModule(oldModuleName, newModuleName);

    var tag = findTag(m3File, "project.dependencies.dependency.exclusions.exclusion.artifactId");
    assertEquals(newModuleName, tag.getValue().getText());
  }

  @Test
  public void testModuleRenameAnotherGroupArtifactIdNotChanged() {
    createProjectPom("""
                  <groupId>group</groupId>
                  <artifactId>parent</artifactId>
                  <version>1</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>m1</module>
                    <module>m2</module>
                  </modules>
                  """);
    var m1File = createModulePom("m1", """
                  <groupId>group1</groupId>
                  <artifactId>m1</artifactId>
                  <version>1</version>
                  <parent>
                    <version>1</version>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                  </parent>
                  """);
    var m2File = createModulePom("m2", """
                  <groupId>group</groupId>
                  <artifactId>m2</artifactId>
                  <version>1</version>
                  <parent>
                    <version>1</version>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                  </parent>
                  <dependencies>
                    <dependency>
                      <version>1</version>
                      <groupId>anotherGroup</groupId>
                      <artifactId>m1</artifactId>
                    </dependency>
                  </dependencies>
                  """);
    importProject();

    var oldModuleName = "m1";
    var newModuleName = "m1new";
    renameModule(oldModuleName, newModuleName);

    var tag = findTag(m2File, "project.dependencies.dependency.artifactId");
    assertEquals(oldModuleName, tag.getValue().getText());
  }
}
