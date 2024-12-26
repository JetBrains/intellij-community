// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.references;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixProvider;
import com.intellij.lang.properties.PropertiesQuickFixFactory;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PropertyReference extends PropertyReferenceBase implements LocalQuickFixProvider {
  private final @Nullable String myBundleName;

  public PropertyReference(final @NotNull String key, final @NotNull PsiElement element, final @Nullable String bundleName, final boolean soft, final TextRange range) {
    super(key, soft, element, range);
    myBundleName = bundleName;
  }

  public PropertyReference(@NotNull String key, @NotNull PsiElement element, final @Nullable String bundleName, final boolean soft) {
    super(key, soft, element);
    myBundleName = bundleName;
  }

  @Override
  protected @Nullable List<PropertiesFile> getPropertiesFiles() {
    if (myBundleName == null) {
      return null;
    }
    return retrievePropertyFilesByBundleName(myBundleName, myElement);
  }

  protected List<PropertiesFile> retrievePropertyFilesByBundleName(String bundleName, PsiElement element) {
    return I18nUtil.propertiesFilesByBundleName(bundleName, element);
  }

  @Override
  public @NotNull LocalQuickFix @Nullable [] getQuickFixes() {
    List<PropertiesFile> propertiesFiles = retrievePropertyFilesByBundleName(myBundleName, getElement());
    LocalQuickFix fix = PropertiesQuickFixFactory.getInstance().createCreatePropertyFix(myElement, myKey, propertiesFiles);
    return new LocalQuickFix[] {fix};
  }
}
