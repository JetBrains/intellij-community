package org.jetbrains.idea.maven.indices;

import org.jetbrains.idea.maven.MavenTestCase;
import org.jetbrains.idea.maven.core.util.MavenId;

import java.util.ArrayList;
import java.util.List;

public class MavenPluginsRepositoryTest extends MavenTestCase {
  private MavenWithDataTestFixture myDataFixture;
  private MavenPluginInfo p;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myDataFixture = new MavenWithDataTestFixture(myDir);
    myDataFixture.setUp();

    setRepositoryPath(myDataFixture.getTestDataPath("plugins"));

    MavenId id = new MavenId("org.apache.maven.plugins", "maven-compiler-plugin", "2.0.2");
    p = MavenPluginsRepository.getInstance(myProject).loadPluginInfo(id);
  }

  public void testLoadingPluginInfo() throws Exception {
    assertEquals("org.apache.maven.plugins", p.getGroupId());
    assertEquals("maven-compiler-plugin", p.getArtifactId());
    assertEquals("2.0.2", p.getVersion());
  }

  public void testGoals() throws Exception {
    assertEquals("compiler", p.getGoalPrefix());

    List<String> qualifiedGoals = new ArrayList<String>();
    List<String> goals = new ArrayList<String>();
    for (MavenPluginInfo.Mojo m : p.getMojos()) {
      goals.add(m.getGoal());
      qualifiedGoals.add(m.getQualifiedGoal());
    }

    assertOrderedElementsAreEqual(goals, "compile", "testCompile");
    assertOrderedElementsAreEqual(qualifiedGoals, "compiler:compile", "compiler:testCompile");
  }
}
