/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

  public boolean isInputValid(String newName, PsiElement element, ProcessingContext context) {
    return LanguageNamesValidation.INSTANCE.forLanguage(XPathFileType.XPATH.getLanguage())
      .isIdentifier(newName, element.getProject());
  }
}
