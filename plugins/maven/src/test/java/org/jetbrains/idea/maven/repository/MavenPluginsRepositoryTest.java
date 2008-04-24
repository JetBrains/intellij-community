package org.jetbrains.idea.maven.repository;

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

  public void testParameters() throws Exception {
    MavenPluginInfo.Mojo m = p.findMojo("compile");
    List<MavenPluginInfo.ParameterInfo> params = m.getParameterInfos();
    assertFalse(params.isEmpty());

    MavenPluginInfo.ParameterInfo i = m.findParameterInfo("compilerVersion");
    assertNotNull(i);
    assertTrue(i.getDescription(), i.getDescription().startsWith("Version of the compiler to use"));
  }

  public void testDoNotIncludeReadOnlyParameters() throws Exception {
    assertNull(p.findMojo("compile").findParameterInfo("basedir"));
  }

  public void testDoNotIncludeOtherMojosParameters() throws Exception {
    assertNotNull(p.findMojo("testCompile").findParameterInfo("testIncludes"));
    assertNull(p.findMojo("compile").findParameterInfo("testIncludes"));
  }

  public void testLoadingPluginsFromStandsrdGroupsIfGroupIsNotSpecified() throws Exception {
    assertNotNull(new MavenId(null, "maven-compiler-plugin", "2.0.2"));
    assertNotNull(new MavenId(null, "build-helper-maven-plugin", "1.0"));
  }
}
