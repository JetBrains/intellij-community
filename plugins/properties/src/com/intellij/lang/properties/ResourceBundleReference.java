/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.lang.properties;

import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.psi.*;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author yole
 */
public class ResourceBundleReference extends PsiReferenceBase<PsiElement>
  implements PsiPolyVariantReference, BundleNameEvaluator, ResolvingHint {
  private static final Function<PropertiesFile, PsiElement> PROPERTIES_FILE_PSI_ELEMENT_FUNCTION =
    PropertiesFile::getContainingFile;
  private final String myBundleName;

  public ResourceBundleReference(final PsiElement element) {
    this(element, false);
  }

  public ResourceBundleReference(final PsiElement element, boolean soft) {
    super(element, soft);
    myBundleName = getValue().replace('/', '.');
  }

  @Override
  public boolean canResolveTo(Class<? extends PsiElement> elementClass) {
    return ReflectionUtil.isAssignable(PsiFile.class, elementClass);
  }

  @Override
  @Nullable
  public PsiElement resolve() {
    ResolveResult[] resolveResults = multiResolve(false);
    return resolveResults.length == 1 ? resolveResults[0].getElement() : null;
  }

  @Override
  @NotNull
  public ResolveResult[] multiResolve(final boolean incompleteCode) {
    PropertiesReferenceManager referenceManager = PropertiesReferenceManager.getInstance(myElement.getProject());
    List<PropertiesFile> propertiesFiles = referenceManager.findPropertiesFiles(myElement.getResolveScope(), myBundleName, this);
    return PsiElementResolveResult.createResults(ContainerUtil.map(propertiesFiles, PROPERTIES_FILE_PSI_ELEMENT_FUNCTION));
  }

  @Override
  @NotNull
  public String getCanonicalText() {
    return myBundleName;
  }

  @Override
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    if (newElementName.endsWith(PropertiesFileType.DOT_DEFAULT_EXTENSION)) {
      newElementName = newElementName.substring(0, newElementName.lastIndexOf(PropertiesFileType.DOT_DEFAULT_EXTENSION));
    }

    final String currentValue = getValue();
    final char packageDelimiter = getPackageDelimiter();
    final int index = currentValue.lastIndexOf(packageDelimiter);
    if (index != -1) {
      newElementName = currentValue.substring(0, index) + packageDelimiter + newElementName;
    }

    return super.handleElementRename(newElementName);
  }

  private char getPackageDelimiter() {
    return getValue().indexOf('/') != -1 ? '/' : '.';
  }

  @Override
  public PsiElement bindToElement(@NotNull final PsiElement element) throws IncorrectOperationException {
    if (!(element instanceof PropertiesFile)) {
      throw new IncorrectOperationException();
    }
    final String name = ResourceBundleManager.getInstance(element.getProject()).getFullName((PropertiesFile)element);
    return name != null ? super.handleElementRename(name) : element;
  }


  @Override
  public boolean isReferenceTo(PsiElement element) {
    if (element instanceof PropertiesFile) {
      final String name = ResourceBundleManager.getInstance(element.getProject()).getFullName((PropertiesFile)element);
      if (name != null && name.equals(myBundleName)) {
        return true;
      }
    }
    return false;
  }

  @Override
  @NotNull
  public Object[] getVariants() {
    final ProjectFileIndex projectFileIndex = ProjectFileIndex.SERVICE.getInstance(getElement().getProject());
    final PropertiesReferenceManager referenceManager = PropertiesReferenceManager.getInstance(getElement().getProject());

    final Set<String> bundleNames = new HashSet<>();
    final List<LookupElement> variants = new SmartList<>();
    PropertiesFileProcessor processor = (baseName, propertiesFile) -> {
      if (!bundleNames.add(baseName)) return true;

      final LookupElementBuilder builder =
        LookupElementBuilder.create(baseName)
          .withIcon(AllIcons.Nodes.ResourceBundle);
      boolean isInContent = projectFileIndex.isInContent(propertiesFile.getVirtualFile());
      variants.add(isInContent ? PrioritizedLookupElement.withPriority(builder, Double.MAX_VALUE) : builder);
      return true;
    };

    referenceManager.processPropertiesFiles(myElement.getResolveScope(), processor, this);
    return variants.toArray(new LookupElement[variants.size()]);
  }

  @Override
  public String evaluateBundleName(final PsiFile psiFile) {
    return BundleNameEvaluator.DEFAULT.evaluateBundleName(psiFile);
  }
}
