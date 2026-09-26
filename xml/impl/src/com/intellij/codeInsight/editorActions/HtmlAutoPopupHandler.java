// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.html.HtmlFileImpl;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlText;
import com.intellij.psi.xml.XmlTokenType;
import org.jetbrains.annotations.NotNull;

public class HtmlAutoPopupHandler extends TypedHandlerDelegate {

  @Override
  public @NotNull Result checkAutoPopup(char charTyped,
                                        @NotNull Project project,
                                        @NotNull Editor editor,
                                        @NotNull PsiFile file) {
    if (charTyped != '&' || !(file instanceof HtmlFileImpl)) {
      return Result.CONTINUE;
    }
    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    if (element == null) {
      return Result.CONTINUE;
    }
    IElementType elementType = element.getNode().getElementType();
    PsiElement parent = element.getParent();
    if (elementType != XmlTokenType.XML_END_TAG_START
        && elementType != XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER
        && elementType != XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN
        && !((elementType == XmlTokenType.XML_DATA_CHARACTERS || elementType == XmlTokenType.XML_WHITE_SPACE) && parent instanceof XmlText)) {
      return Result.CONTINUE;
    }
    AutoPopupController.getInstance(project).scheduleAutoPopup(editor);
    return Result.STOP;
  }
}
