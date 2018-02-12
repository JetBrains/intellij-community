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
package com.intellij.codeInsight.editorActions;

import com.intellij.application.options.editor.WebEditorOptions;
import com.intellij.codeInsight.AutoPopupController;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlExtension;
import com.intellij.xml.XmlExtension.AttributeValuePresentation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class XmlEqTypedHandler extends TypedHandlerDelegate {
  private boolean needToInsertQuotes = false;

  @NotNull
  @Override
  public Result beforeCharTyped(char c, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file, @NotNull FileType fileType) {
    if (c == '=' && WebEditorOptions.getInstance().isInsertQuotesForAttributeValue()) {
      if (XmlGtTypedHandler.fileContainsXmlLanguage(file)) {
        PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());

        PsiElement atParent = getAttributeCandidate(editor, file, false);
        if (atParent instanceof XmlAttribute && ((XmlAttribute)atParent).getValueElement() == null) {
          needToInsertQuotes = ((XmlAttribute)atParent).getValueElement() == null;
        }
      }
    }

    return super.beforeCharTyped(c, project, editor, file, fileType);
  }

  @Nullable
  private static PsiElement getAttributeCandidate(@NotNull Editor editor, @NotNull PsiFile file, boolean typed) {
    int newOffset = editor.getCaretModel().getOffset() - (typed ? 2 : 1);
    if (newOffset < 0) return null;

    PsiElement at = file.findElementAt(newOffset);
    return at != null ? at.getParent() : null;
  }

  @NotNull
  @Override
  public Result charTyped(char c, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    if (needToInsertQuotes) {
      int offset = editor.getCaretModel().getOffset();
      PsiElement fileContext = file.getContext();
      String toInsert = tryCompleteQuotes(fileContext);
      boolean showPopup = true;
      if (toInsert == null) {
        final String quote = getDefaultQuote(file);
        AttributeValuePresentation presentation = getValuePresentation(editor, file, quote);
        toInsert = presentation.getPrefix() + presentation.getPostfix();
        showPopup = presentation.showAutoPopup();
      }
      editor.getDocument().insertString(offset, toInsert);
      editor.getCaretModel().moveToOffset(offset + toInsert.length() / 2);
      if (showPopup) {
        AutoPopupController.getInstance(project).scheduleAutoPopup(editor);
      }
      needToInsertQuotes = false;
    }

    return super.charTyped(c, project, editor, file);
  }

  @Nullable
  private static String tryCompleteQuotes(@Nullable PsiElement fileContext) {
    if (fileContext != null) {
      if (fileContext.getText().startsWith("\"")) return "''";
      if (fileContext.getText().startsWith("\'")) return "\"\"";
    }
    return null;
  }

  @NotNull
  private static String getDefaultQuote(@NotNull PsiFile file) {
    return XmlEditUtil.getAttributeQuote(file);
  }

  @NotNull
  private static AttributeValuePresentation getValuePresentation(@NotNull Editor editor, @NotNull PsiFile file, @NotNull String quote) {
    PsiElement atParent = getAttributeCandidate(editor, file, true);
    XmlAttributeDescriptor descriptor = atParent instanceof XmlAttribute ? ((XmlAttribute)atParent).getDescriptor() : null;
    return XmlExtension.getExtension(file).getAttributeValuePresentation(descriptor, quote);
  }
}
