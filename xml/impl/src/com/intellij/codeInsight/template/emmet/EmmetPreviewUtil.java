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
package com.intellij.codeInsight.template.emmet;

import com.intellij.codeInsight.template.emmet.generators.XmlZenCodingGenerator;
import com.intellij.codeInsight.template.emmet.generators.ZenCodingGenerator;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.undo.UndoConstants;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.Producer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;

public class EmmetPreviewUtil {
  private EmmetPreviewUtil() {
  }

  @Nullable
  public static String calculateTemplateText(@NotNull Editor editor, @NotNull PsiFile file, boolean expandPrimitiveAbbreviations) {
    PsiDocumentManager.getInstance(file.getProject()).commitDocument(editor.getDocument());
    CollectCustomTemplateCallback callback = new CollectCustomTemplateCallback(editor, file);
    ZenCodingGenerator generator = ZenCodingTemplate.findApplicableDefaultGenerator(callback, false);
    if (generator instanceof XmlZenCodingGenerator) {
      final String templatePrefix = new ZenCodingTemplate().computeTemplateKeyWithoutContextChecking(callback);
      if (templatePrefix != null) {
        try {
          ZenCodingTemplate.expand(templatePrefix, callback, generator, Collections.emptyList(),
                                   expandPrimitiveAbbreviations, 0);
          TemplateImpl template = callback.getGeneratedTemplate();
          String templateText = template != null ? template.getTemplateText() : null;
          if (!StringUtil.isEmpty(templateText)) {
            return template.isToReformat() ? reformatTemplateText(file, templateText) : templateText;
          }
        }
        catch (EmmetException e) {
          return e.getMessage();
        }

      }
    }
    return null;
  }

  public static void addEmmetPreviewListeners(@NotNull final Editor editor,
                                              @NotNull final PsiFile file,
                                              final boolean expandPrimitiveAbbreviations) {
    editor.getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(@NotNull DocumentEvent e) {
        EmmetPreviewHint existingHint = EmmetPreviewHint.getExistingHint(editor);
        if (existingHint != null) {
          existingHint.updateText(new TemplateTextProducer(editor, file, expandPrimitiveAbbreviations));
        }
        else {
          e.getDocument().removeDocumentListener(this);
        }
      }
    });

    editor.getCaretModel().addCaretListener(new CaretListener() {
      @Override
      public void caretPositionChanged(@NotNull CaretEvent e) {
        EmmetPreviewHint existingHint = EmmetPreviewHint.getExistingHint(e.getEditor());
        if (existingHint != null) {
          existingHint.updateText(new TemplateTextProducer(editor, file, expandPrimitiveAbbreviations));
        }
        else {
          e.getEditor().getCaretModel().removeCaretListener(this);
        }
      }
    });
  }

  private static String reformatTemplateText(@NotNull final PsiFile file, @NotNull String templateText) {
    final PsiFile copy = PsiFileFactory.getInstance(file.getProject()).createFileFromText(file.getName(), file.getFileType(), templateText);
    VirtualFile vFile = copy.getVirtualFile();
    if (vFile != null) {
      vFile.putUserData(UndoConstants.DONT_RECORD_UNDO, Boolean.TRUE);
    }
    ApplicationManager.getApplication().runWriteAction(() -> CommandProcessor.getInstance().runUndoTransparentAction(() -> CodeStyleManager.getInstance(file.getProject()).reformat(copy)));
    return copy.getText();
  }

  private static class TemplateTextProducer implements Producer<String> {
    @NotNull private final Editor myEditor;
    @NotNull private final PsiFile myFile;
    private final boolean myExpandPrimitiveAbbreviations;

    public TemplateTextProducer(@NotNull Editor editor, @NotNull PsiFile file, boolean expandPrimitiveAbbreviations) {
      myEditor = editor;
      myFile = file;
      myExpandPrimitiveAbbreviations = expandPrimitiveAbbreviations;
    }

    @Nullable
    @Override
    public String produce() {
      return calculateTemplateText(myEditor, myFile, myExpandPrimitiveAbbreviations);
    }
  }
}
