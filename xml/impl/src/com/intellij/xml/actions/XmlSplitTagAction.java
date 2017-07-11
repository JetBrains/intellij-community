/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.xml.actions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.xhtml.XHTMLLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.xml.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlBundle;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
public class XmlSplitTagAction implements IntentionAction {

  @Override
  @NotNull
  public String getText() {
    return XmlBundle.message("xml.split.tag.intention.action");
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return XmlBundle.message("xml.split.tag.intention.action");
  }

  @Override
  public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
    if (file instanceof XmlFile) {
      if (editor != null) {
        final int offset = editor.getCaretModel().getOffset();
        final PsiElement psiElement = file.findElementAt(offset);
        if (psiElement != null) {
          final PsiElement parent = psiElement.getParent();
          if (parent != null && parent instanceof XmlText && parent.getText().trim().length() > 0) {
            final PsiElement grandParent = parent.getParent();
            if (grandParent != null && !isInsideUnsplittableElement(grandParent)) {
              return true;
            }
          }
        }
      }
    }

    return false;
  }

  private static boolean isInsideUnsplittableElement(final PsiElement grandParent) {
    if (!(grandParent instanceof HtmlTag) && grandParent.getContainingFile().getLanguage() != XHTMLLanguage.INSTANCE) {
      return false;
    }

    final String name = ((XmlTag)grandParent).getName();
    return "html".equals(name) || "body".equals(name) || "title".equals(name);
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    if (editor != null) {
      final int offset = editor.getCaretModel().getOffset();
      final PsiElement psiElement = file.findElementAt(offset);
      if (psiElement != null) {
        final PsiElement xmlText = psiElement.getParent();
        final TextRange textRange = xmlText.getTextRange();
        final int offsetInElement = offset - textRange.getStartOffset();

        final PsiElement containingTag = xmlText.getParent();
        if (containingTag instanceof XmlTag) {
          final XmlTag xmlTag = (XmlTag)containingTag;

          final String s = xmlText.getText();
          String first = s.substring(0, offsetInElement);
          String second = s.substring(offsetInElement);

          if (xmlText instanceof XmlTagChild) {
            XmlTagChild prev = ((XmlTagChild)xmlText).getPrevSiblingInTag();
            while(prev != null) {
              first = prev.getText() + first;
              prev = prev.getPrevSiblingInTag();
            }

            XmlTagChild next = ((XmlTagChild)xmlText).getNextSiblingInTag();
            while(next != null) {
              second += next.getText();
              next = next.getNextSiblingInTag();
            }
          }

          final String filetext = buildNewText(xmlTag, first, second);

          final XmlFile xmlFile = (XmlFile)PsiFileFactory.getInstance(project).createFileFromText("dummy.xml", xmlTag.getLanguage(),
                                                                                               filetext);
          final PsiElement parent2 = containingTag.getParent();
          final XmlTag tag = xmlFile.getDocument().getRootTag();
          XmlTag last = null;
          final PsiElement[] children = tag.getChildren();
          for (int i = children.length - 1; i >= 0; i--) {
            PsiElement element = children[i];
            if (element instanceof XmlTag) {
              final XmlTag tag1 = (XmlTag)parent2.addAfter(element, containingTag);

              if (last == null) {
                last = tag1;
              }
            }
          }

          containingTag.delete();
          editor.getCaretModel().moveToOffset(last.getValue().getTextRange().getStartOffset());
        }
      }
    }
  }

  private static String buildNewText(final XmlTag xmlTag, final String first, final String second) {
    final StringBuilder attrs = new StringBuilder();
    final StringBuilder attrsWoId = new StringBuilder();
    for (XmlAttribute attribute : xmlTag.getAttributes()) {
      if (!HtmlUtil.ID_ATTRIBUTE_NAME.equals(attribute.getName())) {
        attrs.append(attribute.getName()).append("=\"").append(attribute.getValue()).append("\" ");
        attrsWoId.append(attribute.getName()).append("=\"").append(attribute.getValue()).append("\" ");
      } else {
        attrs.append(attribute.getName()).append("=\"").append(attribute.getValue()).append("\" ");
      }

    }

    final StringBuilder sb = new StringBuilder();
    final String name = xmlTag.getName();
    sb.append("<root><").append(name);
    if (attrs.length() > 0) {
      sb.append(' ').append(attrs);
    }
    sb.append('>');
    sb.append(first);
    sb.append("</").append(name).append("><").append(name);
    if (attrsWoId.length() > 0) {
      sb.append(' ').append(attrsWoId);
    }
    sb.append('>');
    sb.append(second).append("</").append(name).append("></root>");

    return sb.toString();
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
