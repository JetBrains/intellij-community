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
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.xhtml.XHTMLLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
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
          if (parent instanceof XmlText && parent.getText().trim().length() > 0) {
            final PsiElement grandParent = parent.getParent();
            if (grandParent != null && !isInsideUnsplittableElement(grandParent)) {
              return InjectedLanguageManager.getInstance(project).findInjectedElementAt(file, offset) == null;
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
        final PsiElement containingTag = psiElement.getParent().getParent();
        if (containingTag instanceof XmlTag) {
          XmlTag tag = (XmlTag)containingTag;
          TextRange tagRange = tag.getTextRange();

          String name = tag.getName();
          String toInsert = "</" + name + "><" + name + getAttrsWithoutId(tag) + ">";
          editor.getDocument().insertString(offset, toInsert);
          editor.getCaretModel().moveToOffset(offset + toInsert.length());
          PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());

          CodeStyleManager.getInstance(project).reformatRange(file, tagRange.getStartOffset(), tagRange.getEndOffset() + toInsert.length());
        }
      }
    }
  }

  private static String getAttrsWithoutId(XmlTag xmlTag) {
    final StringBuilder attrsWoId = new StringBuilder();
    for (XmlAttribute attribute : xmlTag.getAttributes()) {
      if (!HtmlUtil.ID_ATTRIBUTE_NAME.equals(attribute.getName())) {
        attrsWoId.append(attribute.getName()).append("=\"").append(attribute.getValue()).append("\" ");
      }
    }
    return attrsWoId.length() == 0 ? "" : " " + attrsWoId.toString();
  } 

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
