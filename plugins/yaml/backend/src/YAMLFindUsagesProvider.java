// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml;

import com.intellij.lang.HelpID;
import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.*;

/**
 * @author shalupov
 */
public class YAMLFindUsagesProvider implements FindUsagesProvider {
  @Override
  public @NotNull WordsScanner getWordsScanner() {
    return new YAMLWordsScanner();
  }

  @Override
  public boolean canFindUsagesFor(@NotNull PsiElement psiElement) {
    return psiElement instanceof PsiNamedElement || psiElement instanceof YAMLScalar;
  }

  @Override
  public @NotNull String getHelpId(@NotNull PsiElement psiElement) {
    return HelpID.FIND_OTHER_USAGES;
  }

  @Override
  public @NotNull String getType(@NotNull PsiElement element) {
    if (element instanceof YAMLScalarText) {
      return YAMLBundle.message("find.usages.scalar");
    } else if (element instanceof YAMLSequence) {
      return YAMLBundle.message("find.usages.sequence");
    } else if (element instanceof YAMLMapping) {
      return YAMLBundle.message("find.usages.mapping");
    } else if (element instanceof YAMLKeyValue) {
      return YAMLBundle.message("find.usages.key.value");
    } else if (element instanceof YAMLDocument) {
      return YAMLBundle.message("find.usages.document");
    } else {
      return "";
    }
  }

  @Override
  public @NotNull String getDescriptiveName(@NotNull PsiElement element) {
    if (element instanceof PsiNamedElement) {
      return StringUtil.notNullize(((PsiNamedElement)element).getName(), YAMLBundle.message("find.usages.unnamed"));
    }

    if (element instanceof YAMLScalar) {
      return ((YAMLScalar)element).getTextValue();
    }

    return YAMLBundle.message("find.usages.unnamed");
  }

  @Override
  public @NotNull String getNodeText(@NotNull PsiElement element, boolean useFullName) {
    return getDescriptiveName(element);
  }
}