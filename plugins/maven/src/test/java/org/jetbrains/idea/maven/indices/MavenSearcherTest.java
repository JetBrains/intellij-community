package org.jetbrains.idea.maven.indices;

import org.jetbrains.idea.maven.MavenTestCase;
import org.sonatype.nexus.index.ArtifactInfo;

import java.util.ArrayList;
import java.util.List;

public class MavenSearcherTest extends MavenTestCase {
  MavenIndicesTestFixture myIndicesFixture;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myIndicesFixture = new MavenIndicesTestFixture(myDir, myProject);
    myIndicesFixture.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    myIndicesFixture.tearDown();
    super.tearDown();
  }

  public void testClassSearch() throws Exception {
    assertClassSearchResults("TestCas",
                             "TestCase(junit.framework) junit:junit:4.0 junit:junit:3.8.2 junit:junit:3.8.1",
                             "TestCaseClassLoader(junit.runner) junit:junit:3.8.2 junit:junit:3.8.1");
    assertClassSearchResults("TESTcase",
                             "TestCase(junit.framework) junit:junit:4.0 junit:junit:3.8.2 junit:junit:3.8.1",
                             "TestCaseClassLoader(junit.runner) junit:junit:3.8.2 junit:junit:3.8.1");

    assertClassSearchResults("After",
                             "After(org.junit) junit:junit:4.0",
                             "AfterClass(org.junit) junit:junit:4.0");
    assertClassSearchResults("After ",
                             "After(org.junit) junit:junit:4.0");
    assertClassSearchResults("*After",
                             "After(org.junit) junit:junit:4.0",
                             "AfterClass(org.junit) junit:junit:4.0",
                             "BeforeAndAfterRunner(org.junit.internal.runners) junit:junit:4.0",
                             "InvokedAfterMatcher(org.jmock.core.matcher) jmock:jmock:1.2.0 jmock:jmock:1.1.0 jmock:jmock:1.0.0");

    // do not include package hits 
    assertClassSearchResults("JUnit",
                             "JUnit4TestAdapter(junit.framework) junit:junit:4.0",
                             "JUnit4TestAdapterCache(junit.framework) junit:junit:4.0",
                             "JUnit4TestCaseFacade(junit.framework) junit:junit:4.0",
                             "JUnitCore(org.junit.runner) junit:junit:4.0");

    //assertClassSearchResults("org.junit.After", "junit:junit:4.0");
  }

  public void testArtifactSearch() throws Exception {
    assertArtifactSearchResults("",
                                "jmock:jmock:1.2.0 jmock:jmock:1.1.0 jmock:jmock:1.0.0",
                                "junit:junit:4.0 junit:junit:3.8.2 junit:junit:3.8.1");
    assertArtifactSearchResults("j *1*",
                                "jmock:jmock:1.2.0 jmock:jmock:1.1.0 jmock:jmock:1.0.0",
                                "junit:junit:3.8.1");
    assertArtifactSearchResults("junit", "junit:junit:4.0 junit:junit:3.8.2 junit:junit:3.8.1");
    assertArtifactSearchResults("junit 3.", "junit:junit:3.8.2 junit:junit:3.8.1");
    assertArtifactSearchResults("uni 3.", "junit:junit:3.8.2 junit:junit:3.8.1");
    assertArtifactSearchResults("juni juni 3.", "junit:junit:3.8.2 junit:junit:3.8.1");
    assertArtifactSearchResults("junit foo");
    assertArtifactSearchResults("junit:", "junit:junit:4.0 junit:junit:3.8.2 junit:junit:3.8.1");
    assertArtifactSearchResults("junit:junit", "junit:junit:4.0 junit:junit:3.8.2 junit:junit:3.8.1");
    assertArtifactSearchResults("junit:junit:3.", "junit:junit:3.8.2 junit:junit:3.8.1");

    assertArtifactSearchResults("junit:junit:4.0", "junit:junit:4.0");
  }

  private void assertClassSearchResults(String pattern, String... expected) {
    List<String> actualArtifacts = new ArrayList<String>();
    for (MavenClassSearchResult eachResult : new MavenClassSearcher().search(myProject, pattern, 100)) {
      String s = eachResult.className + "(" + eachResult.packageName + ")";
      for (ArtifactInfo eachVersion : eachResult.versions) {
        if (s.length() > 0) s += " ";
        s += eachVersion.groupId + ":" + eachVersion.artifactId + ":" + eachVersion.version;
      }
      actualArtifacts.add(s);
    }
    assertOrderedElementsAreEqual(actualArtifacts, expected);
  }

  private void assertArtifactSearchResults(String pattern, String... expected) {
    List<String> actual = new ArrayList<String>();
    for (MavenArtifactSearchResult eachResult : new MavenArtifactSearcher().search(myProject, pattern, 100)) {
      String s = "";
      for (ArtifactInfo eachVersion : eachResult.versions) {
        if (s.length() > 0) s += " ";
        s += eachVersion.groupId + ":" + eachVersion.artifactId + ":" + eachVersion.version;
      }
      actual.add(s);
    }
    assertOrderedElementsAreEqual(actual, expected);
  }
}
