// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet.filters;

import com.intellij.codeInsight.template.emmet.EmmetAbbreviationTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.template.emmet.EmmetAbbreviationTestFixture.emmetAbbreviationFixture;

@TestApplication
public class TrimZenCodingFilterTest {
  private final TestFixture<EmmetAbbreviationTestFixture> emmet = emmetAbbreviationFixture("html");

  @Test
  public void testTrimFilter1() {
    emmet.get().expandAndCheck("{1. test}|t", "test");
  }

  @Test
  public void testTrimFilter2() {
    emmet.get().expandAndCheck("{ 1 test}|t", "test");
  }

  @Test
  public void testTrimFilter3() {
    emmet.get().expandAndCheck("{ * test}|t", "test");
  }

  @Test
  public void testTrimFilterWithWrapping() {
    emmet.get().emmetWrap("""
                1. list item one
                2. list item two
                3. list item three""", "div*", """
                <div>1. list item one</div>
                <div>2. list item two</div>
                <div>3. list item three</div>""");

    emmet.get().emmetWrap("""
                1. list item one
                2. list item two
                3. list item three""", "div*|t", """
                <div>list item one</div>
                <div>list item two</div>
                <div>list item three</div>""");
  }
}
