/*
 * Created by IntelliJ IDEA.
 * User: Alexey
 * Date: 11.04.2005
 * Time: 1:26:45
 */
package com.intellij.lang.properties.psi;

import com.intellij.lang.properties.ResourceBundle;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface PropertiesFile extends PsiFile {
  List<Property> getProperties();
  Property findPropertyByKey(String key);
  List<Property> findPropertiesByKey(String key);

  @NotNull ResourceBundle getResourceBundle();
}