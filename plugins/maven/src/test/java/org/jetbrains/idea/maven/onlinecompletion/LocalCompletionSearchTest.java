// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.onlinecompletion;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.idea.maven.MavenTestCase;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenDependencyCompletionItem;
import org.jetbrains.idea.maven.onlinecompletion.model.SearchParameters;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class LocalCompletionSearchTest extends MavenTestCase {
  private File myLocalRepo;

  @Before
  @Override
  public void setUp() throws Exception {
    super.setUp();
    myLocalRepo = new File(myDir, ".m2/repository");
    createFile("org/apache/commons/commons-collections4/4.3/commons-collections4-4.3.pom");
    createFile("org/apache/commons/commons-collections4/4.2/commons-collections4-4.2.pom");
    createFile("org/apache/commons/commons-lang3/3.1/commons-lang3-3.1.pom");
    createFile("xalan/xalan/2.6.0/xalan-2.6.0.pom");
    createFile("xalan/serializer/2.7.2/serializer-2.7.2.pom");
    FileUtil.ensureExists(myLocalRepo);
  }

  @Test
  public void testFindAllGroups() throws IOException {
    LocalCompletionSearch search = new LocalCompletionSearch(myLocalRepo);

    List<MavenDependencyCompletionItem> items = search.findGroupCandidates(null, SearchParameters.DEFAULT);
    List<String> map = ContainerUtil.map(items, i -> i.getGroupId());
    assertUnorderedElementsAreEqual(map, "org.apache.commons",
                                    "xalan");
  }

  @Test
  public void testArtifacts() throws IOException {
    LocalCompletionSearch search = new LocalCompletionSearch(myLocalRepo);

    List<MavenDependencyCompletionItem> items =
      search.findArtifactCandidates(new MavenDependencyCompletionItem("org.apache.commons"), SearchParameters.DEFAULT);
    List<String> map = ContainerUtil.map(items, i -> i.getArtifactId());
    assertUnorderedElementsAreEqual(map, "commons-collections4",
                                    "commons-lang3");
  }

  @Test
  public void testVersions() throws IOException {
    LocalCompletionSearch search = new LocalCompletionSearch(myLocalRepo);

    List<MavenDependencyCompletionItem> items =
      search.findAllVersions(
        new MavenDependencyCompletionItem("org.apache.commons:commons-collections4"), SearchParameters.DEFAULT);
    List<String> map = ContainerUtil.map(items, i -> i.getVersion());
    assertUnorderedElementsAreEqual(map, "4.3",
                                    "4.2");
  }

  private void createFile(String path) throws IOException {
    File file = new File(myLocalRepo, path);
    FileUtil.ensureCanCreateFile(file);
    file.createNewFile();
  }
}