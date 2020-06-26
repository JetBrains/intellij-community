// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.function.Supplier;

public final class EmmetPreviewUtil {
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

  private static class TemplateTextProducer implements Supplier<String> {
    @NotNull private final Editor myEditor;
    @NotNull private final PsiFile myFile;
    private final boolean myExpandPrimitiveAbbreviations;

    TemplateTextProducer(@NotNull Editor editor, @NotNull PsiFile file, boolean expandPrimitiveAbbreviations) {
      myEditor = editor;
      myFile = file;
      myExpandPrimitiveAbbreviations = expandPrimitiveAbbreviations;
    }

    @Nullable
    @Override
    public String get() {
      return calculateTemplateText(myEditor, myFile, myExpandPrimitiveAbbreviations);
    }
  }
}
