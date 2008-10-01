package org.jetbrains.idea.maven;

import com.intellij.openapi.progress.EmptyProgressIndicator;
import org.apache.maven.project.MavenProject;
import org.jetbrains.idea.maven.runner.*;

import java.io.File;
import java.util.*;

public class ArchetypesTest extends MavenTestCase {
  public void testGeneratingUsingEmbeddedMaven() throws Exception {
    doTestGenerating(true);
  }

  public void testGeneratingUsingExternalMaven() throws Exception {
    doTestGenerating(false);
  }
  
  private void doTestGenerating(boolean useEmbedder) throws Exception {
    File dir = new File(myDir.getPath(), "generated");
    dir.mkdirs();

    MavenRunnerParameters params = new MavenRunnerParameters(false, dir.getPath(),
                                                             Arrays.asList("archetype:create"),
                                                             Collections.<String>emptyList());

    MavenRunnerSettings settings = new MavenRunnerSettings();
    Map<String, String> props = new HashMap<String, String>();
    props.put("archetypeGroupId", "org.apache.maven.archetypes");
    props.put("archetypeArtifactId", "maven-archetype-quickstart");
    props.put("archetypeVersion", "1.0");
    props.put("groupId", "foo");
    props.put("artifactId", "bar");

    settings.setMavenProperties(props);
    MavenExecutor exec;
    if (useEmbedder) {
      exec = new MavenEmbeddedExecutor(params, getMavenCoreSettings(), settings, new TestConsoleAdapter());
    } else {
      settings.setJreName(MavenRunnerSettings.USE_INTERNAL_JAVA);
      exec = new MavenExternalExecutor(params, getMavenCoreSettings(), settings, new TestConsoleAdapter());
    }
    exec.execute(new ArrayList<MavenProject>(), new EmptyProgressIndicator());

    assertTrue(new File(dir, "bar/pom.xml").exists());
  }
}