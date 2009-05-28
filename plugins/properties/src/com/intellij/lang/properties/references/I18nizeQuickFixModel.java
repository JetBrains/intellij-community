/**
 * @author cdr
 */
package com.intellij.lang.properties.references;

import com.intellij.lang.properties.psi.PropertiesFile;

import java.util.Collection;

public interface I18nizeQuickFixModel {
  String getValue();

  String getKey();

  boolean hasValidData();

  Collection<PropertiesFile> getAllPropertiesFiles();
}