package com.intellij.codeInspection.htmlInspections;

import org.jetbrains.annotations.NonNls;

public interface XmlEntitiesInspection {
  @NonNls String BOOLEAN_ATTRIBUTE_SHORT_NAME = "HtmlUnknownBooleanAttribute";
  @NonNls String ATTRIBUTE_SHORT_NAME = "HtmlUnknownAttribute";
  @NonNls String TAG_SHORT_NAME = "HtmlUnknownTag";
  @NonNls String REQUIRED_ATTRIBUTES_SHORT_NAME = "RequiredAttributes";

  String getAdditionalEntries();
  void addEntry(String text);
}
