// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.backend.shellcheck;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.sh.ShBundle;
import com.intellij.sh.shellcheck.ShShellcheckUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.stream.Stream;

public class ShQuickFixIntention implements IntentionAction {
  @SafeFieldForPreview private final ShShellcheckExternalAnnotator.Fix fix;
  private final long timestamp;
  private final @IntentionName String message;

  public ShQuickFixIntention(@IntentionName String message, ShShellcheckExternalAnnotator.Fix fix, long timestamp) {
    this.timestamp = timestamp;
    this.message = message;
    this.fix = fix;
  }

  @Override
  public @NotNull String getFamilyName() {
    return ShBundle.message("sh.shell.script");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    return timestamp == psiFile.getModificationStamp();
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  @Override
  public @NotNull String getText() {
    return message;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
    if (editor == null) return;
    class Replacement {
      final String replacement;
      final int startOffset;
      final int endOffset;

      Replacement(int startOffset, int endOffset, String replacement) {
        this.replacement = replacement;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
      }
    }
    Document document = editor.getDocument();
    Stream.of(fix.replacements)
      .map(it -> {
        int startOffset = calcOffset(document, it.line, it.column);
        int endOffset = calcOffset(document, it.endLine, it.endColumn);
        return new Replacement(startOffset, endOffset, it.replacement);
      })
      // applying replacements from right to left not to break offsets in document
      .sorted(Comparator.comparingInt(it -> -it.endOffset))
      .forEach(it -> document.replaceString(it.startOffset, it.endOffset, it.replacement));
  }

  private static int calcOffset(Document document, int line, int column) {
    return ShShellcheckUtil.calcOffset(document.getCharsSequence(),
                                       document.getLineStartOffset(line - 1),
                                       column);
  }
}