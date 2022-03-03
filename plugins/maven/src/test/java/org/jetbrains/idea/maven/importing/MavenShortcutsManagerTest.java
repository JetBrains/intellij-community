// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.tasks.MavenKeymapExtension;
import org.jetbrains.idea.maven.tasks.MavenShortcutsManager;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class MavenShortcutsManagerTest extends MavenMultiVersionImportingTestCase {
  private MavenShortcutsManager myShortcutsManager;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myShortcutsManager = MavenShortcutsManager.getInstance(myProject);
    myShortcutsManager.doInit(myProject);
    initProjectsManager(true);
  }

  @Override
  public void tearDown() throws Exception {
    try {
      MavenKeymapExtension.clearActions(myProject);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      myShortcutsManager = null;
      super.tearDown();
    }
  }

  @Test
  public void testRefreshingActionsOnImport() {
    assertTrue(getProjectActions().isEmpty());

    VirtualFile p1 = createModulePom("p1", "<groupId>test</groupId>" +
                                           "<artifactId>p1</artifactId>" +
                                           "<version>1</version>");

    VirtualFile p2 = createModulePom("p2", "<groupId>test</groupId>" +
                                           "<artifactId>p2</artifactId>" +
                                           "<version>1</version>");
    importProjects(p1, p2);

    assertEmptyKeymap();
  }

  @Test
  public void testRefreshingOnProjectRead() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertEmptyKeymap();
    String goal = "clean";
    assignShortcut(myProjectPom, goal, "alt shift X");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <plugins>" +
                  "    <plugin>" +
                  "      <groupId>org.apache.maven.plugins</groupId>" +
                  "      <artifactId>maven-surefire-plugin</artifactId>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");

    assertKeymapContains(myProjectPom, goal);
  }

  @Test
  public void testRefreshingOnPluginResolve() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertEmptyKeymap();

    String goal = "org.apache.maven.plugins:maven-surefire-plugin:2.4.3:test";
    assignShortcut(myProjectPom, goal, "alt shift X");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <plugins>" +
                  "    <plugin>" +
                  "      <groupId>org.apache.maven.plugins</groupId>" +
                  "      <artifactId>maven-surefire-plugin</artifactId>" +
                  "      <version>2.4.3</version>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");
    resolvePlugins();

    assertKeymapContains(myProjectPom, goal);
  }

  @Test
  public void testActionWhenSeveralSimilarPlugins() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <plugins>" +
                  "    <plugin>" +
                  "      <groupId>org.apache.maven.plugins</groupId>" +
                  "      <artifactId>maven-surefire-plugin</artifactId>" +
                  "      <version>2.4.3</version>" +
                  "    </plugin>" +
                  "    <plugin>" +
                  "      <groupId>org.apache.maven.plugins</groupId>" +
                  "      <artifactId>maven-surefire-plugin</artifactId>" +
                  "      <version>2.4.3</version>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");
    String goal = "org.apache.maven.plugins:maven-surefire-plugin:2.4.3:test";
    assignShortcut(myProjectPom, goal, "alt shift X");
    resolvePlugins();

    assertKeymapContains(myProjectPom, goal);
  }

  @Test
  public void testRefreshingOnProjectAddition() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    VirtualFile m = createModulePom("module", "<groupId>test</groupId>" +
                                              "<artifactId>module</artifactId>" +
                                              "<version>1</version>");

    String goal = "clean";
    assertKeymapDoesNotContain(m, goal);

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +
                     "<modules>" +
                     "  <module>module</module>" +
                     "</modules>");

    importProject();

    assertEmptyKeymap();
    assignShortcut(m, goal, "alt shift X");
    assertKeymapContains(m, goal);
  }

  @Test
  public void testDeletingActionOnProjectRemoval() throws IOException {
    final VirtualFile p1 = createModulePom("p1", "<groupId>test</groupId>" +
                                                 "<artifactId>p1</artifactId>" +
                                                 "<version>1</version>");

    VirtualFile p2 = createModulePom("p2", "<groupId>test</groupId>" +
                                           "<artifactId>p2</artifactId>" +
                                           "<version>1</version>");

    importProjects(p1, p2);

    assertEmptyKeymap();
    String goal = "clean";
    assignShortcut(p1, goal, "alt shift X");
    assignShortcut(p2, goal, "alt shift Y");

    assertKeymapContains(p1, goal);
    assertKeymapContains(p2, goal);

    WriteCommandAction.writeCommandAction(myProject).run(() -> p1.delete(this));

    configConfirmationForYesAnswer();
    importProjects(p1, p2);

    assertKeymapDoesNotContain(p1, goal);
    assertKeymapContains(p2, goal);
  }

  @Test
  public void testRefreshingActionsOnChangingIgnoreFlag() {
    VirtualFile p1 = createModulePom("p1", "<groupId>test</groupId>" +
                                           "<artifactId>p1</artifactId>" +
                                           "<version>1</version>");

    VirtualFile p2 = createModulePom("p2", "<groupId>test</groupId>" +
                                           "<artifactId>p2</artifactId>" +
                                           "<version>1</version>");
    importProjects(p1, p2);

    assertEmptyKeymap();
    String goal = "clean";
    assignShortcut(p1, goal, "alt shift X");
    assignShortcut(p2, goal, "alt shift Y");

    assertKeymapContains(p1, goal);
    assertKeymapContains(p2, goal);

    myProjectsManager.setIgnoredState(Collections.singletonList(myProjectsManager.findProject(p1)), true);


    assertKeymapDoesNotContain(p1, goal);
    assertKeymapContains(p2, goal);

    myProjectsManager.setIgnoredState(Collections.singletonList(myProjectsManager.findProject(p1)), false);

    assertKeymapContains(p1, goal);
    assertKeymapContains(p2, goal);
  }

  private void assertKeymapContains(VirtualFile pomFile, String goal) {
    String id = myShortcutsManager.getActionId(pomFile.getPath(), goal);
    assertContain(getProjectActions(), id);
  }

  private void assertEmptyKeymap() {
    assertEmpty(getProjectActions());
  }

  private void assertKeymapDoesNotContain(VirtualFile pomFile, String goal) {
    String id = myShortcutsManager.getActionId(pomFile.getPath(), goal);
    assertDoNotContain(getProjectActions(), id);
  }

  private void assignShortcut(VirtualFile pomFile, String goal, String shortcut) {
    MavenProject mavenProject = myProjectsManager.findProject(pomFile);
    assert mavenProject != null;
    String actionId = myShortcutsManager.getActionId(mavenProject.getPath(), goal);
    assert actionId != null;
    AnAction action = ActionManager.getInstance().getAction(actionId);
    if (action == null) {
      MavenKeymapExtension.getOrRegisterAction(mavenProject, actionId, goal);
    }
    Keymap activeKeymap = KeymapManager.getInstance().getActiveKeymap();
    activeKeymap.addShortcut(actionId, KeyboardShortcut.fromString(shortcut));
  }

  private List<String> getProjectActions() {
    String prefix = MavenKeymapExtension.getActionPrefix(myProject, null);
    return ActionManager.getInstance().getActionIdList(prefix);
  }
}
