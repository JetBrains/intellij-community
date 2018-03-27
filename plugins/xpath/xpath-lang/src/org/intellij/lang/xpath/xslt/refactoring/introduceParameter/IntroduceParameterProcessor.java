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
package org.intellij.lang.xpath.xslt.refactoring.introduceParameter;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.IncorrectOperationException;
import org.intellij.lang.xpath.psi.XPathExpression;
import org.intellij.lang.xpath.psi.XPathVariableReference;
import org.intellij.lang.xpath.psi.impl.XPathChangeUtil;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.psi.XsltElementFactory;
import org.intellij.lang.xpath.xslt.psi.XsltTemplate;
import org.intellij.lang.xpath.xslt.refactoring.RefactoringUtil;
import org.intellij.lang.xpath.xslt.util.XsltCodeInsightUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

class IntroduceParameterProcessor extends BaseRefactoringProcessor {
    private final XPathExpression myExpression;
    private final Set<XPathExpression> myOtherExpressions;
    private final IntroduceParameterOptions mySettings;

    private final XsltTemplate myTemplate;

    public IntroduceParameterProcessor(Project project, XPathExpression expression, Set<XPathExpression> otherExpressions, IntroduceParameterOptions settings) {
        super(project);
        mySettings = settings;
        myExpression = expression;
        myOtherExpressions = settings.isReplaceAll() ? otherExpressions : Collections.emptySet();

        final XmlTag templateTag = XsltCodeInsightUtil.getTemplateTag(myExpression, true, true);
        myTemplate = templateTag != null ? XsltElementFactory.getInstance().wrapElement(templateTag, XsltTemplate.class) : null;

        setPreviewUsages(settings.isPreview());
    }


    @NotNull
    protected UsageViewDescriptor createUsageViewDescriptor(@NotNull UsageInfo[] usageInfos) {
        return new MyUsageViewDescriptorAdapter();
    }

    @NotNull
    protected UsageInfo[] findUsages() {
        int usageCount = myOtherExpressions.size() + 1;

        final List<PsiElement> callsToUpdate;
        if (!mySettings.isCreateDefault()) {
            assert myTemplate != null;

            final Collection<PsiReference> references = ReferencesSearch.search(myTemplate, myTemplate.getUseScope(), false).findAll();
            callsToUpdate = new ArrayList<>(references.size());
            for (PsiReference reference : references) {
                final PsiElement e = reference.getElement();
                final XmlTag tag = PsiTreeUtil.getContextOfType(e, XmlTag.class, true);
                if (tag != null && XsltSupport.isTemplateCall(tag)) {
                    callsToUpdate.add(tag);
                }
            }
            usageCount += callsToUpdate.size();
        } else {
            //noinspection unchecked
            callsToUpdate = Collections.emptyList();
        }

        final UsageInfo[] usageInfos = new UsageInfo[usageCount];
        usageInfos[0] = XPathUsageInfo.create(myExpression);

        int i = 1;
        for (XPathExpression expression : myOtherExpressions) {
            usageInfos[i++] = XPathUsageInfo.create(expression);
        }
        for (PsiElement o : callsToUpdate) {
            usageInfos[i++] = new UsageInfo(o, false);
        }
        return usageInfos;
    }

    @Override
    protected void refreshElements(@NotNull PsiElement[] psiElements) {
        // TODO When's that called? What should it do?
    }

    protected void performRefactoring(@NotNull UsageInfo[] usageInfos) {
        XmlTag tag;
        if (myTemplate != null) {
            tag = myTemplate.getTag();
        } else if ((tag = XsltCodeInsightUtil.getTemplateTag(myExpression, true, false)) == null) {
            final XmlDocument document = PsiTreeUtil.getContextOfType(myExpression, XmlDocument.class, true);
            assert document != null;
            tag = document.getRootTag();
        }
        assert tag != null;

        final XmlTag param = tag.createChildTag("param", XsltSupport.XSLT_NS, null, false);
        try {
            param.setAttribute("name", mySettings.getName());

            if (mySettings.isCreateDefault()) {
                param.setAttribute("select", myExpression.getText());
            }

            XmlTag anchorParam = null;
            for (UsageInfo info : usageInfos) {
                if (info instanceof XPathUsageInfo) {
                    final XPathUsageInfo x = (XPathUsageInfo)info;
                    final XPathVariableReference variableReference = XPathChangeUtil.createVariableReference(x.getExpression(), mySettings.getName());
                    final XmlAttribute attribute = x.getAttribute();
                    assert attribute != null;

                    x.getExpression().replace(variableReference);

                    if (XsltSupport.isParam(attribute.getParent())) {
                        if (anchorParam == null) {
                            anchorParam = attribute.getParent();
                        } else if (attribute.getParent().getTextOffset() < anchorParam.getTextOffset()) {
                            anchorParam = attribute.getParent();
                        }
                    }
                } else {
                    final XmlTag t = (XmlTag)info.getElement();
                    if (t != null) {
                        final XmlTag p = t.createChildTag("with-param", t.getNamespace(), null, false);
                        p.setAttribute("name", mySettings.getName());
                        p.setAttribute("select", myExpression.getText());
                        t.add(p);
                    }
                }
            }

            if (anchorParam != null) {
                RefactoringUtil.addParameter(tag, param, PsiTreeUtil.getPrevSiblingOfType(anchorParam, XmlTag.class));
            } else {
                RefactoringUtil.addParameter(tag, param);
            }
        } catch (IncorrectOperationException e) {
            Logger.getInstance(getClass().getName()).error(e);
        }
    }

    @NotNull
    protected String getCommandName() {
        return XsltIntroduceParameterAction.COMMAND_NAME;
    }

    private class MyUsageViewDescriptorAdapter extends UsageViewDescriptorAdapter {

        @NotNull
        public PsiElement[] getElements() {
            return new PsiElement[]{ myTemplate };
        }

        public String getProcessedElementsHeader() {
            return "Adding parameter to template";
        }
    }
}
