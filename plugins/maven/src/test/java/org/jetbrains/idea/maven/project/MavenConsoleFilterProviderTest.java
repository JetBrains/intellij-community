// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project;

import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.OpenFileHyperlinkInfo;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class MavenConsoleFilterProviderTest extends CodeInsightFixtureTestCase {

  private Filter @NotNull [] filters;
  private String javaFilePath;
  private String ktFilePath;
  private String scalaFilePath;
  private String groovyFilePath;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    filters = new MavenConsoleFilterProvider().getDefaultFilters(myFixture.getProject());
    javaFilePath = myFixture.configureByText("main.java",
                                             "public class Test {ff\n" +
                                             "    public static void main(String[] args) {}\n" +
                                             "}")
      .getVirtualFile()
      .getCanonicalPath();

    ktFilePath = myFixture.configureByText("main.kt",
                                           "fun main(args: Array<String>) {ff\n" +
                                           "}")
      .getVirtualFile()
      .getCanonicalPath();

    scalaFilePath = myFixture.configureByText("main.scala",
                                           "object HelloWorld {" +
                                           "  def main(args: Array[String]): Unit = {ff}" +
                                           "}")
      .getVirtualFile()
      .getCanonicalPath();

    groovyFilePath = myFixture.configureByText("main.groovy",
                                              "class HelloWorld {" +
                                              "  String getMessage(boolean bigger) {{}" +
                                              "}")
      .getVirtualFile()
      .getCanonicalPath();
  }

  public void testMavenFilterKtOk() {
    assertSuccess("[ERROR] " + getFilePath(ktFilePath) + ": (1, 32) Unresolved reference: ff", ktFilePath);
  }

  public void testMavenFilterJavaOk() {
    assertSuccess("[ERROR] " + getFilePath(ktFilePath) + ":[1,32] Unresolved reference: ff", ktFilePath);
  }

  public void testMavenFilterJavaOk2() {
    assertSuccess("[ERROR] " + getFilePath(javaFilePath) + ":[9,1] class, interface, or enum expected", javaFilePath);
  }

  public void testMavenFilterKtBad() {
    assertError("[ERROR] " + getFilePath(ktFilePath) + ": [1,32] Unresolved reference: ff");
  }

  public void testMavenFilterKtBad2() {
    assertError("[ERROR] " + getFilePath(ktFilePath) + ":(1,32) Unresolved reference: ff");
  }

  public void testMavenFilterScala() {
    assertSuccess("[ERROR] " + getFilePath(scalaFilePath) + ":1: error: not found: value ff", scalaFilePath);
  }

  public void testMavenFilterGroovy() {
    assertSuccess("[ERROR] " + getFilePath(groovyFilePath)
                  + ": 1: Ambiguous expression could be either a parameterless closure expression or an isolated open code block;",
                  groovyFilePath);
  }

  private static String getFilePath(String path) {
    return (SystemInfo.isWindows) ? "/" + path : path;
  }

  private void assertSuccess(String line, String expectedPath) {
    int expectedStart = line.indexOf(expectedPath);
    List<Filter.Result> results = applyFilter(line);
    Assert.assertEquals(1, results.size());
    Filter.Result filterResult = results.get(0);
    List<Filter.ResultItem> resultItems = filterResult.getResultItems();
    Assert.assertEquals(1, resultItems.size());
    Filter.ResultItem resultItem = resultItems.get(0);
    Assert.assertTrue(resultItem.getHyperlinkInfo() instanceof OpenFileHyperlinkInfo);
    Assert.assertEquals(expectedPath, ((OpenFileHyperlinkInfo)resultItem.getHyperlinkInfo()).getVirtualFile().getCanonicalPath());
    Assert.assertEquals(expectedStart + expectedPath.length(), resultItem.getHighlightEndOffset());
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