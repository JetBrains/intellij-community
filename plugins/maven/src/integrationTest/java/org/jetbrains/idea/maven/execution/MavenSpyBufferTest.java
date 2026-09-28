// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution;

import com.intellij.openapi.util.Key;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertIterableEquals;

public class MavenSpyBufferTest {

  @Test
  public void testSmoke() {
    doTest(new String[]{
      "first\n",
      "second\n",
      "third\n"
    }, new String[]{
      "first\n",
      "second\n",
      "third\n"
    });
  }

  @Test
  public void testIncompleteLine() {
    doTest(new String[]{
      "fi",
      "rst\n",
      "second\n",
      "third\n"
    }, new String[]{
      "first\n",
      "second\n",
      "third\n"
    });
  }

  @Test
  public void testIncompleteLineNewLine() {
    doTest(new String[]{
      "fi",
      "rst\n",
      "second\n",
      "third",
      "\n"
    }, new String[]{
      "first\n",
      "second\n",
      "third\n"
    });
  }

  @Test
  public void testIJ() {
    doTest(new String[]{
      "[",
      "I",
      "J",
      "]",
      "-",
      "very",
      " long",
      " string\n"
    }, new String[]{
      "[IJ]-very long string\n"
    });
  }

  @Test
  public void testShouldSplitLines() {
    doTest(new String[]{
      "some line\nwith another line\nwith",
      " third line\n"
    }, new String[]{
      "some line\n",
      "with another line\n",
      "with third line\n"
    });
  }

  private static void doTest(String[] text, String[] expected) {
    List<String> actual = new ArrayList<>();
    MavenSpyEventsBuffer spyEventsBuffer =
      new MavenSpyEventsBuffer((l, k) -> actual.add(l));
    for (String s : text) {
      spyEventsBuffer.addText(s, Key.create("test"));
    }
    assertIterableEquals(Arrays.asList(expected), actual);
  }
}