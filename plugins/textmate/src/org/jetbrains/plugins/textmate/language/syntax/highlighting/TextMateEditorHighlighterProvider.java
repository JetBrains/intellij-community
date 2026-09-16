package org.jetbrains.plugins.textmate.language.syntax.highlighting;

import com.intellij.lexer.RestartableLexer;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.util.DataStorage;
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileTypes.EditorHighlighterProvider;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateLexerDataStorage;

public class TextMateEditorHighlighterProvider implements EditorHighlighterProvider {
  @Override
  public EditorHighlighter getEditorHighlighter(@Nullable Project project,
                                                @NotNull FileType fileType,
                                                @Nullable VirtualFile virtualFile,
                                                @NotNull EditorColorsScheme colors) {
    return new TextMateLexerEditorHighlighter(SyntaxHighlighterFactory.getSyntaxHighlighter(fileType, project, virtualFile), colors);
  }

  private static final class TextMateLexerEditorHighlighter extends LexerEditorHighlighter {
    private TextMateLexerEditorHighlighter(@Nullable SyntaxHighlighter highlighter, @NotNull EditorColorsScheme colors) {
      super(highlighter != null ? highlighter : new TextMateHighlighter(null), colors);
    }

    /**
     * {@link TextMateLexerDataStorage} needs a {@link RestartableLexer}, because it gives all its 16 state
     * bits to the state. The factory returns a plain highlighter when no grammar matches the file name,
     * and that one keeps the default storage.
     */
    @Override
    protected @NotNull DataStorage createStorage() {
      return getLexer() instanceof RestartableLexer ? new TextMateLexerDataStorage() : super.createStorage();
    }
  }
}
