// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet.generators;

import com.google.common.collect.Sets;
import com.intellij.openapi.util.text.StringUtil;
import junit.framework.TestCase;

import java.util.List;

public class LoremGeneratorTest extends TestCase {
  public void testGenerate10Words() {
    String lorem = new LoremGenerator().generate(10, true);
    assertEquals(10, StringUtil.getWordsIn(lorem).size());
  }

  public void testGenerate30Words() {
    String lorem = new LoremGenerator().generate(30, true);
    assertEquals(30, StringUtil.getWordsIn(lorem).size());
  }

  public void testCommonStart() {
    String lorem = new LoremGenerator().generate(10, true).toLowerCase();
    List<String> wordsIn = StringUtil.getWordsIn(lorem);
    Sets.SetView<String> difference = Sets.difference(Sets.newHashSet(LoremGenerator.COMMON_P), Sets.newHashSet(wordsIn));
    assertTrue("Following words were not used in lorem: " + difference.toString(), difference.isEmpty());
  }

  /**
   * Brittle test
   */
  public void testCommonStart2() {
    String lorem = new LoremGenerator().generate(10, false).toLowerCase();
    List<String> wordsIn = StringUtil.getWordsIn(lorem);
    Sets.SetView<String> difference = Sets.difference(Sets.newHashSet(LoremGenerator.COMMON_P), Sets.newHashSet(wordsIn));
    assertFalse(difference.isEmpty());
  }
}
