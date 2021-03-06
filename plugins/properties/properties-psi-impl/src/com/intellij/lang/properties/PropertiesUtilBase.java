// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * @author Anna Bulenkova
 */
public final class PropertiesUtilBase {
  private PropertiesUtilBase() {}

  @Nullable
  public static PropertiesFile getPropertiesFile(@NotNull String bundleName,
                                                 @NotNull Module searchFromModule,
                                                 @Nullable Locale locale) {
    PropertiesReferenceManager manager = PropertiesReferenceManager.getInstance(searchFromModule.getProject());
    return manager.findPropertiesFile(searchFromModule, bundleName, locale);
  }
}
