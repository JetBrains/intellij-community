/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.codeInspection.htmlInspections;

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.codeInsight.daemon.impl.analysis.XmlHighlightVisitor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.xml.XmlBundle;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.text.MessageFormat;

/**
 * @author Eugene.Kudelevsky
 */
public class HtmlUnknownTargetInspection extends HtmlLocalInspectionTool {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return XmlBundle.message("html.inspections.unknown.target");
  }

  @NotNull
  @Override
  public String getShortName() {
    return "HtmlUnknownTarget";
  }

  @Override
  protected void checkAttribute(@NotNull XmlAttribute attribute, @NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!HtmlUtil.isHtmlTagContainingFile(attribute)) {
      return;
    }
    if (attribute.getLocalName().equalsIgnoreCase("href")) {
      XmlAttributeValue valueElement = attribute.getValueElement();
      if (valueElement != null) {
        PsiReference[] refs = valueElement.getReferences();
        for (PsiReference ref : refs) {
          if (ref instanceof EmptyResolveMessageProvider && XmlHighlightVisitor.hasBadResolve(ref, false)) {
            String description;
            String messagePattern = ((EmptyResolveMessageProvider)ref).getUnresolvedMessagePattern();
            try {
              description = MessageFormat.format(messagePattern, ref.getCanonicalText());
            }
            catch (IllegalArgumentException ex) {
              description = messagePattern;
            }
            if (description != null) {
              description += " #loc";
            }
            holder.registerProblem(ref, description, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
          }
        }
      }
    }
  }
}
