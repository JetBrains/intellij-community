// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml;

import com.intellij.codeInsight.daemon.ChangeLocalityDetector;
import com.intellij.codeInspection.DefaultXmlSuppressionProvider;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public class XmlChangeLocalityDetector implements ChangeLocalityDetector {
  @Override
  public PsiElement getChangeHighlightingDirtyScopeFor(@NotNull PsiElement changedElement) {
    // rehighlight everything when inspection suppress comment changed
    if (changedElement.getLanguage() instanceof XMLLanguage
        && changedElement instanceof PsiComment
        && changedElement.getText().contains(DefaultXmlSuppressionProvider.SUPPRESS_MARK)) {
      return changedElement.getContainingFile();
    }
    return null;
  }
}
