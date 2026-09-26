// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.refactoring.rename;

import com.intellij.lang.properties.PropertiesBundle;
import com.intellij.lang.properties.PropertiesImplUtil;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.rename.naming.AutomaticRenamer;
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author Dmitry Batkovich
 */
public class ResourceBundleRenamerFactory implements AutomaticRenamerFactory {
  @Override
  public boolean isApplicable(final @NotNull PsiElement element) {
    if (!(element instanceof PsiFile)) {
      return false;
    }
    final PropertiesFile file = PropertiesImplUtil.getPropertiesFile(element);
    if (file == null) {
      return false;
    }
    final ResourceBundle resourceBundle = file.getResourceBundle();
    return resourceBundle.getBaseDirectory() != null && resourceBundle.getPropertiesFiles().size() != 1;
  }


  @Override
  public @Nullable String getOptionName() {
    return PropertiesBundle.message("resource.bundle.renamer.option");
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  @Override
  public void setEnabled(final boolean enabled) {
  }

  @Override
  public @NotNull AutomaticRenamer createRenamer(final PsiElement element, final String newName, final Collection<UsageInfo> usages) {
    final PropertiesFile propertiesFile = PropertiesImplUtil.getPropertiesFile((PsiFile)element);
    assert propertiesFile != null;
    return new ResourceBundleRenamer(propertiesFile, newName);
  }
}
