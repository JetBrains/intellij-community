// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.xpath.xslt.validation.inspections;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.quickfix.AbstractFix;
import org.intellij.lang.xpath.xslt.quickfix.RenameVariableFix;
import org.intellij.lang.xpath.xslt.validation.DeclarationChecker;
import org.intellij.plugins.xpathView.XPathBundle;
import org.jetbrains.annotations.NotNull;

public class VariableShadowingInspection extends XsltInspection {

  @Override
  @NotNull
  public String getShortName() {
    return "XsltVariableShadowing";
  }

  @Override
  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    if (!(holder.getFile() instanceof XmlFile)) return PsiElementVisitor.EMPTY_VISITOR;
    return new XmlElementVisitor() {
      @Override
      public void visitXmlTag(final @NotNull XmlTag tag) {
        final XmlAttribute nameAttr = tag.getAttribute("name", null);
        if (nameAttr == null || PsiTreeUtil.hasErrorElements(nameAttr)) return;

        if (XsltSupport.isVariableOrParam(tag)) {
          final XmlTag shadowedVariable = DeclarationChecker.getInstance((XmlFile)tag.getContainingFile()).getShadowedVariable(tag);
          if (shadowedVariable != null) {

            final LocalQuickFix fix1 = new RenameVariableFix(tag, XPathBundle.message("variable.place.local")).createQuickFix(isOnTheFly);
            final LocalQuickFix fix2 = new RenameVariableFix(shadowedVariable, XPathBundle.message("variable.place.outer")).createQuickFix(isOnTheFly);

            final PsiElement token = XsltSupport.getAttValueToken(nameAttr);
            if (token == null) return;
            final String message = XPathBundle.message("inspection.message.variable.shadows.variable",
                                                       XsltSupport.isParam(tag) ? 0 : 1,
                                                       nameAttr.getValue(),
                                                       XsltSupport.isParam(shadowedVariable) ? 0 : 1);
            //noinspection DialogTitleCapitalization
            holder.registerProblem(token, message, AbstractFix.createFixes(fix1, fix2));
          }
        }
      }
    };
  }
}