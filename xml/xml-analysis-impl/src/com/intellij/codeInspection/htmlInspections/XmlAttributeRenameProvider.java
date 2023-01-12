// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.htmlInspections;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Extension point to provide renaming options for xml attributes
 * Example for JSX markup: class attribute should be fixed to className
 * */
public interface XmlAttributeRenameProvider {
  ExtensionPointName<XmlAttributeRenameProvider> EP_NAME = ExtensionPointName.create("com.intellij.xml.xmlAttributeRenameProvider");

  @NotNull Collection<LocalQuickFix> getAttributeFixes(@NotNull XmlTag tag, @NotNull String name);
}
