/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lang.properties.xml;

import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesUtil;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.reference.SoftLazyValue;
import com.intellij.util.containers.MultiMap;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Dmitry Avdeev
 *         Date: 7/26/11
 */
public class XmlPropertiesFile implements PropertiesFile {

  private static final Key<CachedValue<PropertiesFile>> KEY = Key.create("xml properties file");
  private final XmlFile myFile;
  private final SoftLazyValue<MultiMap<String, IProperty>> myPropertiesMap = new SoftLazyValue<MultiMap<String, IProperty>>() {
    @NotNull
    @Override
    protected MultiMap<String, IProperty> compute() {
      XmlTag rootTag = myFile.getRootTag();
      if (rootTag == null) {
        return MultiMap.emptyInstance();
      }

      XmlTag[] entries = rootTag.findSubTags("entry");
      MultiMap<String, IProperty> map = new MultiMap<String, IProperty>();

      for (XmlTag entry : entries) {
        XmlProperty property = new XmlProperty(entry, XmlPropertiesFile.this);
        map.putValue(property.getKey(), property);
      }
      return map;
    }
  };

  @Nullable
  public static PropertiesFile getPropertiesFile(final PsiFile file) {
    return file instanceof XmlFile ? getPropertiesFile((XmlFile)file) : null;
  }

  public static PropertiesFile getPropertiesFile(final XmlFile file) {
    CachedValuesManager manager = CachedValuesManager.getManager(file.getProject());
    return manager.getCachedValue(file, KEY,
                                  new CachedValueProvider<PropertiesFile>() {
                                    @Override
                                    public Result<PropertiesFile> compute() {
                                      PropertiesFile value = !XmlPropertiesIndex.isAccepted(file.getText()) ? null : new XmlPropertiesFile(file);
                                      return Result.create(value, file);
                                    }
                                  }, false);
  }

  private XmlPropertiesFile(XmlFile file) {
    myFile = file;
  }

  @NotNull
  @Override
  public PsiFile getContainingFile() {
    return myFile;
  }

  @NotNull
  @Override
  public List<IProperty> getProperties() {
    return new ArrayList<IProperty>(myPropertiesMap.getValue().values());
  }

  @Override
  public IProperty findPropertyByKey(@NotNull @NonNls String key) {
    Collection<IProperty> properties = myPropertiesMap.getValue().get(key);
    return properties.isEmpty() ? null : properties.iterator().next();
  }

  @NotNull
  @Override
  public List<IProperty> findPropertiesByKey(@NotNull @NonNls String key) {
    return new ArrayList<IProperty>(myPropertiesMap.getValue().get(key));
  }

  @NotNull
  @Override
  public ResourceBundle getResourceBundle() {
    return PropertiesUtil.getResourceBundle(getContainingFile());
  }

  @NotNull
  @Override
  public Locale getLocale() {
    return PropertiesUtil.getLocale(getVirtualFile());
  }

  @NotNull
  @Override
  public PsiElement addProperty(@NotNull IProperty property) throws IncorrectOperationException {
    return null;
  }

  @NotNull
  @Override
  public PsiElement addPropertyAfter(@NotNull Property property, @Nullable Property anchor) throws IncorrectOperationException {
    return null;
  }

  @Override
  public IProperty addProperty(String key, String value) {
    XmlTag rootTag = myFile.getRootTag();
    XmlTag entry = rootTag.createChildTag("entry", "", value, false);
    entry.setAttribute("key", key);
    return new XmlProperty(entry, this);
  }

  @NotNull
  @Override
  public Map<String, String> getNamesMap() {
    Map<String, String> result = new THashMap<String, String>();
    for (IProperty property : getProperties()) {
      result.put(property.getUnescapedKey(), property.getValue());
    }
    return result;
  }

  @Override
  public String getName() {
    return getContainingFile().getName();
  }

  @Override
  public VirtualFile getVirtualFile() {
    return getContainingFile().getVirtualFile();
  }

  @Override
  public PsiDirectory getParent() {
    return getContainingFile().getParent();
  }

  @Override
  public Project getProject() {
    return getContainingFile().getProject();
  }

  @Override
  public String getText() {
    return getContainingFile().getText();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    XmlPropertiesFile that = (XmlPropertiesFile)o;

    if (!myFile.equals(that.myFile)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myFile.hashCode();
  }
}
