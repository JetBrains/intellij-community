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
package org.intellij.lang.xpath.xslt.refactoring;

import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.refactoring.RefactoringActionHandler;
import org.intellij.lang.xpath.XPathFile;
import org.intellij.lang.xpath.psi.XPathVariable;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.psi.XsltTemplate;
import org.intellij.lang.xpath.xslt.psi.XsltVariable;
import org.intellij.lang.xpath.xslt.refactoring.introduceParameter.XsltIntroduceParameterAction;
import org.intellij.lang.xpath.xslt.refactoring.introduceVariable.XsltIntroduceVariableAction;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public class XPathRefactoringSupportProvider extends RefactoringSupportProvider {

  @Override
  public boolean isAvailable(@NotNull PsiElement context) {
    PsiFile containingFile = context.getContainingFile();
    if (containingFile instanceof XPathFile) {
      final XmlFile xmlFile = PsiTreeUtil.getContextOfType(containingFile, XmlFile.class);
      if (xmlFile != null && XsltSupport.isXsltFile(xmlFile)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isInplaceRenameAvailable(@NotNull PsiElement element, PsiElement context) {
    return element instanceof XsltVariable && element.getUseScope() instanceof LocalSearchScope;
  }

  @Override
  public RefactoringActionHandler getIntroduceParameterHandler() {
    return new XsltIntroduceParameterAction();
  }

  @Override
  public RefactoringActionHandler getIntroduceVariableHandler() {
    return new XsltIntroduceVariableAction();
  }

  @Override
  public RefactoringActionHandler getExtractMethodHandler() {
    return new XsltExtractFunctionAction();
  }

  @Override
  public boolean isSafeDeleteAvailable(@NotNull PsiElement element) {
    return element instanceof XPathVariable ||
            element instanceof XsltTemplate;
  }
}
