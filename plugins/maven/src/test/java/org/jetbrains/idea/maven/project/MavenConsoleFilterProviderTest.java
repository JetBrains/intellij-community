// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project;

import com.intellij.execution.filters.Filter;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class MavenConsoleFilterProviderTest extends CodeInsightFixtureTestCase {

  private Filter @NotNull [] filters;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    filters = new MavenConsoleFilterProvider().getDefaultFilters(myFixture.getProject());
  }

  public void testMavenFilterKtOk() {
    assertSuccess("[ERROR] /home/IdeaProjects/jb/kt-demo/src/main/kotlin/main.kt: (1, 32) Unresolved reference: ff", 8, 61);
  }

  public void testMavenFilterJavaOk() {
    assertSuccess("[ERROR] /home/IdeaProjects/jb/kt-demo/src/main/kotlin/main.kt:[1,32] Unresolved reference: ff", 8, 61);
  }

  public void testMavenFilterJavaOk2() {
    assertSuccess("[ERROR] /home/IdeaProjects/demo/src/main/java/com/example/demo/DemoApplication.java:[9,1]" +
                  " class, interface, or enum expected", 8, 83);
  }

  public void testMavenFilterKtBad() {
    assertError("[ERROR] /home/IdeaProjects/jb/kt-demo/src/main/kotlin/main.kt: [1,32] Unresolved reference: ff");
  }

  public void testMavenFilterKtBad2() {
    assertError("[ERROR] /home/IdeaProjects/jb/kt-demo/src/main/kotlin/main.kt:(1,32) Unresolved reference: ff");
  }

  public void testMavenFilterJavaWinOk() {
    assertSuccess("[ERROR] /C:/Users/Admin/IdeaProjects/test/src/main/java/test/Test.java:[2,1] class, interface, or enum expected", 9, 70);
  }

  public void testMavenFilterJavaWinBad() {
    assertError("[ERROR] /C:/Users/Admin/IdeaProjects/test/src/main/java/test/Test.java: [2,1] class, interface, or enum expected");
  }

  public void testMavenFilterKtWinOk() {
    assertSuccess("[ERROR] /C:/Users/Admin/IdeaProjects/test/src/main/java/test/Test.kt: (2, 1) class, interface, or enum expected", 9, 68);
  }

  public void testMavenFilterKtWinBad() {
    assertError("[ERROR] /C:/Users/Admin/IdeaProjects/test/src/main/java/test/Test.kt: (2,1) class, interface, or enum expected");
  }

  private void assertSuccess(String line, int expectedStart, int expectedEnd) {
    List<Filter.Result> results = applyFilter(line);
    Assert.assertEquals(1, results.size());
    Filter.Result filterResult = results.get(0);
    List<Filter.ResultItem> resultItems = filterResult.getResultItems();
    Assert.assertEquals(1, resultItems.size());
    Assert.assertEquals(expectedStart, resultItems.get(0).getHighlightStartOffset());
    Assert.assertEquals(expectedEnd, resultItems.get(0).getHighlightEndOffset());
  }

  private void assertError(String line) {
    Assert.assertTrue(applyFilter(line).isEmpty());
  }

  private List<Filter.Result> applyFilter(String line) {
    return Arrays.stream(filters)
      .map(f -> f.applyFilter(line, line.length()))
      .filter(r -> r != null)
      .collect(Collectors.toList());
  }
}