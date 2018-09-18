// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.jetbrains.annotations.NotNull;

public class VariableShadowingInspection extends XsltInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return "Variable Shadowing";
  }

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
      public void visitXmlTag(final XmlTag tag) {
        final XmlAttribute nameAttr = tag.getAttribute("name", null);
        if (nameAttr == null || PsiTreeUtil.hasErrorElements(nameAttr)) return;

        if (XsltSupport.isVariableOrParam(tag)) {
          final XmlTag shadowedVariable = DeclarationChecker.getInstance((XmlFile)tag.getContainingFile()).getShadowedVariable(tag);
          if (shadowedVariable != null) {
            final String innerKind = XsltSupport.isParam(tag) ? "Parameter" : "Variable";
            final String outerKind = XsltSupport.isParam(shadowedVariable) ? "parameter" : "variable";

            final LocalQuickFix fix1 = new RenameVariableFix(tag, "local").createQuickFix(isOnTheFly);
            final LocalQuickFix fix2 = new RenameVariableFix(shadowedVariable, "outer").createQuickFix(isOnTheFly);

            final XmlAttribute name = tag.getAttribute("name");
            assert name != null;

            final PsiElement token = XsltSupport.getAttValueToken(name);
            assert token != null;

            holder.registerProblem(token,
                    innerKind + " '" + name.getValue() + "' shadows " + outerKind,
                    AbstractFix.createFixes(fix1, fix2));
          }
        }
      }
    };
  }
}