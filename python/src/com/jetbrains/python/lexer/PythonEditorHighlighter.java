package com.jetbrains.python.lexer;

import com.intellij.lexer.LayeredLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.PythonFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User : catherine
 */
public class PythonEditorHighlighter extends LexerEditorHighlighter {

  public PythonEditorHighlighter(@NotNull EditorColorsScheme scheme, @Nullable Project project, @Nullable VirtualFile file) {
    super(SyntaxHighlighter.PROVIDER.create(file != null ? file.getFileType() : PythonFileType.INSTANCE,
                                            project,
                                            file),
          scheme);
  }

  @Override
  public void documentChanged(DocumentEvent e) {
    synchronized (this) {
      final Document document = e.getDocument();
      Lexer l = getLexer();
      // if the document been changed before "from __future__ import unicode_literals"
      // we should update the whole document
      if (l instanceof LayeredLexer) {
        Lexer delegate = ((LayeredLexer)l).getDelegate();
        if (delegate instanceof PythonHighlightingLexer &&
            (((PythonHighlightingLexer)delegate).getImportOffset() > e.getOffset()
             || ((PythonHighlightingLexer)delegate).getImportOffset() == -1)) {

          ((PythonHighlightingLexer)delegate).clearState(e.getDocument().getTextLength());
          setText(document.getCharsSequence());
        }
        else super.documentChanged(e);
      }
      else super.documentChanged(e);
    }
  }
}
