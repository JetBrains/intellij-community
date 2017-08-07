/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.lang.properties.*;
import com.intellij.lang.properties.parsing.PropertiesElementTypes;
import com.intellij.lang.properties.parsing.PropertiesTokenTypes;
import com.intellij.lang.properties.psi.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.tree.ChangeUtil;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PropertiesFileImpl extends PsiFileBase implements PropertiesFile {
  private static final Logger LOG = Logger.getInstance(PropertiesFileImpl.class);
  private static final TokenSet PROPERTIES_LIST_SET = TokenSet.create(PropertiesElementTypes.PROPERTIES_LIST);

  public PropertiesFileImpl(FileViewProvider viewProvider) {
    super(viewProvider, PropertiesLanguage.INSTANCE);
  }

  @Override
  @NotNull
  public FileType getFileType() {
    return PropertiesFileType.INSTANCE;
  }

  @NonNls
  public String toString() {
    return "Properties file:" + getName();
  }

  @Override
  @NotNull
  public List<IProperty> getProperties() {
    final PropertiesList propertiesList = PsiTreeUtil.getStubChildOfType(this, PropertiesList.class);
    if (propertiesList == null) return Collections.emptyList();
    return Collections.unmodifiableList(PsiTreeUtil.getStubChildrenOfTypeAsList(propertiesList, Property.class));
  }

  private ASTNode getPropertiesList() {
    return ArrayUtil.getFirstElement(getNode().getChildren(PROPERTIES_LIST_SET));
  }

  @Nullable
  @Override
  public IProperty findPropertyByKey(@NotNull String key) {
    return propertiesByKey(key).findFirst().orElse(null);
  }

  @Override
  @NotNull
  public List<IProperty> findPropertiesByKey(@NotNull String key) {
    return propertiesByKey(key).collect(Collectors.toList());
  }

  @Override
  @NotNull
  public ResourceBundle getResourceBundle() {
    return PropertiesImplUtil.getResourceBundle(this);
  }

  @Override
  @NotNull
  public Locale getLocale() {
    return PropertiesUtil.getLocale(this);
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
    final IProperty position = findInsertionPosition(property);
    return addPropertyAfter(property, position);
  }

  @Override
  @NotNull
  public PsiElement addPropertyAfter(@NotNull final IProperty property, @Nullable final IProperty anchor) throws IncorrectOperationException {
    final TreeElement copy = ChangeUtil.copyToElement(property.getPsiElement());
    List<IProperty> properties = getProperties();
    ASTNode anchorBefore = anchor == null ? properties.isEmpty() ? null : properties.get(0).getPsiElement().getNode()
                           : anchor.getPsiElement().getNode().getTreeNext();
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

  @NotNull
  @Override
  public IProperty addProperty(String key, String value) {
    return (IProperty)addProperty(PropertiesElementFactory.createProperty(getProject(), key, value, null));
  }

  @NotNull
  @Override
  public IProperty addPropertyAfter(String key, String value, @Nullable IProperty anchor) {
    return (IProperty)addPropertyAfter(PropertiesElementFactory.createProperty(getProject(), key, value, null), anchor);
  }

  private void insertLineBreakBefore(final ASTNode anchorBefore) {
    ASTNode propertiesList = getPropertiesList();
    if (anchorBefore == null && propertiesList.getFirstChildNode() == null) {
      getNode().addChild(ASTFactory.whitespace("\n"), propertiesList);
    } else {
      propertiesList.addChild(ASTFactory.whitespace("\n"), anchorBefore);
    }
  }

  private boolean haveToAddNewLine() {
    ASTNode propertiesList = getPropertiesList();
    ASTNode lastChild = propertiesList.getLastChildNode();
    if (lastChild != null) {
      return !lastChild.getText().endsWith("\n");
    }
    ASTNode prev = propertiesList.getTreePrev();
    return prev == null || !PropertiesTokenTypes.WHITESPACES.contains(prev.getElementType());
  }

  @Override
  @NotNull
  public Map<String, String> getNamesMap() {
    Map<String, String> result = new THashMap<>();
    for (IProperty property : getProperties()) {
      result.put(property.getUnescapedKey(), property.getValue());
    }
    return result;
  }

  @Override
  public boolean isAlphaSorted() {
    return PropertiesImplUtil.isAlphaSorted(getProperties());
  }

  private IProperty findInsertionPosition(@NotNull IProperty property) {
    List<IProperty> properties = getProperties();
    if (properties.isEmpty()) return null;
    if (PropertiesImplUtil.isAlphaSorted(properties)) {
      final int insertIndex = Collections.binarySearch(getProperties(), property, (p1, p2) -> {
        final String k1 = p1.getKey();
        final String k2 = p2.getKey();
        LOG.assertTrue(k1 != null && k2 != null);
        return String.CASE_INSENSITIVE_ORDER.compare(k1, k2);
      });
      return insertIndex == -1 ? null : getProperties().get(insertIndex < 0 ? -insertIndex - 2 : insertIndex);
    }
    return ContainerUtil.getLastItem(properties);
  }

  private Stream<? extends IProperty> propertiesByKey(@NotNull String key) {
    if (shouldReadIndex()) {
      return PropertyKeyIndex.getInstance().get(key, getProject(), GlobalSearchScope.fileScope(this)).stream();
    }
    else {
      // see PropertiesElementFactory.createPropertiesFile(Project, Properties, String)
      return getProperties().stream().filter(p -> key.equals(p.getUnescapedKey()));
    }
  }

  private boolean shouldReadIndex() {
    Project project = getProject();
    if (DumbService.getInstance(project).isDumb()) return false;
    VirtualFile file = getVirtualFile();
    return file != null && ProjectFileIndex.getInstance(project).isInContent(file);
  }
}
