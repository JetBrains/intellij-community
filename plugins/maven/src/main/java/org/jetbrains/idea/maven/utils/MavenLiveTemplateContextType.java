// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.utils;

import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.project.MavenProjectBundle;

public final class MavenLiveTemplateContextType extends TemplateContextType {
  public MavenLiveTemplateContextType() {
    super(MavenProjectBundle.message("configurable.MavenSettings.display.name"));
  }

  @Override
  public boolean isInContext(final @NotNull PsiFile file, final int offset) {
    if (!MavenDomUtil.isMavenFile(file)) return false;

    PsiElement element = file.findElementAt(offset);
    if (element == null) return false;

    if (PsiTreeUtil.getParentOfType(element, XmlText.class) == null) return false;

    return true;
  }
}
