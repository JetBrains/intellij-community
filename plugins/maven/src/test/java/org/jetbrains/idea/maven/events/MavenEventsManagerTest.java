package org.jetbrains.idea.maven.events;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.MavenImportingTestCase;

import java.util.Arrays;
import java.util.List;

public class MavenEventsManagerTest extends MavenImportingTestCase {
  private MavenEventsManager myEventsManager;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myEventsManager = MavenEventsManager.getInstance(myProject);
    myEventsManager.doInit();
    myMavenProjectsManager.initEventsHandling();
  }

  public void testRefreshingActionsOnImport() throws Exception {
    assertTrue(getProjectActions().isEmpty());

    VirtualFile p1 = createModulePom("p1", "<groupId>test</groupId>" +
                                           "<artifactId>p1</artifactId>" +
                                           "<version>1</version>");

    VirtualFile p2 = createModulePom("p2", "<groupId>test</groupId>" +
                                           "<artifactId>p2</artifactId>" +
                                           "<version>1</version>");
    importSeveralProjects(p1, p2);

    assertKeymapContains(p1, "clean");
    assertKeymapContains(p2, "clean");
  }

  public void testRefreshingOnProjectFileChange() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertKeymapDoesNotContain(myProjectPom, "surefire:test");

    updateProjectPom("<groupId>test</groupId>" +
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

    assertKeymapContains(myProjectPom, "surefire:test");
  }

  public void testRefreshingOnProjectAddition() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");


    VirtualFile m = createModulePom("module", "<groupId>test</groupId>" +
                                              "<artifactId>module</artifactId>" +
                                              "<version>1</version>");

    assertKeymapDoesNotContain(m, "clean");

    updateProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>module</module>" +
                     "</modules>");

    assertKeymapContains(m, "clean");
  }

  public void testDeletingActionOnProjectRemoval() throws Exception {
    VirtualFile p1 = createModulePom("p1", "<groupId>test</groupId>" +
                                           "<artifactId>p1</artifactId>" +
                                           "<version>1</version>");

    VirtualFile p2 = createModulePom("p2", "<groupId>test</groupId>" +
                                           "<artifactId>p2</artifactId>" +
                                           "<version>1</version>");

    importSeveralProjects(p1, p2);

    assertKeymapContains(p1, "clean");
    assertKeymapContains(p2, "clean");

    p1.delete(this);

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
    importSeveralProjects(p1, p2);

    assertKeymapContains(p1, "clean");
    assertKeymapContains(p2, "clean");

    myMavenProjectsManager.setIgnoredFlag(myMavenProjectsManager.findProject(p1), true);

    assertKeymapDoesNotContain(p1, "clean");
    assertKeymapContains(p2, "clean");

    myMavenProjectsManager.setIgnoredFlag(myMavenProjectsManager.findProject(p1), false);

    assertKeymapContains(p1, "clean");
    assertKeymapContains(p2, "clean");
  }

  private void assertKeymapContains(VirtualFile pomFile, String goal) {
    String id = myEventsManager.getActionId(pomFile.getPath(), goal);
    assertTrue(getProjectActions().contains(id));
  }

  private void assertKeymapDoesNotContain(VirtualFile pomFile, String goal) {
    String id = myEventsManager.getActionId(pomFile.getPath(), goal);
    assertFalse(getProjectActions().contains(id));
  }

  private List<String> getProjectActions() {
    String prefix = MavenKeymapExtension.getProjectPrefix(myProject);
    return Arrays.asList(ActionManager.getInstance().getActionIds(prefix));
  }
}
