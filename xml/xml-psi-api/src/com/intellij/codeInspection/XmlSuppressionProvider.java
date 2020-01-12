// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public abstract class XmlSuppressionProvider implements InspectionSuppressor {
  public static final ExtensionPointName<XmlSuppressionProvider> EP_NAME =
    new ExtensionPointName<>("com.intellij.xml.xmlSuppressionProvider");

  public static boolean isSuppressed(@NotNull PsiElement element, @NotNull String inspectionId) {
    return EP_NAME.getExtensionList().stream().anyMatch(provider -> provider.isProviderAvailable(element.getContainingFile()) &&
                                                                    provider.isSuppressedFor(element, inspectionId));
  }

  public abstract boolean isProviderAvailable(@NotNull PsiFile file);

  @Override
  public abstract boolean isSuppressedFor(@NotNull PsiElement element, @NotNull String inspectionId);

  public abstract void suppressForFile(@NotNull PsiElement element, @NotNull String inspectionId);

  public abstract void suppressForTag(@NotNull PsiElement element, @NotNull String inspectionId);

  @Override
  public SuppressQuickFix @NotNull [] getSuppressActions(@Nullable PsiElement element, @NotNull String toolId) {
    return XmlSuppressableInspectionTool.getSuppressFixes(toolId, this);
  }
}
