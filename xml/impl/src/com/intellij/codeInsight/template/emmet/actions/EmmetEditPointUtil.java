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

import com.intellij.lang.Language;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlText;
import com.intellij.psi.xml.XmlTokenType;

/**
 * @author Dennis.Ushakov
 */
public class EmmetEditPointUtil {
  public static void moveForward(Editor editor, PsiFile file) {
    if (!isApplicableFile(file)) return;
    moveToNextPoint(editor, file, editor.getCaretModel().getOffset(), 1);
  }

  public static void moveBackward(Editor editor, PsiFile file) {
    if (!isApplicableFile(file)) return;
    moveToNextPoint(editor, file, editor.getCaretModel().getOffset(), -1);
  }

  private static void moveToNextPoint(Editor editor, PsiFile file, int offset, int inc) {
    final Document doc = editor.getDocument();
    PsiDocumentManager.getInstance(file.getProject()).commitDocument(doc);
    for (int i = offset + inc; i < doc.getTextLength() && i >= 0; i += inc) {
      PsiElement current = InjectedLanguageUtil.findElementAtNoCommit(file, i);
      if (current == null) continue;

      if (current.getParent() instanceof XmlText) {
        final int line = doc.getLineNumber(i);
        final int lineStart = doc.getLineStartOffset(line);
        final int lineEnd = doc.getLineEndOffset(line);
        if (lineEnd == offset) continue;

        final CharSequence text = doc.getCharsSequence().subSequence(lineStart, lineEnd);
        if (StringUtil.isEmptyOrSpaces(text) && moveCaret(editor, current, lineEnd)) {
          return;
        }
      } else if (isEmptyEditPoint(current) && moveCaret(editor, current, current.getTextRange().getStartOffset())) {
        return;
      }
    }
  }

  private static boolean moveCaret(Editor editor, PsiElement current, int offset) {
    editor = InjectedLanguageUtil.getInjectedEditorForInjectedFile(editor, current.getContainingFile());
    if (editor.getCaretModel().getOffset() == offset) return false;
    editor.getCaretModel().moveToOffset(offset);
    return true;
  }

  private static boolean isEmptyEditPoint(PsiElement element) {
    final IElementType type = element.getNode().getElementType();
    if (type == XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER) {
      final PsiElement prev = PsiTreeUtil.prevLeaf(element);
      return prev != null && prev.getNode().getElementType() == XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER;
    }
    if (type == XmlTokenType.XML_END_TAG_START || type == XmlTokenType.XML_START_TAG_START) {
      final PsiElement prev = PsiTreeUtil.prevLeaf(element);
      return prev != null && prev.getNode().getElementType() == XmlTokenType.XML_TAG_END;
    }
    return false;
  }

  static boolean isApplicableFile(PsiFile file) {
    if (file == null) return false;
    for (Language language : file.getViewProvider().getLanguages()) {
      if (language.isKindOf(XMLLanguage.INSTANCE)) return true;
    }
    return false;
  }
}
