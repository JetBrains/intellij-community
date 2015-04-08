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
import com.intellij.reference.SoftLazyValue;
import com.intellij.util.IncorrectOperationException;
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
public class XmlPropertiesFileImpl extends XmlPropertiesFile {
  private static final Key<CachedValue<PropertiesFile>> KEY = Key.create("xml properties file");
  private final XmlFile myFile;
  private final SoftLazyValue<Info> myInfo = new SoftLazyValue<Info>() {
    @NotNull
    @Override
    protected Info compute() {
      return new Info();
    }
  };

  private class Info {
    private final MultiMap<String, IProperty> myPropertiesMap = MultiMap.create();
    private List<IProperty> myPropertiesOrder;
    private boolean mySorted;

    public Info() {
      XmlTag rootTag = myFile.getRootTag();
      final List<IProperty> propertiesOrder = new ArrayList<IProperty>();
      if (rootTag != null) {
        XmlTag[] entries = rootTag.findSubTags("entry");
        for (XmlTag entry : entries) {
          XmlProperty property = new XmlProperty(entry, XmlPropertiesFileImpl.this);
          propertiesOrder.add(property);
          myPropertiesMap.putValue(property.getKey(), property);
        }
      }
      mySorted = PropertiesImplUtil.isAlphaSorted(propertiesOrder);
      myPropertiesOrder = mySorted ? propertiesOrder : null;
    }

    public void setSorted(boolean sorted) {
      mySorted = sorted;
      myPropertiesOrder = null;
    }

    public MultiMap<String, IProperty> getPropertiesMap() {
      return myPropertiesMap;
    }

    public List<IProperty> getPropertiesOrder() {
      return myPropertiesOrder;
    }

    public boolean isSorted() {
      return mySorted;
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
    return new ArrayList<IProperty>(myInfo.getValue().getPropertiesMap().values());
  }

  @Override
  public IProperty findPropertyByKey(@NotNull @NonNls String key) {
    Collection<IProperty> properties = myInfo.getValue().getPropertiesMap().get(key);
    return properties.isEmpty() ? null : properties.iterator().next();
  }

  @NotNull
  @Override
  public List<IProperty> findPropertiesByKey(@NotNull @NonNls String key) {
    return new ArrayList<IProperty>(myInfo.getValue().getPropertiesMap().get(key));
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

  @Override
  public PsiElement addProperty(@NotNull IProperty property) throws IncorrectOperationException {
    return null;
  }

  @Override
  public PsiElement addPropertyAfter(@NotNull Property property, @Nullable Property anchor) throws IncorrectOperationException {
    return null;
  }

  @Override
  public IProperty addPropertyAfter(String key, String value, Property anchor) {
    return addPropertyAfterAndCheckAlphaSorting(key, value, anchor, true, true);
  }

  @NotNull
  public IProperty addPropertyAfterAndCheckAlphaSorting(String key, String value, @Nullable IProperty anchor, boolean addToEnd, boolean checkAlphaSorting) {
    final XmlTag anchorTag = anchor == null ? null : (XmlTag)anchor.getPsiElement();
    final XmlTag rootTag = myFile.getRootTag();
    final XmlTag entry = createPropertyTag(key, value);
    final XmlTag addedEntry = (XmlTag) (anchorTag == null ? myFile.getRootTag().addSubTag(entry, !addToEnd) : rootTag.addAfter(entry, anchorTag));
    final XmlProperty property = new XmlProperty(addedEntry, this);
    myInfo.getValue().getPropertiesMap().putValue(key, property);
    if (checkAlphaSorting) {
      checkAlphaSorting(property);
    }
    return property;
  }

  @NotNull
  @Override
  public IProperty addProperty(String key, String value) {
    final XmlTag entry = createPropertyTag(key, value);
    if (myInfo.getValue().isSorted()) {
      final XmlProperty dummyProperty = new XmlProperty(entry, this);
      final int insertIndex = Collections.binarySearch(myInfo.getValue().getPropertiesOrder(), dummyProperty, new Comparator<IProperty>() {
        @Override
        public int compare(IProperty p1, IProperty p2) {
          final String k1 = p1.getKey();
          final String k2 = p2.getKey();
          return k1.compareTo(k2);
        }
      });
      final IProperty insertPosition;
      final IProperty inserted;
      if (insertIndex == -1) {
        inserted = addPropertyAfterAndCheckAlphaSorting(key, value, null, false, false);
        myInfo.getValue().getPropertiesOrder().add(0, inserted);
      }
      else {
        final int position = insertIndex < 0 ? -insertIndex - 2 : insertIndex;
        insertPosition = myInfo.getValue().getPropertiesOrder().get(position);
        inserted = addPropertyAfterAndCheckAlphaSorting(key, value, insertPosition, false, false);
        myInfo.getValue().getPropertiesOrder().add(position + 1, inserted);
      }
      return inserted;
    } else {
      return addPropertyAfterAndCheckAlphaSorting(key, value, null, true, false);
    }
  }

  private XmlTag createPropertyTag(final String key, final String value) {
    XmlTag rootTag = myFile.getRootTag();
    XmlTag entry = rootTag.createChildTag("entry", "", value, false);
    entry.setAttribute("key", key);
    return entry;
  }

  public static PropertiesFile getPropertiesFile(final PsiFile file) {
    CachedValuesManager manager = CachedValuesManager.getManager(file.getProject());
    if (file instanceof XmlFile) {
      return manager.getCachedValue(file, KEY,
                                    new CachedValueProvider<PropertiesFile>() {
                                      @Override
                                      public Result<PropertiesFile> compute() {
                                        PropertiesFile value =
                                          XmlPropertiesIndex.isPropertiesFile((XmlFile)file)
                                          ? new XmlPropertiesFileImpl((XmlFile)file)
                                          : null;
                                        return Result.create(value, file);
                                      }
                                    }, false
      );
    }
    return null;
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
  public boolean isAlphaSorted() {
    return myInfo.getValue().isSorted();
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

  private void checkAlphaSorting(final IProperty property) {
    if (myInfo.getValue().isSorted()) {
      final String key = property.getKey();
      final XmlTag prev = getSibling((XmlTag)property.getPsiElement(), true);
      final String prevKey = prev == null ? null : new XmlProperty(prev, this).getKey();
      if (prevKey != null && key != null && prevKey.compareTo(key) > 0) {
        myInfo.getValue().setSorted(false);
      } else {
        final XmlTag next = getSibling((XmlTag)property.getPsiElement(), false);
        final String nextKey = next == null ? null : new XmlProperty(next, this).getKey();
        if (nextKey != null && key != null && nextKey.compareTo(key) < 0) {
          myInfo.getValue().setSorted(false);
        }
      }
    }
  }

  private static XmlTag getSibling(final XmlTag entry, final boolean prev) {
    XmlTag sibling = (XmlTag)(prev ? entry.getPrevSibling() : entry.getNextSibling());
    while (sibling != null && !"entry".equals(sibling.getName())) {
      sibling = (XmlTag)(prev ? sibling.getPrevSibling() : sibling.getNextSibling());
    }
    return sibling;
  }
}
