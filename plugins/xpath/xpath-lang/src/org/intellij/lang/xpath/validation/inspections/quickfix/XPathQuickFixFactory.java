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

/*
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 11.04.2006
 * Time: 00:14:22
 */
package org.intellij.lang.xpath.validation.inspections.quickfix;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.SuppressIntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import org.intellij.lang.xpath.psi.XPathExpression;
import org.intellij.lang.xpath.psi.XPathNodeTest;
import org.intellij.lang.xpath.psi.XPathType;
import org.intellij.lang.xpath.validation.inspections.XPathInspection;

public interface XPathQuickFixFactory {
    Fix<XPathExpression>[] createImplicitTypeConversionFixes(XPathExpression expression, XPathType type, boolean explicit);

    Fix<XPathExpression>[] createRedundantTypeConversionFixes(XPathExpression expression);

    Fix<XPathNodeTest>[] createUnknownNodeTestFixes(XPathNodeTest test);

    SuppressIntentionAction[] getSuppressActions(XPathInspection inspection);

    boolean isSuppressedFor(PsiElement element, XPathInspection inspection);

    abstract class Fix<E extends PsiElement> implements LocalQuickFix, IntentionAction {
        protected final E myElement;

        protected Fix(E element) {
            myElement = element;
        }

        public boolean startInWriteAction() {
            return true;
        }

        public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
            return isAvailableImpl();
        }

        protected boolean isAvailableImpl() {
            return myElement.isValid() && myElement.getParent().isValid();
        }

        @NotNull
        public final String getText() {
            return getName();
        }

        public final void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            assert myElement == descriptor.getPsiElement();
            if (!isAvailableImpl()) return;
            
            try {
                invokeImpl(project, descriptor.getPsiElement().getContainingFile());
            } catch (IncorrectOperationException e) {
                Logger.getInstance(getClass().getName()).error(e);
            }
        }

        public final void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
            if(!CodeInsightUtilBase.prepareFileForWrite(file)) {
                return;
            }
            invokeImpl(project, file);
        }

        protected abstract void invokeImpl(Project project, PsiFile file) throws IncorrectOperationException;
    }
}
