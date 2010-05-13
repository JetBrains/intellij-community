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
package com.intellij.lang.properties;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author yole
 */
public class ResourceBundleReference extends PsiReferenceBase<PsiElement> implements PsiPolyVariantReference, BundleNameEvaluator {
  private String myBundleName;

  public ResourceBundleReference(final PsiElement element) {
    this(element, false);
  }

  public ResourceBundleReference(final PsiElement element, boolean soft) {
    super(element, soft);
    myBundleName = getValue();
  }

  @Nullable public PsiElement resolve() {
    ResolveResult[] resolveResults = multiResolve(false);
    return resolveResults.length == 1 ? resolveResults[0].getElement() : null;
  }

  @NotNull public ResolveResult[] multiResolve(final boolean incompleteCode) {
    PropertiesReferenceManager referenceManager = PropertiesReferenceManager.getInstance(myElement.getProject());
    List<PropertiesFile> propertiesFiles = referenceManager.findPropertiesFiles(myElement.getResolveScope(), myBundleName, this);
    return PsiElementResolveResult.createResults(propertiesFiles);
  }

  @NotNull
  public String getCanonicalText() {
    return myBundleName;
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    if (newElementName.endsWith(PropertiesFileType.DOT_DEFAULT_EXTENSION)) {
      newElementName = newElementName.substring(0, newElementName.lastIndexOf(PropertiesFileType.DOT_DEFAULT_EXTENSION));
    }

    final int index = myBundleName.lastIndexOf('.');
    if (index != -1) {
      newElementName = myBundleName.substring(0, index) + "." + newElementName;
    }

    return super.handleElementRename(newElementName);
  }

  public PsiElement bindToElement(@NotNull final PsiElement element) throws IncorrectOperationException {
    if (!(element instanceof PropertiesFile)) {
      throw new IncorrectOperationException();
    }
    final String name = PropertiesUtil.getFullName((PropertiesFile)element);
    return super.handleElementRename(name);
  }


  public boolean isReferenceTo(PsiElement element) {
    if (element instanceof PropertiesFile) {
      final String name = PropertiesUtil.getFullName((PropertiesFile)element);
      if (name != null && name.equals(myBundleName)) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  public Object[] getVariants() {
    PropertiesReferenceManager referenceManager = PropertiesReferenceManager.getInstance(getElement().getProject());
    return referenceManager.getPropertyFileBaseNames(myElement.getResolveScope(), this);
  }

  public String evaluateBundleName(final PsiFile psiFile) {
    return BundleNameEvaluator.DEFAULT.evaluateBundleName(psiFile);
  }
}
