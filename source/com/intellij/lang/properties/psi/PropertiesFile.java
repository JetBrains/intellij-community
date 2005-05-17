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
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface PropertiesFile extends PsiFile {
  @NotNull List<Property> getProperties();
  @Nullable Property findPropertyByKey(String key);
  @NotNull List<Property> findPropertiesByKey(String key);

  @NotNull ResourceBundle getResourceBundle();
}