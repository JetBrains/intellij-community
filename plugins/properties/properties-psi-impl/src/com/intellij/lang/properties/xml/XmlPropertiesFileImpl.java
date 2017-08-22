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
import com.intellij.lang.properties.PropertiesImplUtil;
import com.intellij.lang.properties.PropertiesUtil;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
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
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MostlySingularMultiMap;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Dmitry Avdeev
 *         Date: 7/26/11
 */
public class XmlPropertiesFileImpl extends XmlPropertiesFile {
  public static final String ENTRY_TAG_NAME = "entry";

  private static final Key<CachedValue<PropertiesFile>> KEY = Key.create("xml properties file");
  private final XmlFile myFile;

  private List<IProperty> myProperties;
  private MostlySingularMultiMap<String, IProperty> myPropertiesMap;
  private boolean myAlphaSorted;
  private long myFileModificationStamp = -1L;
  private final Object myLock = new Object();

  private void ensurePropertiesLoaded() {
    while (myFileModificationStamp != myFile.getModificationStamp() || myPropertiesMap == null) {
      myFileModificationStamp = myFile.getModificationStamp();
      MostlySingularMultiMap<String, IProperty> propertiesMap = new MostlySingularMultiMap<>();
      XmlTag rootTag = myFile.getRootTag();
      final List<IProperty> propertiesOrder = new ArrayList<>();
      if (rootTag != null) {
        XmlTag[] entries = rootTag.findSubTags(ENTRY_TAG_NAME);
        for (XmlTag entry : entries) {
          XmlProperty property = new XmlProperty(entry, this);
          propertiesOrder.add(property);
          final String key = property.getKey();
          if (key != null) {
            propertiesMap.add(key, property);
          }
        }
      }
      myAlphaSorted = PropertiesImplUtil.isAlphaSorted(propertiesOrder);
      myProperties = propertiesOrder;
      myPropertiesMap = propertiesMap;
    }
  }

  private XmlPropertiesFileImpl(XmlFile file) {
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
    synchronized (myLock) {
      ensurePropertiesLoaded();
      return myProperties;
    }
  }

  @Override
  public IProperty findPropertyByKey(@NotNull @NonNls String key) {
    synchronized (myLock) {
      ensurePropertiesLoaded();
      Iterator<IProperty> properties = myPropertiesMap.get(key).iterator();
      return properties.hasNext() ? properties.next(): null;
    }
  }

  @NotNull
  @Override
  public List<IProperty> findPropertiesByKey(@NotNull @NonNls String key) {
    synchronized (myLock) {
      ensurePropertiesLoaded();
      return ContainerUtil.collect(myPropertiesMap.get(key).iterator());
    }
  }

  @NotNull
  @Override
  public ResourceBundle getResourceBundle() {
    return PropertiesImplUtil.getResourceBundle(this);
  }

  @NotNull
  @Override
  public Locale getLocale() {
    return PropertiesUtil.getLocale(this);
  }

  @NotNull
  @Override
  public PsiElement addProperty(@NotNull IProperty property) throws IncorrectOperationException {
    return addProperty(property.getKey(), property.getValue()).getPsiElement().getNavigationElement();
  }

  @NotNull
  @Override
  public PsiElement addPropertyAfter(@NotNull IProperty property, @Nullable IProperty anchor) throws IncorrectOperationException {
    return addPropertyAfter(property.getKey(), property.getValue(), anchor).getPsiElement().getNavigationElement();
  }

  @Override
  public IProperty addPropertyAfter(String key, String value, IProperty anchor) {
    return addPropertyAfter(key, value, anchor, true);
  }

  @NotNull
  public IProperty addPropertyAfter(String key, String value, @Nullable IProperty anchor, boolean addToEnd) {
    final XmlTag anchorTag = anchor == null ? null : (XmlTag)anchor.getPsiElement().getNavigationElement();
    final XmlTag rootTag = myFile.getRootTag();
    final XmlTag entry = createPropertyTag(key, value);
    final XmlTag addedEntry = (XmlTag) (anchorTag == null ? myFile.getRootTag().addSubTag(entry, !addToEnd) : rootTag.addAfter(entry, anchorTag));
    return new XmlProperty(addedEntry, this);
  }

  @NotNull
  @Override
  public IProperty addProperty(String key, String value) {
    final XmlTag entry = createPropertyTag(key, value);
    synchronized (myLock) {
      ensurePropertiesLoaded();
      if (myAlphaSorted) {
        final XmlProperty dummyProperty = new XmlProperty(entry, this);
        final int insertIndex =
          Collections.binarySearch(myProperties, dummyProperty, (p1, p2) -> {
            final String k1 = p1.getKey();
            final String k2 = p2.getKey();
            return k1.compareTo(k2);
          });
        final IProperty insertPosition;
        final IProperty inserted;
        if (insertIndex == -1) {
          inserted = addPropertyAfter(key, value, null, false);
          myProperties.add(0, inserted);
        }
        else {
          final int position = insertIndex < 0 ? -insertIndex - 2 : insertIndex;
          insertPosition = myProperties.get(position);
          inserted = addPropertyAfter(key, value, insertPosition, false);
          myProperties.add(position + 1, inserted);
        }
        return inserted;
      }
      else {
        return addPropertyAfter(key, value, null, true);
      }
    }
  }

  private XmlTag createPropertyTag(final String key, final String value) {
    XmlTag rootTag = myFile.getRootTag();
    XmlTag entry = rootTag.createChildTag("entry", "", value, false);
    entry.setAttribute("key", key);
    return entry;
  }

  public static PropertiesFile getPropertiesFile(@NotNull PsiFile file) {
    CachedValuesManager manager = CachedValuesManager.getManager(file.getProject());
    if (file instanceof XmlFile) {
      return manager.getCachedValue(file, KEY,
                                    () -> {
                                      PropertiesFile value =
                                        XmlPropertiesIndex.isPropertiesFile((XmlFile)file)
                                        ? new XmlPropertiesFileImpl((XmlFile)file)
                                        : null;
                                      return CachedValueProvider.Result.create(value, file);
                                    }, false
      );
    }
    return null;
  }

  @NotNull
  @Override
  public Map<String, String> getNamesMap() {
    Map<String, String> result = new THashMap<>();
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
  public boolean isAlphaSorted() {
    synchronized (myLock) {
      ensurePropertiesLoaded();
      return myAlphaSorted;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    XmlPropertiesFileImpl that = (XmlPropertiesFileImpl)o;

    if (!myFile.equals(that.myFile)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myFile.hashCode();
  }

  @Override
  public String toString() {
    return "XmlPropertiesFileImpl:" + getName();
  }
}
