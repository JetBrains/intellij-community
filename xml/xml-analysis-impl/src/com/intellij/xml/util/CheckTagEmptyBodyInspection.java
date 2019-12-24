/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.xml.util;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.XmlInspectionGroupNames;
import com.intellij.codeInspection.XmlSuppressableInspectionTool;
import com.intellij.lang.ASTNode;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlBundle;
import com.intellij.xml.XmlExtension;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Maxim Mossienko
 */
public class CheckTagEmptyBodyInspection extends XmlSuppressableInspectionTool {
  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new XmlElementVisitor() {
      @Override public void visitXmlTag(final XmlTag tag) {
        if (!CheckEmptyTagInspection.isTagWithEmptyEndNotAllowed(tag)) {
          final ASTNode child = XmlChildRole.START_TAG_END_FINDER.findChild(tag.getNode());

          if (child != null) {
            final ASTNode node = child.getTreeNext();

            if (node != null &&
                node.getElementType() == XmlTokenType.XML_END_TAG_START) {
              holder.registerProblem(
                tag,
                XmlBundle.message("xml.inspections.tag.empty.body"),
                isCollapsibleTag(tag) ? new Fix(tag) : null
              );
            }
          }
        }
      }
    };
  }

  static boolean isCollapsibleTag(final XmlTag tag) {
    final String name = StringUtil.toLowerCase(tag.getName());
    return tag.getLanguage() == XMLLanguage.INSTANCE ||
           "link".equals(name) || "br".equals(name) || "meta".equals(name) || "img".equals(name) || "input".equals(name) || "hr".equals(name) ||
           XmlExtension.isCollapsible(tag);
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return XmlInspectionGroupNames.XML_INSPECTIONS;
  }

  @Override
  @NotNull
  @NonNls
  public String getShortName() {
    return "CheckTagEmptyBody";
  }

  public static class Fix extends CollapseTagIntention {
    private final SmartPsiElementPointer<XmlTag> myPointer;

    public Fix(XmlTag tag) {
      myPointer = SmartPointerManager.getInstance(tag.getProject()).createSmartPsiElementPointer(tag);
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
      XmlTag tag = myPointer.getElement();
      if (tag == null) {
        return;
      }
      applyFix(project, tag);
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
      return true;
    }
  }
}