// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.htmlInspections;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Extension point to provide quick fixes for unknown XML attributes.
 * Example for JSX markup: class attribute should be fixed to className.
 */
public interface XmlUnknownAttributeQuickFixProvider {
  ExtensionPointName<XmlUnknownAttributeQuickFixProvider> EP_NAME = ExtensionPointName.create("com.intellij.xml.xmlAttributeRenameProvider");

  /**
   *
   * @param tag tag with unknown attribute
   * @param name name of unknown attribute
   * @param isFixRequired true if no check required, see {@link HtmlUnknownAttributeInspectionBase#checkAttribute(XmlAttribute, ProblemsHolder, boolean)}
   * @return collection of quick fixes for unknown attribute
   */
  @NotNull Collection<@NotNull LocalQuickFix> getOrRegisterAttributeFixes(@NotNull XmlTag tag, @NotNull String name, ProblemsHolder holder, boolean isFixRequired);

  default @NotNull ProblemHighlightType getProblemHighlightType(@NotNull PsiElement element) {
    return ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
  }
}
