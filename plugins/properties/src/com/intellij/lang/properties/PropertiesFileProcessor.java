package com.intellij.lang.properties;

import com.intellij.lang.properties.psi.PropertiesFile;

public interface PropertiesFileProcessor {
  boolean process(String baseName, PropertiesFile propertiesFile);
}
