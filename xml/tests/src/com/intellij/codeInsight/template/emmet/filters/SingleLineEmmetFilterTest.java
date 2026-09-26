// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet.filters;

public class SingleLineEmmetFilterTest extends EmmetFilterTestCase {
  private final ZenCodingFilter myFilter = new SingleLineEmmetFilter();

  public void testSingleLineFilter() {
    expandAndCheck("div>p|s", "<div><p></p></div>");
  }

  public void testSingleLineFilter2() {
    expandAndCheck("ul>li*4|s", "<ul><li></li><li></li><li></li><li></li></ul>");
  }

  public void testSingleLineFilterWithClimbUpOperation() {
    expandAndCheck(".g>.gg>.ggg^^.gggg|s", "<div class=\"g\"><div class=\"gg\"><div class=\"ggg\"></div></div></div><div class=\"gggg\"></div>");
  }

  @Override
  protected ZenCodingFilter getFilter() {
    return myFilter;
  }

  @Override
  protected String getExtension() {
    return "html";
  }
}
