/*
 * Copyright 2007 Sascha Weinreuter
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
package org.intellij.lang.xpath.xslt.validation;

import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.psi.XsltParameter;
import org.intellij.lang.xpath.xslt.psi.XsltVariable;
import org.intellij.lang.xpath.xslt.quickfix.DeleteUnusedParameterFix;
import org.intellij.lang.xpath.xslt.quickfix.DeleteUnusedVariableFix;
import org.intellij.lang.xpath.xslt.quickfix.DeleteUnusedElementBase;
import org.intellij.lang.xpath.xslt.util.XsltCodeInsightUtil;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.Query;

import java.util.Collection;

public class XsltValidator {
  private XsltValidator() {
  }

  public static void checkUnusedVariable(XsltVariable variable, ProblemsHolder holder) {
        if (variable instanceof XsltParameter) {
            if (((XsltParameter)variable).isAbstract()) {
                return;
            }
        } else {
            if (variable.isVoid()) {
                return;
            }
        }

        final XmlTag tag = variable.getTag();
        final XmlTag templateTag = XsltCodeInsightUtil.getTemplateTag(tag, false);
        if (templateTag == null) {
            return;
        }
        final XmlAttribute attribute = tag.getAttribute("name");
        if (attribute == null) {
          return;
        }
        final PsiElement token = XsltSupport.getAttValueToken(attribute);
        if (token == null) {
          return;
        }

        final SearchScope scope = new LocalSearchScope(templateTag);
        final Query<PsiReference> refs = ReferencesSearch.search(variable, scope, false);

        if (isUnused(variable, refs)) {
            final String name = variable.getName();
            assert name != null;

            final LocalQuickFix[] fixes;
            if (variable instanceof XsltParameter) {
                fixes = new LocalQuickFix[]{ new DeleteUnusedParameterFix(name, (XsltParameter)variable) };
            } else {
                fixes = new LocalQuickFix[]{ new DeleteUnusedVariableFix(name, variable) };
            }

            holder.registerProblem(token, ((DeleteUnusedElementBase)fixes[0]).getType() +
                    " '" + name + "' is never used", ProblemHighlightType.LIKE_UNUSED_SYMBOL, fixes);
        }
    }

    private static boolean isUnused(PsiElement obj, Query<PsiReference> query) {
        if (obj instanceof XsltParameter) {
            final Collection<PsiReference> references = query.findAll();
            int n = references.size();
            for (PsiReference reference : references) {
                final PsiElement element = reference.getElement();
                if (element instanceof XmlAttributeValue) {
                    final XmlAttribute parent = (XmlAttribute)element.getParent();
                    if ("name".equals(parent.getName())) {
                        final XmlTag tag = parent.getParent();
                        if (tag != null && "with-param".equals(tag.getLocalName())) {
                            n--;
                        }
                    }
                }
            }
            return n == 0;
        } else {
            return query.findFirst() == null;
        }
    }
}
