// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class RemovePropertyFromBundleFix extends PsiUpdateModCommandAction<Property> {
  public RemovePropertyFromBundleFix(final @NotNull Property origProperty) {
    super(origProperty);
  }

  @Override
  public @NotNull String getFamilyName() {
    return PropertiesBundle.message("remove.property.intention.text");
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull Property property, @NotNull ModPsiUpdater updater) {
    String key = property.getUnescapedKey();
    property.delete();

    if (key == null) return;
    PropertiesFile originalPropertiesFile = PropertiesImplUtil.getPropertiesFile(context.file());
    if (originalPropertiesFile == null) return;
    for (PropertiesFile file : originalPropertiesFile.getResourceBundle().getPropertiesFiles()) {
      if (file.getContainingFile().equals(context.file())) continue;
      PsiFile writableFile = updater.getWritable(file.getContainingFile());
      PropertiesFile writablePropsFile = PropertiesImplUtil.getPropertiesFile(writableFile);
      if (writablePropsFile == null) continue;
      for (IProperty prop : writablePropsFile.findPropertiesByKey(key)) {
        prop.getPsiElement().delete();
      }
    }
  }
}
