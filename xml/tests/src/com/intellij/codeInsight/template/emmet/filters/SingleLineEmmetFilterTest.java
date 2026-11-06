// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet.filters;

import com.intellij.codeInsight.template.emmet.EmmetAbbreviationTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.template.emmet.EmmetAbbreviationTestFixture.emmetAbbreviationFixture;

@TestApplication
public class SingleLineEmmetFilterTest {
  private final TestFixture<EmmetAbbreviationTestFixture> emmet = emmetAbbreviationFixture("html");

  @Test
  public void testSingleLineFilter() {
    emmet.get().expandAndCheck("div>p|s", "<div><p></p></div>");
  }

  @Test
  public void testSingleLineFilter2() {
    emmet.get().expandAndCheck("ul>li*4|s", "<ul><li></li><li></li><li></li><li></li></ul>");
  }

  @Test
  public void testSingleLineFilterWithClimbUpOperation() {
    emmet.get().expandAndCheck(".g>.gg>.ggg^^.gggg|s", "<div class=\"g\"><div class=\"gg\"><div class=\"ggg\"></div></div></div><div class=\"gggg\"></div>");
  }
}
