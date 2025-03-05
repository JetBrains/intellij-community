// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.psi.PsiReference;
import com.intellij.xml.util.AnchorReference;
import org.jetbrains.annotations.NotNull;

public class HtmlUnknownAnchorTargetInspection extends XmlPathReferenceInspection {
  @Override
  public @NotNull String getShortName() {
    return "HtmlUnknownAnchorTarget";
  }

  @Override
  protected boolean isForHtml() {
    return true;
  }

  @Override
  protected boolean needToCheckRef(PsiReference reference) {
    return super.needToCheckRef(reference) && reference instanceof AnchorReference && HtmlUnknownTargetInspection.notRemoteBase(reference);
  }
}
