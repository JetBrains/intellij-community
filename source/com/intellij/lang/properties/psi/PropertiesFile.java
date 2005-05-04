/*
 * Created by IntelliJ IDEA.
 * User: Alexey
 * Date: 11.04.2005
 * Time: 1:26:45
 */
package com.intellij.lang.properties.psi;

import com.intellij.psi.PsiFile;

import java.util.List;

public interface PropertiesFile extends PsiFile {
  List<Property> getProperties();
  Property findPropertyByKey(String key);
  List<Property> findPropertiesByKey(String key);
}