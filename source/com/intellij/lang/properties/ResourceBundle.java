/*
 * Created by IntelliJ IDEA.
 * User: Alexey
 * Date: 08.05.2005
 * Time: 0:17:52
 */
package com.intellij.lang.properties;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;

import java.util.List;

public interface ResourceBundle {
  List<PropertiesFile> getPropertiesFiles();

  List<Property> findProperties(String key);

  String getBaseName();
}