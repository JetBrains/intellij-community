// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet.filters;

import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.emmet.EmmetAbbreviationTestCase;
import com.intellij.codeInsight.template.emmet.tokens.TemplateToken;

public abstract class EmmetFilterTestCase extends EmmetAbbreviationTestCase {
  protected void doFilterText(String sourceData, String expectedData) {
    configureFromFileText("test." + getExtension(), sourceData);
    TemplateToken templateToken = new TemplateToken("div");
    templateToken.setTemplateText(sourceData, new CustomTemplateCallback(getEditor(), getFile()));
    String filteredData = getFilter().filterText(sourceData, templateToken);
    assertEquals(expectedData, filteredData);
  }

  protected abstract ZenCodingFilter getFilter();
}
