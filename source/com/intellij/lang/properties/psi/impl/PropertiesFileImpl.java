package com.intellij.lang.properties.psi.impl;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.lang.ASTNode;
import com.intellij.lang.properties.PropertiesElementTypes;
import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.ResourceBundleImpl;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 28, 2005
 * Time: 12:25:04 AM
 * To change this template use File | Settings | File Templates.
 */
public class PropertiesFileImpl extends PsiFileBase implements PropertiesFile {
  private Map<String,List<Property>> myPropertiesMap;
  private List<Property> myProperties;

  public PropertiesFileImpl(Project project, VirtualFile file) {
    super(project, file, PropertiesFileType.FILE_TYPE.getLanguage());
  }

  public PropertiesFileImpl(Project project, String name, CharSequence text) {
    super(project, name, text, PropertiesFileType.FILE_TYPE.getLanguage());
  }

  public FileType getFileType() {
    return PropertiesFileType.FILE_TYPE;
  }

  public String toString() {
    return "Property file:" + getName();
  }

  public List<Property> getProperties() {
    ensurePropertiesLoaded();
    return myProperties;
  }

  private void ensurePropertiesLoaded() {
    if (myPropertiesMap != null) {
      return;
    }
    final ASTNode[] props = getNode().findChildrenByFilter(PropertiesElementTypes.PROPERTIES);
    myPropertiesMap = new LinkedHashMap<String, List<Property>>();
    myProperties = new ArrayList<Property>(props.length);
    for (final ASTNode prop : props) {
      final Property property = (Property) prop.getPsi();
      String key = property.getKey();
      List<Property> list = myPropertiesMap.get(key);
      if (list == null) {
        list = new SmartList<Property>();
        myPropertiesMap.put(key, list);
      }
      list.add(property);
      myProperties.add(property);
    }
  }

  public Property findPropertyByKey(String key) {
    ensurePropertiesLoaded();
    List<Property> list = myPropertiesMap.get(key);
    return list == null ? null : list.get(0);
  }

  public List<Property> findPropertiesByKey(String key) {
    ensurePropertiesLoaded();
    List<Property> list = myPropertiesMap.get(key);
    return list == null ? Collections.EMPTY_LIST : list;
  }

  @NotNull public ResourceBundle getResourceBundle() {
    String baseName = getBaseName();
    return new ResourceBundleImpl(getContainingFile().getContainingDirectory(), baseName);
  }

  private String getBaseName() {
    String name = getVirtualFile().getNameWithoutExtension();
    if (name.length() > 3 && name.charAt(name.length()-3) == '_') {
      name = name.substring(0, name.length() - 3);
    }
    if (name.length() > 3 && name.charAt(name.length()-3) == '_') {
      name = name.substring(0, name.length() - 3);
    }
    return name;
  }

  public void subtreeChanged() {
    super.subtreeChanged();
    myPropertiesMap = null;
    myProperties = null;
  }
}