package com.intellij.codeInspection.htmlInspections;

/**
 * User: anna
 * Date: 16-Dec-2005
 */
public interface XmlEntitiesInspection {
  int UNKNOWN_TAG = 1;
  int UNKNOWN_ATTRIBUTE = 2;
  int NOT_REQUIRED_ATTRIBUTE = 3;
  
  String getAdditionalEntries();
  void setAdditionalEntries(String additionalEntries);
}
