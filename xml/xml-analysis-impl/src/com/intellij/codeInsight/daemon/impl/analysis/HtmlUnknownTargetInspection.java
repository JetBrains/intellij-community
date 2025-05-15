// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlFile;
import com.intellij.xml.util.AnchorReference;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NotNull;

public class HtmlUnknownTargetInspection extends XmlPathReferenceInspection {
  @Override
  public @NotNull String getShortName() {
    return "HtmlUnknownTarget";
  }

  @Override
  protected boolean isForHtml() {
    return true;
  }

  @Override
  protected boolean needToCheckRef(PsiReference reference) {
    return super.needToCheckRef(reference) && !(reference instanceof AnchorReference) && notRemoteBase(reference);
  }

  static boolean notRemoteBase(PsiReference reference) {
    final PsiFile psiFile = reference.getElement().getContainingFile();
    final String basePath = psiFile instanceof XmlFile ? HtmlUtil.getHrefBase((XmlFile)psiFile) : null;
    return basePath == null || !HtmlUtil.hasHtmlPrefix(basePath);
  }
}
