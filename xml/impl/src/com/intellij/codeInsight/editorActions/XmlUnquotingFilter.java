/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlTokenType;
import org.jetbrains.annotations.NotNull;

public class XmlUnquotingFilter extends SelectionQuotingTypedHandler.UnquotingFilter {
  @Override
  public boolean skipReplacementQuotesOrBraces(@NotNull PsiFile file, @NotNull Editor editor, @NotNull String selectedText, char c) {
    if (selectedText.startsWith("<") && selectedText.endsWith(">")) {
      SelectionModel model = editor.getSelectionModel();
      int startIndex = model.getSelectionStart();
      PsiElement start = file.findElementAt(startIndex);
      int endIndex = model.getSelectionEnd();
      PsiElement end = file.findElementAt(endIndex > 0 ? endIndex - 1 : endIndex);
      if (start != null && (start.getNode().getElementType() == XmlTokenType.XML_START_TAG_START ||
                            start.getNode().getElementType() == XmlTokenType.XML_END_TAG_START) &&
          end != null && (end.getNode().getElementType() == XmlTokenType.XML_TAG_END ||
                          end.getNode().getElementType() == XmlTokenType.XML_EMPTY_ELEMENT_END)) {
        return true;
      }
    }
    return false;
  }
}
