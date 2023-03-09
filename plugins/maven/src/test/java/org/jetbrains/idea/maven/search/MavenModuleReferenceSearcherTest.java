// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.search;

import com.intellij.maven.testFramework.MavenDomTestCase;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import com.intellij.refactoring.rename.RenameDialog;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

public class MavenModuleReferenceSearcherTest extends MavenDomTestCase {

  private void renameDirectory(@NotNull PsiDirectory directory, @NotNull String newName) {
    var renameDialog = new RenameDialog(myProject, directory, directory, null);
    renameDialog.performRename(newName);
  }

  @Test
  public void testDirectoryRenameModuleReferenceChanged() {
    var parentFile = createProjectPom("""
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

    var newModulePath = "m1new";

    var m1Parent = m1File.getParent();
    var directory = PsiDirectoryFactory.getInstance(myProject).createDirectory(m1Parent);

    renameDirectory(directory, newModulePath);

    var tag = findTag(parentFile, "project.modules.module");
    assertEquals(newModulePath, tag.getValue().getText());
  }

  @Test
  public void testParentDirectoryRenameModuleReferenceChanged() {
    var parentFile = createProjectPom("""
                  <groupId>group</groupId>
                  <artifactId>parent</artifactId>
                  <version>1</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>m/m1</module>
                  </modules>
                  """);
    var m1File = createModulePom("m/m1", """
                  <artifactId>m2</artifactId>
                  <version>1</version>
                  <parent>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                  </parent>
                  """);
    importProject();

    var newDirectoryName = "mNew";
    var newModulePath = "mNew/m1";

    var m1Parent = m1File.getParent().getParent();
    var directory = PsiDirectoryFactory.getInstance(myProject).createDirectory(m1Parent);

    renameDirectory(directory, newDirectoryName);

    var tag = findTag(parentFile, "project.modules.module");
    assertEquals(newModulePath, tag.getValue().getText());
  }

  @Test
  public void testDirectoryRenameModuleRelativeReferenceChanged() {
    var parentFile = createProjectPom("""
                  <groupId>group</groupId>
                  <artifactId>parent</artifactId>
                  <version>1</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>./m1</module>
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

    var newDirectoryName = "m1new";
    var newModulePath = "./m1new";

    var m1Parent = m1File.getParent();
    var directory = PsiDirectoryFactory.getInstance(myProject).createDirectory(m1Parent);

    renameDirectory(directory, newDirectoryName);

    var tag = findTag(parentFile, "project.modules.module");
    assertEquals(newModulePath, tag.getValue().getText());
  }

  @Test
  public void testDirectoryRenameModuleParentPathReferenceChanged() {
    var parentFile = createProjectPom("""
                  <groupId>group</groupId>
                  <artifactId>parent</artifactId>
                  <version>1</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>parent2</module>
                  </modules>
                  """);
    var parent2File = createModulePom("parent2", """
                  <groupId>group</groupId>
                  <artifactId>parent2</artifactId>
                  <version>1</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>../m1</module>
                  </modules>
                  """);
    var m1File = createModulePom("m1", """
                  <artifactId>m1</artifactId>
                  <version>1</version>
                  <parent>
                    <groupId>group</groupId>
                    <artifactId>parent2</artifactId>
                    <relativePath>../parent2/pom.xml</relativePath>
                  </parent>
                  """);
    importProject();

    var newDirectoryName = "m1new";
    var newModulePath = "../m1new";

    var m1Parent = m1File.getParent();
    var directory = PsiDirectoryFactory.getInstance(myProject).createDirectory(m1Parent);

    renameDirectory(directory, newDirectoryName);

    var tag = findTag(parent2File, "project.modules.module");
    assertEquals(newModulePath, tag.getValue().getText());
  }

  @Test
  public void testDirectoryRenameModuleWeirdNameReferenceChanged() {
    var parentFile = createProjectPom("""
                  <groupId>group</groupId>
                  <artifactId>parent</artifactId>
                  <version>1</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>module/module</module>
                  </modules>
                  """);
    var m1File = createModulePom("module/module", """
                  <artifactId>m1</artifactId>
                  <version>1</version>
                  <parent>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                  </parent>
                  """);
    importProject();

    var newDirectoryName = "module-new";
    var newModulePath = "module/module-new";

    var m1Parent = m1File.getParent();
    var directory = PsiDirectoryFactory.getInstance(myProject).createDirectory(m1Parent);

    renameDirectory(directory, newDirectoryName);

    var tag = findTag(parentFile, "project.modules.module");
    assertEquals(newModulePath, tag.getValue().getText());
  }

  @Test
  public void testParentDirectoryRenameModuleWeirdNameReferenceChanged() {
    var parentFile = createProjectPom("""
                  <groupId>group</groupId>
                  <artifactId>parent</artifactId>
                  <version>1</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>module/module</module>
                  </modules>
                  """);
    var m1File = createModulePom("module/module", """
                  <artifactId>m1</artifactId>
                  <version>1</version>
                  <parent>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                  </parent>
                  """);
    importProject();

    var newDirectoryName = "module-new";
    var newModulePath = "module-new/module";

    var m1Parent = m1File.getParent().getParent();
    var directory = PsiDirectoryFactory.getInstance(myProject).createDirectory(m1Parent);

    renameDirectory(directory, newDirectoryName);

    var tag = findTag(parentFile, "project.modules.module");
    assertEquals(newModulePath, tag.getValue().getText());
  }
}
