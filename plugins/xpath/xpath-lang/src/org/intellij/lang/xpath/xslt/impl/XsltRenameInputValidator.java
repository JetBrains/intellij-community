// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.xpath.xslt.impl;

import com.intellij.lang.LanguageNamesValidation;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.RenameInputValidator;
import com.intellij.util.ProcessingContext;
import org.intellij.lang.xpath.XPathFileType;
import org.intellij.lang.xpath.xslt.psi.impl.XsltLanguage;

import static com.intellij.patterns.PlatformPatterns.psiElement;

public class XsltRenameInputValidator implements RenameInputValidator {
  @Override
  public ElementPattern<? extends PsiElement> getPattern() {
    return psiElement().withLanguage(XsltLanguage.INSTANCE);
  }

  @Override
  public boolean isInputValid(String newName, PsiElement element, ProcessingContext context) {
    return LanguageNamesValidation.INSTANCE.forLanguage(XPathFileType.XPATH.getLanguage())
      .isIdentifier(newName, element.getProject());
  }
}
