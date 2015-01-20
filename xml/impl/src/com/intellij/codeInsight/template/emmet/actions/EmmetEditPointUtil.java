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
package com.intellij.codeInsight.template.emmet.actions;

import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTokenType;

/**
 * @author Dennis.Ushakov
 */
public class EmmetEditPointUtil {
  public static void moveForward(Editor editor, PsiFile file) {
    final int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    if (element == null || !element.getLanguage().isKindOf(XMLLanguage.INSTANCE)) return;

    do {
      element = PsiTreeUtil.nextLeaf(element);
    } while (element != null && !isEmptyEditPoint(element));

    moveCaret(editor, element);
  }

  public static void moveBackward(Editor editor, PsiFile file) {
    final int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    element = element == null ? file.findElementAt(offset - 1) : element;
    if (element == null || !element.getLanguage().isKindOf(XMLLanguage.INSTANCE)) return;

    do {
      element = PsiTreeUtil.prevLeaf(element);
    }
    while (element != null && !isEmptyEditPoint(element));

    moveCaret(editor, element);
  }

  public static void moveCaret(Editor editor, PsiElement element) {
    if (element != null) {
      int offset = element.getTextRange().getStartOffset();
      if (XmlTokenType.WHITESPACES.contains(element.getNode().getElementType())) {
        final String text = element.getText();
        if (text.startsWith("\n")) {
          final int pos = text.indexOf('\n', 1);
          offset += pos > 0 ? pos : 1;
        }
      }
      editor.getCaretModel().moveToOffset(offset);
    }
  }

  private static boolean isEmptyEditPoint(PsiElement element) {
    final IElementType type = element.getNode().getElementType();
    if (type == XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER) {
      final PsiElement prev = PsiTreeUtil.prevLeaf(element);
      return prev != null && prev.getNode().getElementType() == XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER;
    }
    if (type == XmlTokenType.XML_END_TAG_START) {
      final PsiElement tag = element.getParent();
      final PsiElement prev = PsiTreeUtil.prevLeaf(element, true);
      return prev != null && prev.getParent() == tag && prev.getNode().getElementType() == XmlTokenType.XML_TAG_END;
    }
    if (XmlTokenType.WHITESPACES.contains(type)) {
      final PsiElement text = element.getParent();
      final PsiElement prev = PsiTreeUtil.prevLeaf(element, true);
      return prev != null && prev.getParent() != text.getParent() && prev.getNode().getElementType() == XmlTokenType.XML_TAG_END;
    }
    return false;
  }
}
