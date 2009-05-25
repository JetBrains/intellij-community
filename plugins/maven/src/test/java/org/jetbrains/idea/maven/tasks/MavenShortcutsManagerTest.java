package org.jetbrains.idea.maven.tasks;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.MavenImportingTestCase;

import java.util.Arrays;
import java.util.List;

public class MavenShortcutsManagerTest extends MavenImportingTestCase {
  private MavenShortcutsManager myShortcutsManager;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myShortcutsManager = MavenShortcutsManager.getInstance(myProject);
    myShortcutsManager.doInit();
    initProjectsManager(true);
  }

  public void testRefreshingActionsOnImport() throws Exception {
    assertTrue(getProjectActions().isEmpty());

    VirtualFile p1 = createModulePom("p1", "<groupId>test</groupId>" +
                                           "<artifactId>p1</artifactId>" +
                                           "<version>1</version>");

    VirtualFile p2 = createModulePom("p2", "<groupId>test</groupId>" +
                                           "<artifactId>p2</artifactId>" +
                                           "<version>1</version>");
    importProjects(p1, p2);

    assertKeymapContains(p1, "clean");
    assertKeymapContains(p2, "clean");
  }

  public void testRefreshingOnProjectRead() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertKeymapContains(myProjectPom, "clean");

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

    assertKeymapContains(myProjectPom, "clean");
  }

  public void testRefreshingOnPluginResolve() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertKeymapDoesNotContain(myProjectPom, "org.apache.maven.plugins:maven-surefire-plugin:2.4.2:test");

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
    resolvePlugins();

    assertKeymapContains(myProjectPom, "org.apache.maven.plugins:maven-surefire-plugin:2.4.2:test");
  }

  public void testActionWhenSeveralSimilarPlugins() throws Exception {
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
                  "    </plugin>" +
                  "    <plugin>" +
                  "      <groupId>org.apache.maven.plugins</groupId>" +
                  "      <artifactId>maven-surefire-plugin</artifactId>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");
    resolvePlugins();

    assertKeymapContains(myProjectPom, "org.apache.maven.plugins:maven-surefire-plugin:2.4.2:test");
  }

  public void testRefreshingOnProjectAddition() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    VirtualFile m = createModulePom("module", "<groupId>test</groupId>" +
                                              "<artifactId>module</artifactId>" +
                                              "<version>1</version>");

    assertKeymapDoesNotContain(m, "clean");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>module</module>" +
                     "</modules>");
    waitForReadingCompletion();

    assertKeymapContains(m, "clean");
  }

  public void testDeletingActionOnProjectRemoval() throws Exception {
    VirtualFile p1 = createModulePom("p1", "<groupId>test</groupId>" +
                                           "<artifactId>p1</artifactId>" +
                                           "<version>1</version>");

    VirtualFile p2 = createModulePom("p2", "<groupId>test</groupId>" +
                                           "<artifactId>p2</artifactId>" +
                                           "<version>1</version>");

    importProjects(p1, p2);

    assertKeymapContains(p1, "clean");
    assertKeymapContains(p2, "clean");

    p1.delete(this);
    waitForReadingCompletion();

    assertKeymapDoesNotContain(p1, "clean");
    assertKeymapContains(p2, "clean");
  }

  public void testRefreshingActionsOnChangingIgnoreFlag() throws Exception {
    VirtualFile p1 = createModulePom("p1", "<groupId>test</groupId>" +
                                           "<artifactId>p1</artifactId>" +
                                           "<version>1</version>");

    VirtualFile p2 = createModulePom("p2", "<groupId>test</groupId>" +
                                           "<artifactId>p2</artifactId>" +
                                           "<version>1</version>");
    importProjects(p1, p2);

    assertKeymapContains(p1, "clean");
    assertKeymapContains(p2, "clean");

    myProjectsManager.setIgnoredState(Arrays.asList(myProjectsManager.findProject(p1)), true);

    assertKeymapDoesNotContain(p1, "clean");
    assertKeymapContains(p2, "clean");

    myProjectsManager.setIgnoredState(Arrays.asList(myProjectsManager.findProject(p1)), false);

    assertKeymapContains(p1, "clean");
    assertKeymapContains(p2, "clean");
  }

  private void assertKeymapContains(VirtualFile pomFile, String goal) {
    String id = myShortcutsManager.getActionId(pomFile.getPath(), goal);
    assertTrue("Action " + id + " not found among: \n" + StringUtil.join(getProjectActions(), "\n"), getProjectActions().contains(id));
  }

  private void assertKeymapDoesNotContain(VirtualFile pomFile, String goal) {
    String id = myShortcutsManager.getActionId(pomFile.getPath(), goal);
    assertFalse(getProjectActions().contains(id));
  }

  private List<String> getProjectActions() {
    String prefix = MavenKeymapExtension.getActionPrefix(myProject, null);
    return Arrays.asList(ActionManager.getInstance().getActionIds(prefix));
  }
}
