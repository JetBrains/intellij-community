/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.inspections.PyEncodingUtil;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * add missing encoding declaration
 * # -*- coding: <encoding name> -*-
 * to the source file
 * <p/>
 * User: catherine
 */
public class AddEncodingQuickFix implements LocalQuickFix {

  private final String myDefaultEncoding;
  private final int myEncodingFormatIndex;

  public AddEncodingQuickFix(String defaultEncoding, int encodingFormatIndex) {
    myDefaultEncoding = defaultEncoding;
    myEncodingFormatIndex = encodingFormatIndex;
  }

  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.add.encoding");
  }

  @NonNls
  @NotNull
  public String getFamilyName() {
    return getName();
  }

  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    final PsiFile file = element.getContainingFile();
    if (file == null) return;
    PsiElement firstLine = file.getFirstChild();
    if (firstLine instanceof PsiComment && firstLine.getText().startsWith("#!")) {
      firstLine = firstLine.getNextSibling();
    }
    final String commentText = String.format(PyEncodingUtil.ENCODING_FORMAT_PATTERN[myEncodingFormatIndex], myDefaultEncoding);
    final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    PsiComment encodingComment = elementGenerator.createFromText(LanguageLevel.forElement(file), PsiComment.class, commentText);
    encodingComment = (PsiComment)file.addBefore(encodingComment, firstLine);

    final FileEditor fileEditor = FileEditorManager.getInstance(project).getSelectedEditor(element.getContainingFile().getVirtualFile());
    if (fileEditor instanceof TextEditor) {
      final Editor editor = ((TextEditor)fileEditor).getEditor();
      final Document document = editor.getDocument();
      final int insertedLineNumber = document.getLineNumber(encodingComment.getTextOffset());
      if (insertedLineNumber == document.getLineCount() - 1) {
        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document);
        document.insertString(document.getLineEndOffset(insertedLineNumber), "\n");
      }
      editor.getCaretModel().moveToLogicalPosition(new LogicalPosition(insertedLineNumber + 1, 0));
    }
  }
}
