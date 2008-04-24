package org.jetbrains.idea.maven.repository;

import org.jetbrains.idea.maven.MavenTestCase;
import org.jetbrains.idea.maven.core.util.MavenId;

public class MavenPluginsRepositoryTest extends MavenTestCase {
  public void testLoadingPluginInfo() throws Exception {
    MavenId id = new MavenId("org.apache.maven.plugins", "maven-compiler-plugin", "2.0.2");
    PluginPluginInfo p = MavenPluginsRepository.getInstance(myProject).loadPluginInfo(id);

    assertEquals("org.apache.maven.plugins", p.getGroupId());
    assertEquals("maven-compiler-plugin", p.getArtifactId());
    assertEquals("2.0.2", p.getVersion());
  }

  public void testPluginGoals() throws Exception {
    MavenId id = new MavenId("org.apache.maven.plugins", "maven-compiler-plugin", "2.0.2");
    PluginPluginInfo p = MavenPluginsRepository.getInstance(myProject).loadPluginInfo(id);

    assertEquals("compiler", p.getGoalPrefix());
    assertUnorderedElementsAreEqual(p.getGoals(), "compiler:compile", "compiler:testCompile");
  }
}
