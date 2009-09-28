package org.jetbrains.idea.maven.indices;

import org.jetbrains.idea.maven.MavenTestCase;
import org.jetbrains.idea.maven.project.MavenId;
import org.jetbrains.idea.maven.utils.MavenPluginInfo;
import org.jetbrains.idea.maven.utils.MavenArtifactUtil;

import java.util.ArrayList;
import java.util.List;

public class MavenPluginInfoReaderTest extends MavenTestCase {
  private MavenCustomRepositoryHelper myRepositoryHelper;
  private MavenPluginInfo p;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myRepositoryHelper = new MavenCustomRepositoryHelper(myDir, "plugins");

    setRepositoryPath(myRepositoryHelper.getTestDataPath("plugins"));

    MavenId id = new MavenId("org.apache.maven.plugins", "maven-compiler-plugin", "2.0.2");
    p = MavenArtifactUtil.readPluginInfo(getRepositoryFile(), id);
  }

  public void testLoadingPluginInfo() throws Exception {
    assertEquals("org.apache.maven.plugins", p.getGroupId());
    assertEquals("maven-compiler-plugin", p.getArtifactId());
    assertEquals("2.0.2", p.getVersion());
  }

  public void testGoals() throws Exception {
    assertEquals("compiler", p.getGoalPrefix());

    List<String> qualifiedGoals = new ArrayList<String>();
    List<String> displayNames = new ArrayList<String>();
    List<String> goals = new ArrayList<String>();
    for (MavenPluginInfo.Mojo m : p.getMojos()) {
      goals.add(m.getGoal());
      qualifiedGoals.add(m.getQualifiedGoal());
      displayNames.add(m.getDisplayName());
    }

    assertOrderedElementsAreEqual(goals, "compile", "testCompile");
    assertOrderedElementsAreEqual(qualifiedGoals,
                                  "org.apache.maven.plugins:maven-compiler-plugin:2.0.2:compile",
                                  "org.apache.maven.plugins:maven-compiler-plugin:2.0.2:testCompile");
    assertOrderedElementsAreEqual(displayNames, "compiler:compile", "compiler:testCompile");
  }
}
