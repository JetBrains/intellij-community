package org.jetbrains.idea.maven.execution;

import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.vfs.VfsUtil;
import org.jetbrains.idea.maven.MavenTestCase;

import java.io.File;
import java.util.Arrays;

public class MavenExecutorsTest extends MavenTestCase {
  public void testExternalExecutor() throws Exception {
    if (!hasM2Home()) return;

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
}
