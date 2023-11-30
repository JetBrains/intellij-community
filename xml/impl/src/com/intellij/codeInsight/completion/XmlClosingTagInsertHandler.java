// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import org.jetbrains.annotations.NotNull;

public final class XmlClosingTagInsertHandler implements InsertHandler<LookupElement> {
  public static final XmlClosingTagInsertHandler INSTANCE = new XmlClosingTagInsertHandler();

  private XmlClosingTagInsertHandler(){}

  @Override
  public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item) {
    Editor editor = context.getEditor();
    Document document = editor.getDocument();
    Project project = context.getProject();
    if (item instanceof LookupElementDecorator) {
      ((LookupElementDecorator<?>)item).getDelegate().handleInsert(context);
    }
    PsiDocumentManager.getInstance(project).commitDocument(document);
    int lineOffset = document.getLineStartOffset(document.getLineNumber(editor.getCaretModel().getOffset()));
    CodeStyleManager.getInstance(project).adjustLineIndent(document, lineOffset);
  }
}
