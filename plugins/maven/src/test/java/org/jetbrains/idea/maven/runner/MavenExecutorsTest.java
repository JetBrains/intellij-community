package org.jetbrains.idea.maven.runner;

import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.MavenTestCase;
import org.jetbrains.idea.maven.NullMavenConsole;
import org.jetbrains.idea.maven.embedder.MavenConsole;

import java.io.File;
import java.util.Arrays;

public class MavenExecutorsTest extends MavenTestCase {
  public void testExternalExecutor() throws Exception {
    VfsUtil.saveText(createProjectSubFile("src/main/java/A.java"), "public class A {}");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>");

    assertFalse(new File(getProjectPath(), "target").exists());

    MavenRunnerParameters params = new MavenRunnerParameters(true, getProjectPath(), Arrays.asList("compile"), null);
    MavenRunnerSettings settings = new MavenRunnerSettings();

    MavenExecutor e;
    settings.setJreName(MavenRunnerSettings.USE_INTERNAL_JAVA);
    e = new MavenExternalExecutor(params, getMavenGeneralSettings(), settings, NULL_MAVEN_CONSOLE);

    assertTrue(e.execute(new EmptyProgressIndicator()));

    assertTrue(new File(getProjectPath(), "target").exists());
  }

  public void testReportingEmbedderErrorsOnCompilation() throws Exception {
    VirtualFile file = createProjectSubFile("src/main/java/A.java");
    VfsUtil.saveText(file, "invalid content");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>");

    assertFalse(new File(getProjectPath(), "target").exists());

    doTestReportingEmbedderErrors(
        "compile",
        "[ERROR] Compilation failure" +
        MavenConsole.LINE_SEPARATOR +
        FileUtil.toSystemDependentName(file.getPath()) + ":[1,0] class or interface expected");
  }

  public void testReportingEmbedderErrorsOnPluginExecution() throws Exception {
    doTestReportingEmbedderErrors(
        "archetype:create",
        "[ERROR] Error creating from archetype" +
        MavenConsole.LINE_SEPARATOR + MavenConsole.LINE_SEPARATOR +
        "Artifact ID must be specified when creating a new project from an archetype.");
  }

  private void doTestReportingEmbedderErrors(String goal, String containingString) {
    MavenRunnerParameters params = new MavenRunnerParameters(true, getProjectPath(), Arrays.asList(goal), null);

    final StringBuilder buffer = new StringBuilder();
    MavenConsole console = new NullMavenConsole() {
      @Override
      protected void doPrint(String text, OutputType type) {
        buffer.append(text);
      }
    };

    MavenExecutor e = new MavenEmbeddedExecutor(params, getMavenGeneralSettings(), new MavenRunnerSettings(), console);
    e.execute(new EmptyProgressIndicator());
    assertTrue(buffer.toString(), buffer.toString().contains(containingString));
  }
}
