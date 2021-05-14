// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util.xml;

import com.intellij.lang.xml.XMLLanguage;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.usages.impl.rules.UsageType;
import com.intellij.usages.impl.rules.UsageTypeProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Gregory.Shrago
 */
public class DomUsageTypeProvider implements UsageTypeProvider {

  @Override
  @Nullable
  public UsageType getUsageType(@NotNull PsiElement element) {
    final PsiFile psiFile = element.getContainingFile();
    if (psiFile != null && XMLLanguage.INSTANCE.equals(psiFile.getLanguage()) &&
        DomManager.getDomManager(element.getProject()).getFileElement((XmlFile)psiFile, DomElement.class) != null) {
      return DOM_USAGE_TYPE;
    }
    return null;
  }

  private static final UsageType DOM_USAGE_TYPE = new UsageType(XmlDomBundle.messagePointer("dom.usage.type"));
}