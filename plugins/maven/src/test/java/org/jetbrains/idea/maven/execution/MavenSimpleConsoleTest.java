// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.testFramework.UsefulTestCase;

import java.util.ArrayList;
import java.util.List;

public class MavenSimpleConsoleTest extends UsefulTestCase {


  public void testSmoke() {
    doTest(false, new String[]{
             "first\n",
             "second\n",
             "third\n"
           },
           """
             first
             second
             third
             """
    );
  }

  public void testIncompleteLine() {
    doTest(false, new String[]{
             "fi",
             "rst\n",
             "second\n",
             "third\n"
           },
           """
             first
             second
             third
             """
    );
  }

  public void testIncompleteLineNewLine() {
    doTest(false, new String[]{
             "fi",
             "rst\n",
             "second\n",
             "third",
             "\n"
           },
           """
             first
             second
             third
             """
    );
  }

  public void testFilterSpy() {
    doTest(false, new String[]{
             "fi",
             "rst\n",
             "second\n",
             "[IJ]-spy-output\n",
             "third",
             "\n"
           },
           """
             first
             second
             third
             """
    );
  }

  public void testShortStrings() {
    doTest(false, new String[]{
             "1\n",
             "2\n",
             "three\n",
             "[IJ]-spy-output\n",
             "end\n",
             "\n"
           },
           "1\n2\nthree\nend\n\n"
    );
  }

  public void testFilterSpySplittedTimes() {
    doTest(false, new String[]{
             "fi",
             "rst\n",
             "second\n",
             "[IJ]-spy-output",
             "-and-this-is-still-spy-output",
             "-and-this-is-still-spy-output again\n",
             "and this is is not",
             "\n"
           },
           "first\nsecond\nand this is is not\n"
    );
  }

  public void testFilterSpySeveralTimes() {
    doTest(false, new String[]{
             "fi",
             "rst\n",
             "second\n",
             "[IJ]-spy-output-1\n",
             "[IJ]-spy-output-2\n",
             "third\n",
             "[IJ]-spy-output-3\n",
             "end\n"
           },
           "first\nsecond\nthird\nend\n"
    );
  }

  public void testFilterSpySeveralTimesIdea221227() {
    doTest(false, new String[]{
             "fi",
             "rst\n",
             "second\n",
             "[IJ]",
             "-spy-output-",
             "\n",
             "[IJ]-spy-output-3\n",
             "third\n"
           },
           "first\nsecond\nthird\n"
    );
  }

  public void testDoNotFilterSpy() {
    doTest(true, new String[]{
             "fi",
             "rst\n",
             "second\n",
             "[IJ]-spy-output-1\n",
             "[IJ]-spy-output-2\n",
             "third\n",
             "[IJ]-spy-output-3\n",
             "end\n"
           },
           """
             first
             second
             [IJ]-spy-output-1
             [IJ]-spy-output-2
             third
             [IJ]-spy-output-3
             end
             """
    );
  }

  public void testAnsiColors() {
    List<Pair<String, Key<String>>> expected = List.of(
      new Pair<>("[", Key.create("color1")),
      new Pair<>("INFO", Key.create("color2")),
      new Pair<>("]\n", Key.create("color1"))
    );
    List<Pair<String, Key<String>>> actual = new ArrayList<>();
    MavenSimpleConsoleEventsBuffer buffer =
      new MavenSimpleConsoleEventsBuffer((l, k) -> actual.add(new Pair<>(l, k)), false);
    for (var item : expected) {
      buffer.addText(item.first, item.second);
    }
    assertEquals(expected, actual);
  }

  private static void doTest(boolean showSpyOutput, String[] text, String expected) {
    StringBuilder actual = new StringBuilder();
    MavenSimpleConsoleEventsBuffer buffer =
      new MavenSimpleConsoleEventsBuffer((l, k) -> actual.append(l), showSpyOutput);
    for (String s : text) {
      buffer.addText(s, Key.create("test"));
    }
    assertEquals(expected, actual.toString());
  }
}