/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.indices;

import org.jetbrains.idea.maven.model.MavenArtifactInfo;

import java.util.ArrayList;
import java.util.List;

public class MavenSearcherTest extends MavenIndicesTestCase {
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
    assertTrue(!getClassSearchResults("").isEmpty());

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

    assertClassSearchResults("org.junit.After",
                             "After(org.junit) junit:junit:4.0",
                             "AfterClass(org.junit) junit:junit:4.0");

    assertClassSearchResults("org.After",
                             "After(org.junit) junit:junit:4.0",
                             "AfterClass(org.junit) junit:junit:4.0");

    assertClassSearchResults("junit.After",
                             "After(org.junit) junit:junit:4.0",
                             "AfterClass(org.junit) junit:junit:4.0");

    assertClassSearchResults("or.jun.After",
                             "After(org.junit) junit:junit:4.0",
                             "AfterClass(org.junit) junit:junit:4.0");

    // do not include other packages
    assertClassSearchResults("junit.framework.Test ",
                             "Test(junit.framework) junit:junit:4.0 junit:junit:3.8.2 junit:junit:3.8.1");

    assertClassSearchResults("!@][#$%)(^&*()_"); // shouldn't throw
  }

  public void testArtifactSearch() throws Exception {
    assertArtifactSearchResults("",
                                "asm:asm:3.3.1 asm:asm:3.3",
                                "asm:asm-attrs:2.2.1",
                                "commons-io:commons-io:2.4",
                                "jmock:jmock:1.2.0 jmock:jmock:1.1.0 jmock:jmock:1.0.0",
                                "junit:junit:4.0 junit:junit:3.8.2 junit:junit:3.8.1",
                                "org.ow2.asm:asm:4.1");
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
    assertOrderedElementsAreEqual(getClassSearchResults(pattern), expected);
  }

  private List<String> getClassSearchResults(String pattern) {
    List<String> actualArtifacts = new ArrayList<>();
    for (MavenClassSearchResult eachResult : new MavenClassSearcher().search(myProject, pattern, 100)) {
      String s = eachResult.className + "(" + eachResult.packageName + ")";
      for (MavenArtifactInfo eachVersion : eachResult.versions) {
        if (s.length() > 0) s += " ";
        s += eachVersion.getGroupId() + ":" + eachVersion.getArtifactId()+ ":" + eachVersion.getVersion();
      }
      actualArtifacts.add(s);
    }
    return actualArtifacts;
  }

  private void assertArtifactSearchResults(String pattern, String... expected) {
    List<String> actual = new ArrayList<>();
    for (MavenArtifactSearchResult eachResult : new MavenArtifactSearcher(true).search(myProject, pattern, 100)) {
      String s = "";
      for (MavenArtifactInfo eachVersion : eachResult.versions) {
        if (s.length() > 0) s += " ";
        s += eachVersion.getGroupId() + ":" + eachVersion.getArtifactId()+ ":" + eachVersion.getVersion();
      }
      actual.add(s);
    }
    assertOrderedElementsAreEqual(actual, expected);
  }
}
