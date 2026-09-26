// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.psi.impl;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.lang.ASTFactory;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.lang.properties.PropertiesLanguage;
import com.intellij.lang.properties.PropertiesUtil;
import com.intellij.lang.properties.PropertiesUtilService;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.parsing.PropertiesElementTypes;
import com.intellij.lang.properties.parsing.PropertiesParserDefinition;
import com.intellij.lang.properties.parsing.PropertiesTokenTypes;
import com.intellij.lang.properties.psi.PropertiesElementFactory;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.PropertiesList;
import com.intellij.lang.properties.psi.PropertiesListStub;
import com.intellij.lang.properties.psi.Property;
import com.intellij.lang.properties.psi.PropertyKeyValueFormat;
import com.intellij.openapi.application.ApplicationManager;
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
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
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

  public PropertiesFileImpl(FileViewProvider viewProvider, Language language) {
    super(viewProvider, language);
  }

  @Override
  public IFileElementType getFileElementType() {
    return PropertiesParserDefinition.FILE_ELEMENT_TYPE;
  }

  @Override
  public @NotNull FileType getFileType() {
    return PropertiesFileType.INSTANCE;
  }

  @Override
  public @NonNls String toString() {
    return "Properties file:" + getName();
  }

  @Override
  public @NotNull List<IProperty> getProperties() {
    PropertiesList propertiesList = withGreenStubOrAst(
      stub -> {
        PropertiesListStub propertiesListStub =
          ObjectUtils.tryCast(stub.findChildStubByElementType(PropertiesElementTypes.PROPERTIES_LIST), PropertiesListStub.class);
        return propertiesListStub == null ? null : propertiesListStub.getPsi();
      },
      ast -> PsiTreeUtil.findChildOfType(this, PropertiesList.class)
    );
    //noinspection RedundantUnmodifiable
    return Collections.unmodifiableList(PsiTreeUtil.getStubChildrenOfTypeAsList(propertiesList, Property.class));
  }

  private ASTNode getPropertiesList() {
    return ArrayUtil.getFirstElement(getNode().getChildren(PROPERTIES_LIST_SET));
  }

  @Override
  public @Nullable IProperty findPropertyByKey(@NotNull String key) {
    return propertiesByKey(key).findFirst().orElse(null);
  }

  @Override
  public @NotNull List<IProperty> findPropertiesByKey(@NotNull String key) {
    return propertiesByKey(key).collect(Collectors.toList());
  }

  @Override
  public @NotNull ResourceBundle getResourceBundle() {
    return ApplicationManager.getApplication().getService(PropertiesUtilService.class).getResourceBundle(this);
  }

  @Override
  public @NotNull Locale getLocale() {
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
  public @NotNull PsiElement addProperty(@NotNull IProperty property) throws IncorrectOperationException {
    final IProperty position = findInsertionPosition(property);
    return addPropertyAfter(property, position);
  }

  @Override
  public @NotNull PsiElement addPropertyAfter(final @NotNull IProperty property, final @Nullable IProperty anchor) throws IncorrectOperationException {
    final TreeElement copy = ChangeUtil.copyToElement(property.getPsiElement());
    List<IProperty> properties = getProperties();
    ASTNode anchorBefore = anchor == null ? properties.isEmpty() ? null : properties.get(0).getPsiElement().getNode()
                           : anchor.getPsiElement().getNode().getTreeNext();
    if (anchorBefore != null && anchorBefore.getElementType() == TokenType.WHITE_SPACE) {
      anchorBefore = anchorBefore.getTreeNext();
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
  public @NotNull IProperty addProperty(@NotNull String key, @NotNull String value, @NotNull PropertyKeyValueFormat format) {
    return (IProperty)addProperty(PropertiesElementFactory.createProperty(getProject(), key, value, null, format));
  }

  @Override
  public @NotNull IProperty addPropertyAfter(@NotNull String key, @NotNull String value, @Nullable IProperty anchor) {
    return (IProperty)addPropertyAfter(PropertiesElementFactory.createProperty(getProject(), key, value, null), anchor);
  }

  private void insertLineBreakBefore(ASTNode anchorBefore) {
    ASTNode propertiesList = getPropertiesList();
    if (anchorBefore == null && propertiesList.getFirstChildNode() == null) {
      getNode().addChild(ASTFactory.whitespace("\n"), propertiesList);
    }
    else {
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
  public @NotNull Map<String, String> getNamesMap() {
    Map<String, String> result = new HashMap<>();
    for (IProperty property : getProperties()) {
      result.put(property.getUnescapedKey(), property.getValue());
    }
    return result;
  }

  @Override
  public boolean isAlphaSorted() {
    return PropertiesUtil.isAlphaSorted(getProperties());
  }

  private IProperty findInsertionPosition(@NotNull IProperty property) {
    List<IProperty> properties = getProperties();
    if (properties.isEmpty()) return null;
    if (isAlphaSorted()) {
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

  private @NotNull Stream<? extends IProperty> propertiesByKey(@NotNull String key) {
    PropertiesUtilService propertiesUtilService = ApplicationManager.getApplication().getService(PropertiesUtilService.class);
    if (shouldReadIndex() && propertiesUtilService.shouldReadIndex()) {
      return propertiesUtilService.getProperties(key, getProject(), GlobalSearchScope.fileScope(this));
    }
    // see PropertiesElementFactory.createPropertiesFile(Project, Properties, String)
    return getProperties().stream().filter(p -> key.equals(p.getUnescapedKey()));
  }

  private boolean shouldReadIndex() {
    Project project = getProject();
    if (DumbService.isDumb(project)) return false;
    VirtualFile file = getVirtualFile();
    return file != null && ProjectFileIndex.getInstance(project).isInContent(file) && !InjectedLanguageManager.getInstance(project).isInjectedFragment(getContainingFile());
  }
}
