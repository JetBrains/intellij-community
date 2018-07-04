/*
 * Copyright 2005 Sascha Weinreuter
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

package org.intellij.lang.xpath.validation.inspections.quickfix;

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.SuppressIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.intellij.lang.xpath.psi.XPathExpression;
import org.intellij.lang.xpath.psi.XPathNodeTest;
import org.intellij.lang.xpath.psi.XPathType;
import org.intellij.lang.xpath.validation.inspections.XPathInspection;
import org.jetbrains.annotations.NotNull;

public interface XPathQuickFixFactory {
  Fix<XPathExpression>[] createImplicitTypeConversionFixes(XPathExpression expression, XPathType type, boolean explicit);

  Fix<XPathExpression>[] createRedundantTypeConversionFixes(XPathExpression expression);

  Fix<XPathNodeTest>[] createUnknownNodeTestFixes(XPathNodeTest test);

  @NotNull
  SuppressIntentionAction[] getSuppressActions(XPathInspection inspection);

  boolean isSuppressedFor(PsiElement element, XPathInspection inspection);

  abstract class Fix<E extends PsiElement> extends LocalQuickFixAndIntentionActionOnPsiElement {
    protected Fix(E element) {
      super(element);
    }

    @Override
    public void invoke(@NotNull Project project,
                       @NotNull PsiFile file,
                       Editor editor, @NotNull PsiElement startElement,
                       @NotNull PsiElement endElement) {
      invokeImpl(project, file);
    }


    protected abstract void invokeImpl(Project project, PsiFile file) throws IncorrectOperationException;
  }
}
