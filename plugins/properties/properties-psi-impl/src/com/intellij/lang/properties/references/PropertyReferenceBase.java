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
package com.intellij.lang.properties.references;

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesBundle;
import com.intellij.lang.properties.PropertiesImplUtil;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.xml.XmlPropertiesFileImpl;
import com.intellij.lang.properties.xml.XmlProperty;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.PomTargetPsiElement;
import com.intellij.pom.references.PomService;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author nik
 */
public abstract class PropertyReferenceBase implements PsiPolyVariantReference, EmptyResolveMessageProvider {
  private static final Logger LOG = Logger.getInstance(PropertyReferenceBase.class);
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

  public PsiElement resolve() {
    ResolveResult[] resolveResults = multiResolve(false);
    return resolveResults.length == 1 ? resolveResults[0].getElement() : null;
  }

  @NotNull
  protected String getKeyText() {
    return myKey;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PropertyReferenceBase other = (PropertyReferenceBase)o;

    return getElement() == other.getElement() && getKeyText().equals(other.getKeyText());
  }

  public int hashCode() {
    return getKeyText().hashCode();
  }

  @NotNull
  public PsiElement getElement() {
    return myElement;
  }

  public TextRange getRangeInElement() {
    return myTextRange;
  }

  @NotNull
  public String getCanonicalText() {
    return myKey;
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    /*PsiElementFactory factory = JavaPsiFacade.getInstance(myElement.getProject()).getElementFactory();

    if (myElement instanceof PsiLiteralExpression) {
      PsiExpression newExpression = factory.createExpressionFromText("\"" + newElementName + "\"", myElement);
      return myElement.replace(newExpression);
    }
    else {*/
      ElementManipulator<PsiElement> manipulator = ElementManipulators.getManipulator(myElement);
      if (manipulator == null) {
        LOG.error("Cannot find manipulator for " + myElement + " of class " + myElement.getClass());
      }
      return manipulator.handleContentChange(myElement, getRangeInElement(), newElementName);
    /*}*/
  }

  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException("not implemented");
  }

  public boolean isReferenceTo(PsiElement element) {
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

  public boolean isSoft() {
    return mySoft;
  }

  @NotNull
  public String getUnresolvedMessagePattern() {
    return PropertiesBundle.message("unresolved.property.key");
  }

  @NotNull
  public ResolveResult[] multiResolve(final boolean incompleteCode) {
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

  protected static ResolveResult[] getResolveResults(List<IProperty> properties) {
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

  @NotNull
  public Object[] getVariants() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

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
}
