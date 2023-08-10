// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet.filters;

import com.intellij.codeInsight.template.emmet.EmmetAbbreviationTestCase;
import com.intellij.codeInsight.template.emmet.tokens.TemplateToken;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;

import java.util.HashMap;

public abstract class EmmetFilterTestCase extends EmmetAbbreviationTestCase {
  protected void doFilterText(String sourceData, String expectedData) {
    configureFromFileText("test." + getExtension(), sourceData);
    HashMap<String, String> attribute2value = new HashMap<>();
    TemplateToken templateToken = new TemplateToken("div", attribute2value);
    templateToken.setTemplateText(sourceData, getFile());
    XmlTag tag = templateToken.getXmlTag();
    if (tag != null) {
      for (XmlAttribute attribute : tag.getAttributes()) {
        attribute2value.put(attribute.getName(), attribute.getValue());
      }
    }
    String filteredData = getFilter().filterText(sourceData, templateToken);
    assertEquals(expectedData, filteredData);
  }

  protected abstract ZenCodingFilter getFilter();
}
