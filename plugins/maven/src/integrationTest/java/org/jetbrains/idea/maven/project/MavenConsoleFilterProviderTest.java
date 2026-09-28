// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project;

import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.OpenFileHyperlinkInfo;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class MavenConsoleFilterProviderTest {
  private static final TestFixture<Path> tempDir = tempPathFixture();
  private static final TestFixture<Project> project = projectFixture(tempDir, OpenProjectTask.build(), true);

  private Filter @NotNull [] filters;
  private String javaFilePath;
  private String ktFilePath;
  private String scalaFilePath;
  private String groovyFilePath;

  @BeforeEach
  public void setUp() throws IOException {
    filters = new MavenConsoleFilterProvider().getDefaultFilters(project.get());
    javaFilePath = createFile("main.java",
                              """
                                public class Test {ff
                                    public static void main(String[] args) {}
                                }""");

    ktFilePath = createFile("main.kt",
                            "fun main(args: Array<String>) {ff\n" +
                            "}");

    scalaFilePath = createFile("main.scala",
                               "object HelloWorld {" +
                               "  def main(args: Array[String]): Unit = {ff}" +
                               "}");

    groovyFilePath = createFile("main.groovy",
                                "class HelloWorld {" +
                                "  String getMessage(boolean bigger) {{}" +
                                "}");
  }

  @Test
  public void testMavenFilterKtOk() {
    assertSuccess("[ERROR] " + getFilePath(ktFilePath) + ": (1, 32) Unresolved reference: ff", ktFilePath);
  }

  @Test
  public void testMavenFilterJavaOk() {
    assertSuccess("[ERROR] " + getFilePath(ktFilePath) + ":[1,32] Unresolved reference: ff", ktFilePath);
  }

  @Test
  public void testMavenFilterJavaOk2() {
    assertSuccess("[ERROR] " + getFilePath(javaFilePath) + ":[9,1] class, interface, or enum expected", javaFilePath);
  }

  @Test
  public void testMavenFilterKtBad() {
    assertError("[ERROR] " + getFilePath(ktFilePath) + ": [1,32] Unresolved reference: ff");
  }

  @Test
  public void testMavenFilterKtBad2() {
    assertError("[ERROR] " + getFilePath(ktFilePath) + ":(1,32) Unresolved reference: ff");
  }

  @Test
  public void testMavenFilterScala() {
    assertSuccess("[ERROR] " + getFilePath(scalaFilePath) + ":1: error: not found: value ff", scalaFilePath);
  }

  @Test
  public void testMavenFilterGroovy() {
    assertSuccess("[ERROR] " + getFilePath(groovyFilePath)
                  + ": 1: Ambiguous expression could be either a parameterless closure expression or an isolated open code block;",
                  groovyFilePath);
  }

  private String createFile(String name, String text) throws IOException {
    Path file = tempDir.get().resolve(name);
    Files.writeString(file, text);
    VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file);
    assertNotNull(virtualFile, "File not found in VFS: " + file);
    return virtualFile.getCanonicalPath();
  }

  private static String getFilePath(String path) {
    return (SystemInfo.isWindows) ? "/" + path : path;
  }

  private void assertSuccess(String line, String expectedPath) {
    int expectedStart = line.indexOf(expectedPath);
    List<Filter.Result> results = applyFilter(line);
    assertEquals(1, results.size());
    Filter.Result filterResult = results.get(0);
    List<Filter.ResultItem> resultItems = filterResult.getResultItems();
    assertEquals(1, resultItems.size());
    Filter.ResultItem resultItem = resultItems.get(0);
    assertTrue(resultItem.getHyperlinkInfo() instanceof OpenFileHyperlinkInfo);
    assertEquals(expectedPath, ((OpenFileHyperlinkInfo)resultItem.getHyperlinkInfo()).getVirtualFile().getCanonicalPath());
    assertEquals(expectedStart + expectedPath.length(), resultItem.getHighlightEndOffset());
  }

  private void assertError(String line) {
    assertTrue(applyFilter(line).isEmpty());
  }

  private List<Filter.Result> applyFilter(String line) {
    return Arrays.stream(filters)
      .map(f -> f.applyFilter(line, line.length()))
      .filter(r -> r != null)
      .collect(Collectors.toList());
  }
}
