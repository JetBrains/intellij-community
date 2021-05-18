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

  private MavenConsoleFilterProvider filterProvider;
  private Filter @NotNull [] filters;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    filters = new MavenConsoleFilterProvider().getDefaultFilters(myFixture.getProject());
  }

  public void testMavenFilterKtOk() {
    String line = "[ERROR] /home/IdeaProjects/jb/kt-demo/src/main/kotlin/main.kt: (1, 32) Unresolved reference: ff";
    Assert.assertFalse(applyFilter(line).isEmpty());
  }

  public void testMavenFilterJavaOk() {
    String line = "[ERROR] /home/IdeaProjects/jb/kt-demo/src/main/kotlin/main.kt:[1,32] Unresolved reference: ff";
    Assert.assertFalse(applyFilter(line).isEmpty());
  }

  public void testMavenFilterJavaOk2() {
    String line = "[ERROR] /home/IdeaProjects/demo/src/main/java/com/example/demo/DemoApplication.java:[9,1]" +
                  " class, interface, or enum expected";
    Assert.assertFalse(applyFilter(line).isEmpty());
  }

  public void testMavenFilterKtBad() {
    String line = "[ERROR] /home/IdeaProjects/jb/kt-demo/src/main/kotlin/main.kt: [1,32] Unresolved reference: ff";
    Assert.assertTrue(applyFilter(line).isEmpty());
  }

  public void testMavenFilterKtBad2() {
    String line = "[ERROR] /home/IdeaProjects/jb/kt-demo/src/main/kotlin/main.kt:(1,32) Unresolved reference: ff";
    Assert.assertTrue(applyFilter(line).isEmpty());
  }

  private List<Filter.Result> applyFilter(String line) {
    return Arrays.stream(filters)
      .map(f -> f.applyFilter(line, line.length()))
      .filter(r -> r != null)
      .collect(Collectors.toList());
  }
}