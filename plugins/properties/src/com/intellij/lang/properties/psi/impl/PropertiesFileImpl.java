/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.lang.properties.psi.impl;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.lang.ASTFactory;
import com.intellij.lang.ASTNode;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesLanguage;
import com.intellij.lang.properties.PropertiesUtil;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.parsing.PropertiesElementTypes;
import com.intellij.lang.properties.psi.PropertiesElementFactory;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.tree.ChangeUtil;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MostlySingularMultiMap;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class PropertiesFileImpl extends PsiFileBase implements PropertiesFile {
  private static final TokenSet PROPERTIES_LIST_SET = TokenSet.create(PropertiesElementTypes.PROPERTIES_LIST);
  private volatile MostlySingularMultiMap<String,IProperty> myPropertiesMap; //guarded by lock
  private volatile List<IProperty> myProperties;  //guarded by lock
  private final Object lock = new Object();

  public PropertiesFileImpl(FileViewProvider viewProvider) {
    super(viewProvider, PropertiesLanguage.INSTANCE);
  }

  @Override
  @NotNull
  public FileType getFileType() {
    return StdFileTypes.PROPERTIES;
  }

  @NonNls
  public String toString() {
    return "Properties file:" + getName();
  }

  @Override
  @NotNull
  public List<IProperty> getProperties() {
    ensurePropertiesLoaded();
    return myProperties;
  }

  private ASTNode getPropertiesList() {
    return ArrayUtil.getFirstElement(getNode().getChildren(PROPERTIES_LIST_SET));
  }

  private void ensurePropertiesLoaded() {
    if (myPropertiesMap != null) return;

    final ASTNode[] props = getPropertiesList().getChildren(PropertiesElementTypes.PROPERTIES);
    MostlySingularMultiMap<String, IProperty> propertiesMap = new MostlySingularMultiMap<String, IProperty>();
    List<IProperty> properties = new ArrayList<IProperty>(props.length);
    for (final ASTNode prop : props) {
      final Property property = (Property)prop.getPsi();
      String key = property.getUnescapedKey();
      propertiesMap.add(key, property);
      properties.add(property);
    }
    synchronized (lock) {
      if (myPropertiesMap != null) return;
      myProperties = properties;
      myPropertiesMap = propertiesMap;
    }
  }

  @Override
  public IProperty findPropertyByKey(@NotNull String key) {
    ensurePropertiesLoaded();
    synchronized (lock) {
      Iterator<IProperty> iterator = myPropertiesMap.get(key).iterator();
      return iterator.hasNext() ? iterator.next() : null;
    }
  }

  @Override
  @NotNull
  public List<IProperty> findPropertiesByKey(@NotNull String key) {
    ensurePropertiesLoaded();
    synchronized (lock) {
      return ContainerUtil.collect(myPropertiesMap.get(key).iterator());
    }
  }

  @Override
  @NotNull
  public ResourceBundle getResourceBundle() {
    return PropertiesUtil.getResourceBundle(getContainingFile());
  }

  @Override
  @NotNull
  public Locale getLocale() {
    return PropertiesUtil.getLocale(getVirtualFile());
  }

  @Override
  public PsiElement add(@NotNull PsiElement element) throws IncorrectOperationException {
    if (element instanceof Property) {
      throw new IncorrectOperationException("Use addProperty() instead");
    }
    return super.add(element);
  }

  @Override
  @NotNull
  public PsiElement addProperty(@NotNull IProperty property) throws IncorrectOperationException {
    if (haveToAddNewLine()) {
      insertLineBreakBefore(null);
    }
    final TreeElement copy = ChangeUtil.copyToElement(property.getPsiElement());
    getPropertiesList().addChild(copy);
    return copy.getPsi();
  }

  @Override
  @NotNull
  public PsiElement addPropertyAfter(@NotNull final Property property, @Nullable final Property anchor) throws IncorrectOperationException {
    final TreeElement copy = ChangeUtil.copyToElement(property);
    List<IProperty> properties = getProperties();
    ASTNode anchorBefore = anchor == null ? properties.isEmpty() ? null : properties.get(0).getPsiElement().getNode()
                           : anchor.getNode().getTreeNext();
    if (anchorBefore != null) {
      if (anchorBefore.getElementType() == TokenType.WHITE_SPACE) {
        anchorBefore = anchorBefore.getTreeNext();
      }
    }
    if (anchorBefore == null && haveToAddNewLine()) {
      insertLineBreakBefore(null);
    }
    getPropertiesList().addChild(copy, anchorBefore);
    if (anchorBefore != null) {
      insertLineBreakBefore(anchorBefore);
    }
    return copy.getPsi();
  }

  @Override
  public IProperty addProperty(String key, String value) {
    return (IProperty)addProperty(PropertiesElementFactory.createProperty(getProject(), key, value));
  }

  private void insertLineBreakBefore(final ASTNode anchorBefore) {
    getPropertiesList().addChild(ASTFactory.whitespace("\n"), anchorBefore);
  }

  private boolean haveToAddNewLine() {
    ASTNode lastChild = getPropertiesList().getLastChildNode();
    return lastChild != null && !lastChild.getText().endsWith("\n");
  }

  @Override
  @NotNull
  public Map<String, String> getNamesMap() {
    Map<String, String> result = new THashMap<String, String>();
    for (IProperty property : getProperties()) {
      result.put(property.getUnescapedKey(), property.getValue());
    }
    return result;
  }

  @Override
  public void clearCaches() {
    super.clearCaches();

    synchronized (lock) {
      myPropertiesMap = null;
      myProperties = null;
    }
  }
}
