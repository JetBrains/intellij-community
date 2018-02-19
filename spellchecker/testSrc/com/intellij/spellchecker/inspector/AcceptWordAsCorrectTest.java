// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.inspector;

import com.intellij.spellchecker.SpellCheckerManager;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;

@SuppressWarnings("SpellCheckingInspection")
public class AcceptWordAsCorrectTest extends LightPlatformCodeInsightFixtureTestCase {

  private void doTest(String word) {
    final SpellCheckerManager manager = SpellCheckerManager.getInstance(myFixture.getProject());
    assertTrue(manager.hasProblem(word));
    manager.acceptWordAsCorrect(word, myFixture.getProject());
    assertFalse(manager.hasProblem(word));
  }
  
  public void testGeneral(){
    doTest("wooord");
  }
  
  public void testCamelCase(){
    doTest("Tyyyyypo");
  }
}
