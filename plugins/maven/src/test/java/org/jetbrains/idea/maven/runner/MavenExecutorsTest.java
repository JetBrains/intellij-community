package org.jetbrains.idea.maven.runner;

import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.maven.project.MavenProject;
import org.jetbrains.idea.maven.MavenTestCase;
import org.jetbrains.idea.maven.NullMavenConsole;
import org.jetbrains.idea.maven.embedder.MavenConsole;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

public class MavenExecutorsTest extends MavenTestCase {
  public void testEmbeddedExecutor() throws Exception {
    doTestExecution(true);
  }

  public void testExternalExecutor() throws Exception {
    doTestExecution(false);
  }

  private void doTestExecution(boolean useEmbedder) throws IOException {
    VfsUtil.saveText(createProjectSubFile("src/main/java/A.java"), "public class A {}");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>");

    assertFalse(new File(getProjectPath(), "target").exists());

    MavenRunnerParameters params = new MavenRunnerParameters(true, getProjectPath(), Arrays.asList("compile"), null);
    MavenRunnerSettings settings = new MavenRunnerSettings();

    MavenExecutor e;
    if (useEmbedder) {
      e = new MavenEmbeddedExecutor(params, getMavenGeneralSettings(), settings, new NullMavenConsole());
    }
    else {
      settings.setJreName(MavenRunnerSettings.USE_INTERNAL_JAVA);
      e = new MavenExternalExecutor(params, getMavenGeneralSettings(), settings, new NullMavenConsole());
    }

    assertTrue(e.execute(new ArrayList<MavenProject>(), new EmptyProgressIndicator()));

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

    final StringBuffer buffer = new StringBuffer();
    MavenConsole console = new NullMavenConsole() {
      @Override
      protected void doPrint(String text, OutputType type) {
        buffer.append(text);
      }
    };

    MavenExecutor e = new MavenEmbeddedExecutor(params, getMavenGeneralSettings(), new MavenRunnerSettings(), console);
    e.execute(new ArrayList<MavenProject>(), new EmptyProgressIndicator());
    assertTrue(buffer.toString(), buffer.toString().contains(containingString));
  }

  public void testCollectingProjectsFromEmbeddedExecutor() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "</modules>");

    VirtualFile m1 = createModulePom("m1",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>m1</artifactId>" +
                                     "<version>1</version>");

    VirtualFile m2 = createModulePom("m2",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>m2</artifactId>" +
                                     "<version>1</version>");

    MavenRunnerParameters params = new MavenRunnerParameters(true, getProjectPath(), Arrays.asList("compile"), null);
    MavenEmbeddedExecutor e = new MavenEmbeddedExecutor(params, getMavenGeneralSettings(), new MavenRunnerSettings(), new NullMavenConsole())
        ;

    ArrayList<MavenProject> result = new ArrayList<MavenProject>();
    assertTrue(e.execute(result, new EmptyProgressIndicator()));

    assertEquals(3, result.size());
    assertEquals(new File(myProjectPom.getPath()), result.get(0).getFile());
    assertEquals(new File(m1.getPath()), result.get(1).getFile());
    assertEquals(new File(m2.getPath()), result.get(2).getFile());
  }
}
