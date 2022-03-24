/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import org.jetbrains.idea.maven.onlinecompletion.model.MavenDependencyCompletionItem;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;


public class MavenSearcherTest extends MavenIndicesTestCase {
  private static final String[] JUNIT_VERSIONS = {"junit:junit:4.0", "junit:junit:3.8.2", "junit:junit:3.8.1"};
  private static final String[] JMOCK_VERSIONS = {"jmock:jmock:1.2.0", "jmock:jmock:1.1.0", "jmock:jmock:1.0.0"};
  private static final String[] COMMONS_IO_VERSIONS = {"commons-io:commons-io:2.4"};

  MavenIndicesTestFixture myIndicesFixture;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myIndicesFixture = new MavenIndicesTestFixture(myDir.toPath(), myProject);
    myIndicesFixture.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myIndicesFixture.tearDown();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  @Test
  public void testClassSearch() {

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

  @Test
  public void testArtifactSearch() {
    if (ignore()) return;
    assertArtifactSearchResults("");
    assertArtifactSearchResults("j:j",
                                Stream.concat(Arrays.stream(JMOCK_VERSIONS), Arrays.stream(JUNIT_VERSIONS)).toArray(String[]::new));
    assertArtifactSearchResults("junit", JUNIT_VERSIONS);
    assertArtifactSearchResults("junit 3.", JUNIT_VERSIONS);
    assertArtifactSearchResults("uni 3.");
    assertArtifactSearchResults("juni juni 3.");
    assertArtifactSearchResults("junit foo", JUNIT_VERSIONS);
    assertArtifactSearchResults("juni:juni:3.", JUNIT_VERSIONS);
    assertArtifactSearchResults("junit:", JUNIT_VERSIONS);
    assertArtifactSearchResults("junit:junit", JUNIT_VERSIONS);
    assertArtifactSearchResults("junit:junit:3.", JUNIT_VERSIONS);
    assertArtifactSearchResults("junit:junit:4.0", JUNIT_VERSIONS);
  }

  @Test
  public void testArtifactSearchDash() {
    if (ignore()) return;
    assertArtifactSearchResults("commons", COMMONS_IO_VERSIONS);
    assertArtifactSearchResults("commons-", COMMONS_IO_VERSIONS);
    assertArtifactSearchResults("commons-io", COMMONS_IO_VERSIONS);
  }

  private void assertClassSearchResults(String pattern, String... expected) {
    assertOrderedElementsAreEqual(getClassSearchResults(pattern), expected);
  }

  private List<String> getClassSearchResults(String pattern) {
    List<String> actualArtifacts = new ArrayList<>();
    for (MavenClassSearchResult eachResult : new MavenClassSearcher().search(myProject, pattern, 100)) {
      StringBuilder s = new StringBuilder(eachResult.getClassName() + "(" + eachResult.getPackageName() + ")");
      for (MavenDependencyCompletionItem eachVersion : eachResult.getSearchResults().getItems()) {
        if (s.length() > 0) s.append(" ");
        s.append(eachVersion.getGroupId()).append(":").append(eachVersion.getArtifactId()).append(":").append(eachVersion.getVersion());
      }
      actualArtifacts.add(s.toString());
    }
    return actualArtifacts;
  }

  private void assertArtifactSearchResults(String pattern, String... expected) {
    List<String> actual = new ArrayList<>();
    StringBuilder s;
    for (MavenArtifactSearchResult eachResult : new MavenArtifactSearcher().search(myProject, pattern, 100)) {
      for (MavenDependencyCompletionItem eachVersion : eachResult.getSearchResults().getItems()) {
        s = new StringBuilder();
        s.append(eachVersion.getGroupId()).append(":").append(eachVersion.getArtifactId()).append(":").append(eachVersion.getVersion());
        actual.add(s.toString());
      }
    }
    assertUnorderedElementsAreEqual(actual, expected);
  }
}
