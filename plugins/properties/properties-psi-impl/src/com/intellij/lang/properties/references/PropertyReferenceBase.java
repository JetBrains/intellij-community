// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementResolveResult;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiTarget;
import com.intellij.psi.ResolveResult;
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
  private final boolean mySoft;
  private final TextRange myTextRange;

  public PropertyReferenceBase(@NotNull String key, boolean soft, @NotNull PsiElement element) {
    this(key, soft, element, ElementManipulators.getValueTextRange(element));
  }

  public PropertyReferenceBase(@NotNull String key, boolean soft, @NotNull PsiElement element, TextRange range) {
    myKey = key;
    mySoft = soft;
    myElement = element;
    myTextRange = range;
  }

  @Override
  public PsiElement resolve() {
    var resolveResults = multiResolve(false);
    return resolveResults.length == 1 ? resolveResults[0].getElement() : null;
  }

  protected @NotNull String getKeyText() {
    return myKey;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    var other = (PropertyReferenceBase)o;
    return getElement() == other.getElement() && getKeyText().equals(other.getKeyText());
  }

  @Override
  public int hashCode() {
    return getKeyText().hashCode();
  }

  @Override
  public @NotNull PsiElement getElement() {
    return myElement;
  }

  @Override
  public @NotNull TextRange getRangeInElement() {
    return myTextRange;
  }

  @Override
  public @NotNull String getCanonicalText() {
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
    for (var result : multiResolve(false)) {
      var el = result.getElement();
      if (el != null && el.isEquivalentTo(element)) return true;
    }
    return false;
  }

  protected void addKey(Object property, Set<Object> variants) {
    variants.add(property);
  }

  @Override
  public boolean isSoft() {
    return mySoft;
  }

  @Override
  public @InspectionMessage @NotNull String getUnresolvedMessagePattern() {
    return PropertiesBundle.message("unresolved.property.key");
  }

  @Override
  public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
    var key = getKeyText();

    List<IProperty> properties;
    var propertiesFiles = getPropertiesFiles();
    if (propertiesFiles == null) {
      properties = PropertiesImplUtil.findPropertiesByKey(getElement().getProject(), key);
    }
    else {
      properties = new ArrayList<>();
      for (var propertiesFile : propertiesFiles) {
        properties.addAll(propertiesFile.findPropertiesByKey(key));
      }
    }
    if (properties.isEmpty()) return ResolveResult.EMPTY_ARRAY;

    // put default properties file first
    ContainerUtil.quickSort(properties, (o1, o2) -> {
      var name1 = o1.getPropertiesFile().getName();
      var name2 = o2.getPropertiesFile().getName();
      return Comparing.compare(name1, name2);
    });

    var results = new ResolveResult[properties.size()];
    for (var i = 0; i < properties.size(); i++) {
      var property = properties.get(i);
      results[i] = new PsiElementResolveResult(property instanceof PsiElement psi ? psi : PomService.convertToPsi((PsiTarget)property));
    }
    return results;
  }

  protected abstract @Nullable List<PropertiesFile> getPropertiesFiles();

  protected boolean isProperty(PsiElement element) {
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
