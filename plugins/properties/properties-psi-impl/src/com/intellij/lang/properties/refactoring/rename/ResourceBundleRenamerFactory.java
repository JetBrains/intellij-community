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
  public boolean isApplicable(@NotNull final PsiElement element) {
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


  @Nullable
  @Override
  public String getOptionName() {
    return PropertiesBundle.message("resource.bundle.renamer.option");
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  @Override
  public void setEnabled(final boolean enabled) {
  }

  @NotNull
  @Override
  public AutomaticRenamer createRenamer(final PsiElement element, final String newName, final Collection<UsageInfo> usages) {
    final PropertiesFile propertiesFile = PropertiesImplUtil.getPropertiesFile((PsiFile)element);
    assert propertiesFile != null;
    return new ResourceBundleRenamer(propertiesFile, newName);
  }
}
