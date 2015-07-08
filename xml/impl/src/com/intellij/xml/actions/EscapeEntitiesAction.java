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
package com.intellij.xml.actions;

import com.intellij.codeInsight.actions.SimpleCodeInsightAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTokenType;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dennis.Ushakov
 */
public class EscapeEntitiesAction extends SimpleCodeInsightAction {
  private static String escape(XmlFile file, String text, int start) {
    final StringBuilder result = new StringBuilder();
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      final PsiElement element = file.findElementAt(start + i);
      if (element != null && element.getNode().getElementType() == XmlTokenType.XML_DATA_CHARACTERS) {
        if (c == '<') {
          result.append("&lt;");
          continue;
        }
        if (c == '>') {
          result.append("&gt;");
          continue;
        }
        if (c == '&') {
          result.append("&amp;");
          continue;
        }
        if (c == '"') {
          result.append("&quot;");
          continue;
        }
        if (c == '\'') {
          result.append("&#39;");
          continue;
        }
      }
      result.append(c);
    }
    return result.toString();
  }
  @Override
  protected boolean isValidForFile(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    return ApplicationManager.getApplication().isInternal() && file instanceof XmlFile;
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    int[] starts = editor.getSelectionModel().getBlockSelectionStarts();
    int[] ends = editor.getSelectionModel().getBlockSelectionEnds();
    final Document document = editor.getDocument();
    for (int i = starts.length - 1; i >= 0; i--) {
      final int start = starts[i];
      final int end = ends[i];
      String oldText = document.getText(new TextRange(start, end));
      final String newText = escape((XmlFile)file, oldText, start);
      if (!oldText.equals(newText)) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            document.replaceString(start, end, newText);
          }
        });
      }
    }
  }
}
