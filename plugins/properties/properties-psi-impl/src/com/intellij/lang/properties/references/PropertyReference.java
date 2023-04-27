/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
  @Nullable private final String myBundleName;

  public PropertyReference(@NotNull final String key, @NotNull final PsiElement element, @Nullable final String bundleName, final boolean soft, final TextRange range) {
    super(key, soft, element, range);
    myBundleName = bundleName;
  }

  public PropertyReference(@NotNull String key, @NotNull PsiElement element, @Nullable final String bundleName, final boolean soft) {
    super(key, soft, element);
    myBundleName = bundleName;
  }

  @Override
  @Nullable
  protected List<PropertiesFile> getPropertiesFiles() {
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
