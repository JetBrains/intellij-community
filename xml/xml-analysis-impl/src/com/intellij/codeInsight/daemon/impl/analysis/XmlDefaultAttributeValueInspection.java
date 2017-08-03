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

import com.intellij.codeInsight.daemon.XmlErrorMessages;
import com.intellij.codeInspection.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.xml.XmlAttributeDescriptor;
import org.jetbrains.annotations.Nls;
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
      public void visitXmlAttributeValue(XmlAttributeValue value) {
        PsiElement parent = value.getParent();
        if (!(parent instanceof XmlAttribute)) {
          return;
        }
        if (parent.getParent() instanceof HtmlTag && "input".equals(((HtmlTag)parent.getParent()).getName())) return;
        XmlAttributeDescriptor descriptor = ((XmlAttribute)parent).getDescriptor();
        if (descriptor == null) {
          return;
        }
        String defaultValue = descriptor.getDefaultValue();
        if (defaultValue != null && defaultValue.equals(value.getValue())) {
          holder.registerProblem(value, "Redundant default attribute value assignment", ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                                 new LocalQuickFix() {
                                   @Nls
                                   @NotNull
                                   @Override
                                   public String getFamilyName() {
                                     return XmlErrorMessages.message("remove.attribute.quickfix.family");
                                   }

                                   @Override
                                   public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
                                     XmlAttribute attribute = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), XmlAttribute.class);
                                     if (attribute != null) {
                                       attribute.delete();
                                     }
                                   }
                                 });
        }
      }
    };
  }
}
