// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.xpath.xslt.validation.inspections;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.psi.*;
import org.intellij.lang.xpath.xslt.quickfix.AbstractFix;
import org.intellij.lang.xpath.xslt.quickfix.AddParameterFix;
import org.intellij.lang.xpath.xslt.quickfix.AddWithParamFix;
import org.intellij.lang.xpath.xslt.quickfix.RemoveParamFix;
import org.intellij.plugins.xpathView.XPathBundle;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class TemplateInvocationInspection extends XsltInspection {

  @Override
  @NotNull
    public HighlightDisplayLevel getDefaultLevel() {
        return HighlightDisplayLevel.ERROR;
    }

  @Override
    @NotNull
    public String getShortName() {
        return "XsltTemplateInvocation";
    }

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
        if (!(holder.getFile() instanceof XmlFile)) return PsiElementVisitor.EMPTY_VISITOR;
        final XsltElementFactory xsltElementFactory = XsltElementFactory.getInstance();
        return new XmlElementVisitor() {
            @Override
            public void visitXmlTag(XmlTag tag) {
              if (XsltSupport.isTemplateCall(tag)) {
                  final XsltCallTemplate call = xsltElementFactory.wrapElement(tag, XsltCallTemplate.class);
                    checkTemplateInvocation(call, holder, isOnTheFly);
                } else if (XsltSupport.isApplyTemplates(tag)) {
                    final XsltApplyTemplates call = xsltElementFactory.wrapElement(tag, XsltApplyTemplates.class);
                    checkTemplateInvocation(call, holder, isOnTheFly);
                }
            }
        };
    }

    private static void checkTemplateInvocation(XsltTemplateInvocation call, ProblemsHolder holder, boolean onTheFly) {
        final XsltWithParam[] arguments = call.getArguments();

        final Map<String, XsltWithParam> argNames = new HashMap<>();
        for (XsltWithParam arg : arguments) {
            final XmlAttribute attr = arg.getNameAttribute();
            if (attr != null) {
                final String name = attr.getValue();
                if (argNames.containsKey(name)) {
                    final PsiElement token = arg.getNameIdentifier();
                    assert token != null;
                    holder.registerProblem(token, XPathBundle.message("inspection.message.duplicate.argument", name));
                }
                argNames.put(name, arg);
            }
        }

        if (call instanceof XsltCallTemplate) {
            final XsltCallTemplate ct = ((XsltCallTemplate)call);
            final PsiElement nameToken = ct.getNameIdentifier();
            final XsltTemplate template = ct.getTemplate();

            if (template != null) {
                if (nameToken != null) {
                    final XsltParameter[] parameters = template.getParameters();
                    for (XsltParameter parameter : parameters) {
                        if (!argNames.containsKey(parameter.getName()) && !parameter.hasDefault()) {

                            final LocalQuickFix fix = new AddWithParamFix(parameter, call.getTag()).createQuickFix(onTheFly);
                          final String message = XPathBundle.message("inspection.message.missing.template.parameter", parameter.getName());
                          holder.registerProblem(nameToken, message, AbstractFix.createFixes(fix));
                        }
                    }
                }
                for (String s : argNames.keySet()) {
                    final XmlAttribute argAttribute = argNames.get(s).getNameAttribute();
                    assert argAttribute != null;

                    final XmlAttributeValue valueElement = argAttribute.getValueElement();
                    final PsiElement valueToken = XsltSupport.getAttValueToken(argAttribute);
                    if (valueToken != null && s.trim().length() > 0) {
                        if (template.getParameter(s) == null) {
                            final LocalQuickFix fix1 = new AddParameterFix(s, template).createQuickFix(onTheFly);
                            final LocalQuickFix fix2 = new RemoveParamFix(argNames.get(s).getTag(), s).createQuickFix(onTheFly);
                            holder.registerProblem(valueToken, XPathBundle.message("inspection.message.undeclared.template.parameter", s),
                                                   ProblemHighlightType.LIKE_UNKNOWN_SYMBOL, AbstractFix.createFixes(fix1, fix2));
                        }
                    } else if (valueElement != null) {
                        holder.registerProblem(valueElement, XPathBundle.message("inspection.message.parameter.name.expected"));
                    }
                }
            }
        }
    }
}
