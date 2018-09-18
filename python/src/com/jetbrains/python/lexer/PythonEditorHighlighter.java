// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.lexer;

import com.intellij.lexer.LayeredLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterClient;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User : catherine
 */
public class PythonEditorHighlighter extends LexerEditorHighlighter {

  public PythonEditorHighlighter(@NotNull EditorColorsScheme scheme, @Nullable Project project, @Nullable VirtualFile file) {
    super(SyntaxHighlighterFactory.getSyntaxHighlighter(file != null ? file.getFileType() : PythonFileType.INSTANCE,
                                                               project,
                                                               file),
          scheme);
  }

  private Boolean hadUnicodeImport = false;

  public static final Key<Boolean> KEY = new Key<>("python.future.import");
  @Override
  public void documentChanged(@NotNull DocumentEvent e) {
    synchronized (this) {
      final Document document = e.getDocument();
      Lexer l = getLexer();
      // if the document been changed before "from __future__ import unicode_literals"
      // we should update the whole document
      if (l instanceof LayeredLexer) {
        Lexer delegate = ((LayeredLexer)l).getDelegate();
        int offset = e.getOffset();
        int lineNumber = document.getLineNumber(offset);
        TextRange tr = new TextRange(document.getLineStartOffset(lineNumber), document.getLineEndOffset(lineNumber));
        document.putUserData(KEY, document.getText(tr).indexOf(PyNames.UNICODE_LITERALS) == -1);
        Boolean hasUnicodeImport = document.getUserData(KEY);
        if (delegate instanceof PythonHighlightingLexer &&
            (((PythonHighlightingLexer)delegate).getImportOffset() > e.getOffset()
             || hasUnicodeImport != hadUnicodeImport)) {
          ((PythonHighlightingLexer)delegate).clearState(e.getDocument().getTextLength());
          setText(document.getCharsSequence());
        }
        else super.documentChanged(e);
      }
      else super.documentChanged(e);
    }
  }

  @Override
  public void beforeDocumentChange(@NotNull DocumentEvent e) {
    final Document document = e.getDocument();
    hadUnicodeImport = document.getUserData(KEY);
  }

  @Override
  public void setEditor(@NotNull HighlighterClient editor) {
    Lexer l = getLexer();
    if (l instanceof LayeredLexer) {
      editor.getDocument().putUserData(KEY, editor.getDocument().getText().indexOf(PyNames.UNICODE_LITERALS) == -1);
    }
    super.setEditor(editor);
  }
}
