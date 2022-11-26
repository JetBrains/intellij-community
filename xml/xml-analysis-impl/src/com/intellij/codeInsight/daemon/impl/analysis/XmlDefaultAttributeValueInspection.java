/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.XmlSuppressableInspectionTool;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.analysis.XmlAnalysisBundle;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public class XmlDefaultAttributeValueInspection extends XmlSuppressableInspectionTool {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new XmlElementVisitor() {
      @Override
      public void visitXmlAttributeValue(@NotNull XmlAttributeValue value) {
        PsiElement parent = value.getParent();
        if (!(parent instanceof XmlAttribute)) {
          return;
        }
        if (parent.getParent() instanceof HtmlTag || HtmlUtil.isHtmlFile(parent.getParent())) return;
        XmlAttributeDescriptor descriptor = ((XmlAttribute)parent).getDescriptor();
        if (descriptor == null || descriptor.isRequired()) {
          return;
        }
        String defaultValue = descriptor.getDefaultValue();
        if (defaultValue != null && defaultValue.equals(value.getValue()) && !value.getTextRange().isEmpty()) {
          holder.registerProblem(value, XmlAnalysisBundle.message("xml.inspections.redundant.default.attribute.value.assignment"), ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                                 new RemoveAttributeIntentionFix(((XmlAttribute)parent).getLocalName()));
        }
      }
    };
  }
}
