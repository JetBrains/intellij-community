// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.references;

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesBundle;
import com.intellij.lang.properties.PropertiesImplUtil;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.xml.XmlPropertiesFileImpl;
import com.intellij.lang.properties.xml.XmlProperty;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.PomTargetPsiElement;
import com.intellij.pom.references.PomService;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public abstract class PropertyReferenceBase implements PsiPolyVariantReference, EmptyResolveMessageProvider {

  protected final String myKey;
  protected final PsiElement myElement;
  protected boolean mySoft;
  private final TextRange myTextRange;

  public PropertyReferenceBase(@NotNull String key, final boolean soft, @NotNull PsiElement element) {
    this(key, soft, element, ElementManipulators.getValueTextRange(element));
  }

  public PropertyReferenceBase(@NotNull String key, final boolean soft, @NotNull PsiElement element, TextRange range) {
    myKey = key;
    mySoft = soft;
    myElement = element;
    myTextRange = range;
  }

  @Override
  public PsiElement resolve() {
    ResolveResult[] resolveResults = multiResolve(false);
    return resolveResults.length == 1 ? resolveResults[0].getElement() : null;
  }

  @NotNull
  protected String getKeyText() {
    return myKey;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PropertyReferenceBase other = (PropertyReferenceBase)o;

    return getElement() == other.getElement() && getKeyText().equals(other.getKeyText());
  }

  @Override
  public int hashCode() {
    return getKeyText().hashCode();
  }

  @Override
  @NotNull
  public PsiElement getElement() {
    return myElement;
  }

  @Override
  @NotNull
  public TextRange getRangeInElement() {
    return myTextRange;
  }

  @Override
  @NotNull
  public String getCanonicalText() {
    return myKey;
  }

  @Override
  public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
    return ElementManipulators.handleContentChange(myElement, getRangeInElement(), newElementName);
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException("not implemented");
  }

  @Override
  public boolean isReferenceTo(@NotNull PsiElement element) {
    if (!isProperty(element)) return false;
    for (ResolveResult result : multiResolve(false)) {
      final PsiElement el = result.getElement();
      if (el != null && el.isEquivalentTo(element)) return true;
    }
    return false;
  }

  protected void addKey(Object property, Set<Object> variants) {
    variants.add(property);
  }

  protected void setSoft(final boolean soft) {
    mySoft = soft;
  }

  @Override
  public boolean isSoft() {
    return mySoft;
  }

  @Override
  @InspectionMessage
  @NotNull
  public String getUnresolvedMessagePattern() {
    return PropertiesBundle.message("unresolved.property.key");
  }

  @Override
  public ResolveResult @NotNull [] multiResolve(final boolean incompleteCode) {
    final String key = getKeyText();

    List<IProperty> properties;
    final List<PropertiesFile> propertiesFiles = getPropertiesFiles();
    if (propertiesFiles == null) {
      properties = PropertiesImplUtil.findPropertiesByKey(getElement().getProject(), key);
    }
    else {
      properties = new ArrayList<>();
      for (PropertiesFile propertiesFile : propertiesFiles) {
        properties.addAll(propertiesFile.findPropertiesByKey(key));
      }
    }
    // put default properties file first
    ContainerUtil.quickSort(properties, (o1, o2) -> {
      String name1 = o1.getPropertiesFile().getName();
      String name2 = o2.getPropertiesFile().getName();
      return Comparing.compare(name1, name2);
    });
    return getResolveResults(properties);
  }

  private static ResolveResult @NotNull [] getResolveResults(List<? extends IProperty> properties) {
    if (properties.isEmpty()) return ResolveResult.EMPTY_ARRAY;

    final ResolveResult[] results = new ResolveResult[properties.size()];
    for (int i = 0; i < properties.size(); i++) {
      IProperty property = properties.get(i);
      results[i] = new PsiElementResolveResult(property instanceof PsiElement ? (PsiElement)property : PomService.convertToPsi(
                        (PsiTarget)property));
    }
    return results;
  }

  @Nullable
  protected abstract List<PropertiesFile> getPropertiesFiles();

  private static boolean isProperty(PsiElement element) {
    if (element instanceof IProperty) {
      return true;
    }
    if (element instanceof PomTargetPsiElement) {
      return ((PomTargetPsiElement)element).getTarget() instanceof XmlProperty;
    }
    if (element instanceof XmlTag && ((XmlTag)element).getName().equals(XmlPropertiesFileImpl.ENTRY_TAG_NAME)) {
      return PropertiesImplUtil.isPropertiesFile(element.getContainingFile());
    }
    return false;
  }

  public static boolean isPropertyPsi(@NotNull PsiElement target) {
    return target instanceof IProperty ||
           target instanceof PomTargetPsiElement && ((PomTargetPsiElement)target).getTarget() instanceof IProperty;
  }
}
