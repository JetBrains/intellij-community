// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.backend.rename;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

@ApiStatus.Internal
public final class TextOccurrencesRenamer {
  private final Editor myEditor;
  private final String myOldName;
  private final List<TextRange> myOccurrences;
  private final TextRange myOccurrenceAtCaret;
  private final long myInitialModificationStamp;

  TextOccurrencesRenamer(@NotNull Editor editor, @NotNull String occurrenceText,
                         @NotNull Collection<TextRange> occurrences, @NotNull TextRange occurrenceAtCaret) {
    myEditor = editor;
    myOldName = occurrenceText;
    myOccurrences = new ArrayList<>(occurrences);
    myOccurrences.sort(Comparator.comparingInt(TextRange::getStartOffset));
    myOccurrenceAtCaret = occurrenceAtCaret;
    myInitialModificationStamp = myEditor.getDocument().getModificationStamp();
  }

  public @NotNull String getOldName() {
    return myOldName;
  }

  public @NotNull Editor getEditor() {
    return myEditor;
  }

  public void renameTo(@NotNull String newName) {
    Document document = myEditor.getDocument();
    CharSequence documentText = document.getImmutableCharSequence();
    if (document.getModificationStamp() != myInitialModificationStamp || !isValid(documentText)) {
      return;
    }
    String result = getNewDocumentText(documentText, newName);
    WriteAction.run(() -> {
      int prevCount = (int)myOccurrences.stream()
        .filter(range -> range.getStartOffset() < myOccurrenceAtCaret.getStartOffset())
        .count();
      Caret caret = myEditor.getCaretModel().getPrimaryCaret();
      int newCaretOffset = caret.getOffset() + (newName.length() - myOldName.length()) * prevCount;
      CommandProcessor.getInstance().executeCommand(myEditor.getProject(), () -> {
        document.setText(result);
        caret.moveToOffset(newCaretOffset);
      }, null, null, document);
    });
  }

  private @NotNull String getNewDocumentText(@NotNull CharSequence documentText, @NotNull String newName) {
    TextRange prevOccurrence = null;
    StringBuilder result = new StringBuilder(documentText.length() + (newName.length() - myOldName.length()) * myOccurrences.size());
    for (TextRange occurrence : myOccurrences) {
      result.append(documentText.subSequence(prevOccurrence != null ? prevOccurrence.getEndOffset() : 0, occurrence.getStartOffset()));
      result.append(newName);
      prevOccurrence = occurrence;
    }
    result.append(documentText.subSequence(prevOccurrence != null ? prevOccurrence.getEndOffset() : 0, documentText.length()));
    return result.toString();
  }

  private boolean isValid(@NotNull CharSequence text) {
    for (TextRange occurrence : myOccurrences) {
      if (!StringUtil.startsWith(text, occurrence.getStartOffset(), myOldName)) {
        return false;
      }
    }
    return true;
  }
}
