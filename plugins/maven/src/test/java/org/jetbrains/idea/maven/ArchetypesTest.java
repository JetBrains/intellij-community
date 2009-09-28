package org.jetbrains.idea.maven;

import com.intellij.openapi.progress.EmptyProgressIndicator;
import gnu.trove.THashMap;
import org.jetbrains.idea.maven.execution.MavenExecutor;
import org.jetbrains.idea.maven.execution.MavenExternalExecutor;
import org.jetbrains.idea.maven.execution.MavenRunnerParameters;
import org.jetbrains.idea.maven.execution.MavenRunnerSettings;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

public class ArchetypesTest extends MavenTestCase {
  public void testGenerating() throws Exception {
    File dir = new File(myDir.getPath(), "generated");
    dir.mkdirs();

    MavenRunnerParameters params = new MavenRunnerParameters(false, dir.getPath(),
                                                             Arrays.asList("archetype:create"),
                                                             Collections.<String>emptyList());

    MavenRunnerSettings settings = new MavenRunnerSettings();
    Map<String, String> props = new THashMap<String, String>();
    props.put("archetypeGroupId", "org.apache.maven.archetypes");
    props.put("archetypeArtifactId", "maven-archetype-quickstart");
    props.put("archetypeVersion", "1.0");
    props.put("groupId", "foo");
    props.put("artifactId", "bar");

    settings.setMavenProperties(props);
    MavenExecutor exec;
    settings.setJreName(MavenRunnerSettings.USE_INTERNAL_JAVA);
    exec = new MavenExternalExecutor(params, getMavenGeneralSettings(), settings, NULL_MAVEN_CONSOLE);
    exec.execute(new EmptyProgressIndicator());

    assertTrue(new File(dir, "bar/pom.xml").exists());
  }
}
