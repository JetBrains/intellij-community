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
package org.intellij.lang.xpath.xslt.context;

import com.intellij.codeInspection.SuppressIntentionAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.IncorrectOperationException;
import org.intellij.lang.xpath.psi.XPathExpression;
import org.intellij.lang.xpath.psi.XPathNodeTest;
import org.intellij.lang.xpath.psi.XPathType;
import org.intellij.lang.xpath.validation.inspections.XPathInspection;
import org.intellij.lang.xpath.validation.inspections.quickfix.MakeTypeExplicitFix;
import org.intellij.lang.xpath.validation.inspections.quickfix.RemoveExplicitConversionFix;
import org.intellij.lang.xpath.validation.inspections.quickfix.RemoveRedundantConversionFix;
import org.intellij.lang.xpath.validation.inspections.quickfix.XPathQuickFixFactory;
import org.intellij.lang.xpath.xslt.associations.impl.FileAssociationsConfigurable;
import org.intellij.lang.xpath.xslt.validation.inspections.InspectionUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class XsltQuickFixFactory implements XPathQuickFixFactory {
    public static final XsltQuickFixFactory INSTANCE = new XsltQuickFixFactory();

    private XsltQuickFixFactory() {
    }

    @Override
    public Fix<XPathExpression>[] createImplicitTypeConversionFixes(XPathExpression expression, XPathType type, boolean explicit) {
        //noinspection unchecked
        return explicit ? new Fix[]{
                new RemoveExplicitConversionFix(expression),
                new MakeTypeExplicitFix(expression, type),
        } : new Fix[]{
                new MakeTypeExplicitFix(expression, type),
        };
    }


    @Override
    public Fix<XPathExpression>[] createRedundantTypeConversionFixes(XPathExpression expression) {
        //noinspection unchecked
        return new Fix[]{
                new RemoveRedundantConversionFix(expression),
        };
    }

    @Override
    public Fix<XPathNodeTest>[] createUnknownNodeTestFixes(XPathNodeTest test) {
        //noinspection unchecked
        return new Fix[]{
                new EditAssociationsFix(test)
        };
    }

    @Override
    @NotNull
    public SuppressIntentionAction[] getSuppressActions(XPathInspection inspection) {
        final List<SuppressIntentionAction> actions = InspectionUtil.getSuppressActions(inspection, true);
        return actions.toArray(new SuppressIntentionAction[actions.size()]);
    }

    @Override
    public boolean isSuppressedFor(PsiElement element, XPathInspection inspection) {
        return InspectionUtil.isSuppressed(inspection, element);
    }

    private static class EditAssociationsFix extends Fix<XPathNodeTest> {
        public EditAssociationsFix(XPathNodeTest test) {
            super(test);
        }

        public boolean startInWriteAction() {
            return false;
        }

        @Override
        protected void invokeImpl(final Project project, final PsiFile file) throws IncorrectOperationException {
            FileAssociationsConfigurable.editAssociations(project, PsiTreeUtil.getContextOfType(file, XmlFile.class, false));
        }

        @NotNull
        public String getText() {
            return "Edit File Associations";
        }

        @NotNull
        public String getFamilyName() {
            return "Edit File Associations";
        }
    }
}
