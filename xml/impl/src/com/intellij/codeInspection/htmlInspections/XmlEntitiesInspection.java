package com.intellij.codeInspection.htmlInspections;

import org.jetbrains.annotations.NonNls;

/**
 * User: anna
 * Date: 16-Dec-2005
 */
public interface XmlEntitiesInspection {
  int UNKNOWN_TAG = 1;
  int UNKNOWN_ATTRIBUTE = 2;
  int NOT_REQUIRED_ATTRIBUTE = 3;

  @NonNls String ATTRIBUTE_SHORT_NAME = "HtmlUnknownAttribute";
  @NonNls String TAG_SHORT_NAME = "HtmlUnknownTag";
  @NonNls String REQUIRED_ATTRIBUTES_SHORT_NAME = "RequiredAttributes";

  String getAdditionalEntries();
  void addEntry(String text);
}
