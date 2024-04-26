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

package org.intellij.lang.xpath.xslt.impl;

import com.intellij.codeInsight.daemon.impl.analysis.XmlUnresolvedReferenceInspection;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.util.MoveRenameUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.*;
import com.intellij.usages.rules.SingleParentUsageGroupingRule;
import com.intellij.usages.rules.UsageGroupingRule;
import com.intellij.usages.rules.UsageGroupingRuleProvider;
import org.intellij.lang.xpath.psi.XPathExpression;
import org.intellij.lang.xpath.xslt.psi.XsltParameter;
import org.intellij.lang.xpath.xslt.psi.XsltTemplate;
import org.intellij.lang.xpath.xslt.util.XsltCodeInsightUtil;
import org.intellij.lang.xpath.xslt.validation.inspections.TemplateInvocationInspection;
import org.intellij.lang.xpath.xslt.validation.inspections.UnusedElementInspection;
import org.intellij.lang.xpath.xslt.validation.inspections.VariableShadowingInspection;
import org.intellij.lang.xpath.xslt.validation.inspections.XsltDeclarationInspection;
import org.intellij.plugins.xpathView.XPathBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.xml.namespace.QName;

public class XsltStuffProvider implements UsageGroupingRuleProvider {

  @SuppressWarnings({"unchecked"})
  public static final Class<? extends LocalInspectionTool>[] INSPECTION_CLASSES = new Class[]{
    UnusedElementInspection.class,
    TemplateInvocationInspection.class,
    XsltDeclarationInspection.class,
    VariableShadowingInspection.class,
    XmlUnresolvedReferenceInspection.class
  };

    private final UsageGroupingRule[] myUsageGroupingRules;

    public XsltStuffProvider() {
      myUsageGroupingRules = new UsageGroupingRule[]{ new TemplateUsageGroupingRule() };
    }

  @Override
  public @NotNull UsageGroupingRule @NotNull [] getActiveRules(@NotNull Project project) {
        return myUsageGroupingRules;
    }

    private static class TemplateUsageGroup implements UsageGroup {
        private final XsltTemplate myTemplate;

        TemplateUsageGroup(@NotNull XsltTemplate template) {
            myTemplate = template;
        }

        @Override
        public Icon getIcon() {
            return myTemplate.getIcon(0);
        }

        @Override
        @NotNull
        public String getPresentableGroupText() {
            final StringBuilder sb = new StringBuilder();

            final XPathExpression expr = myTemplate.getMatchExpression();
            if (expr != null) sb.append("match='").append(expr.getText()).append("'");
            final QName mode = myTemplate.getMode();

            if (mode != null) {
                if (sb.length() > 0) sb.append(", ");
                sb.append("mode='").append(mode.toString()).append("'");
            }
            return XPathBundle.message("list.item.template", sb);
        }

        @Override
        public boolean isValid() {
            return myTemplate.isValid();
        }

        @Override
        public int compareTo(@NotNull UsageGroup usageGroup) {
            final TemplateUsageGroup myUsageGroup = ((TemplateUsageGroup)usageGroup);
            return myTemplate.getTextOffset() - myUsageGroup.myTemplate.getTextOffset();
        }

        @Override
        public void navigate(boolean requestFocus) {
            ((Navigatable)myTemplate.getTag()).navigate(requestFocus);
        }

        @Override
        public boolean canNavigate() {
            return ((Navigatable)myTemplate.getTag()).canNavigate();
        }

        @Override
        public boolean canNavigateToSource() {
            return canNavigate();
        }

        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            final TemplateUsageGroup that = (TemplateUsageGroup)o;

            if (!myTemplate.equals(that.myTemplate)) return false;

            assert compareTo(that) == 0;
            return true;
        }

        public int hashCode() {
            return myTemplate.hashCode();
        }
    }

    private static class TemplateUsageGroupingRule extends SingleParentUsageGroupingRule {
        @Nullable
        @Override
        protected UsageGroup getParentGroupFor(@NotNull Usage usage, UsageTarget @NotNull [] targets) {
            if (usage instanceof UsageInfo2UsageAdapter u) {
              final UsageInfo usageInfo = u.getUsageInfo();
                if (usageInfo instanceof MoveRenameUsageInfo info) {
                  return buildGroup(info.getReferencedElement(), usageInfo, true);
                } else {
                    for (UsageTarget target : targets) {
                        UsageGroup group = target instanceof PsiElementUsageTarget ?
                                           buildGroup(((PsiElementUsageTarget)target).getElement(), usageInfo, false) :
                                           null;
                        if (group != null) return group;
                    }
                }
            }
            return null;
        }

        @Nullable
        private static UsageGroup buildGroup(PsiElement referencedElement, UsageInfo u, boolean mustBeForeign) {
            if (referencedElement instanceof XsltParameter parameter) {
              final PsiElement element = u.getElement();
                if (element == null) return null;
                final XsltTemplate template = XsltCodeInsightUtil.getTemplate(element, false);
                if (template == null) return null;

                final boolean isForeign = XsltCodeInsightUtil.getTemplate(parameter, false) != template;
                if (template.getMatchExpression() != null && (isForeign || !mustBeForeign)) {
                    return new TemplateUsageGroup(template);
                }
            }
            return null;
        }
    }
}
