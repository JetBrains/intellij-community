// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.codeInspection.unsorted;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Batkovich
 */
public interface AlphaUnsortedPropertiesFileInspectionSuppressor {
  ExtensionPointName<AlphaUnsortedPropertiesFileInspectionSuppressor> EP_NAME = ExtensionPointName.create("com.intellij.properties.alphaUnsortedInspectionSuppressor");

  boolean suppressInspectionFor(@NotNull PropertiesFile propertiesFile);
}
