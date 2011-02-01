/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.intellij.lang.xpath.xslt.validation.inspections;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
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

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 24.01.2008
*/
public class VariableShadowingInspection extends XsltInspection {

  @NotNull
  public String getDisplayName() {
    return "Variable Shadowing";
  }

  @NotNull
  public String getShortName() {
    return "XsltVariableShadowing";
  }

  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.WARNING;
  }

  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
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