/**
 * @author Alexey
 */
package com.intellij.lang.properties;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.rename.RenameHandlerRegistry;
import com.intellij.util.SmartList;

import java.util.List;

public class ResourceBundleImpl implements ResourceBundle {
  private final PsiDirectory myBaseDirectory;
  private final String myBaseName;

  public ResourceBundleImpl(PsiDirectory baseDirectory, String baseName) {
    myBaseDirectory = baseDirectory;
    myBaseName = baseName;
  }

  static {
    RenameHandlerRegistry.getInstance().registerHandler(ResourceBundleRenameHandler.INSTANCE);
  }

  public List<PropertiesFile> getPropertiesFiles() {
    PsiFile[] children = myBaseDirectory.getFiles();
    List<PropertiesFile> result = new SmartList<PropertiesFile>();
    for (PsiFile file : children) {
      if (file instanceof PropertiesFile && ((PropertiesFile)file).getResourceBundle().equals(this)) {
        result.add((PropertiesFile)file);
      }
    }
    return result;
  }

  public List<Property> findProperties(String key) {
    List<Property> result = new SmartList<Property>();
    List<PropertiesFile> propertiesFiles = getPropertiesFiles();
    for (PropertiesFile propertiesFile : propertiesFiles) {
      result.addAll(propertiesFile.findPropertiesByKey(key));
    }
    return result;
  }

  public String getBaseName() {
    return myBaseName;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final ResourceBundleImpl resourceBundle = (ResourceBundleImpl)o;

    if (!myBaseDirectory.equals(resourceBundle.myBaseDirectory)) return false;
    if (!myBaseName.equals(resourceBundle.myBaseName)) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = myBaseDirectory.hashCode();
    result = 29 * result + myBaseName.hashCode();
    return result;
  }
}