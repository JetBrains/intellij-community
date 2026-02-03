package org.jetbrains.plugins.textmate.editor;

import com.intellij.lang.Commenter;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.templateLanguages.MultipleLangCommentProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.TextMateFileType;
import org.jetbrains.plugins.textmate.TextMateService;
import org.jetbrains.plugins.textmate.language.PreferencesReadUtil;
import org.jetbrains.plugins.textmate.language.TextMateBlockCommentPair;
import org.jetbrains.plugins.textmate.language.TextMateCommentPrefixes;
import org.jetbrains.plugins.textmate.language.preferences.ShellVariablesRegistry;
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateScope;

public class TextMateCommentProvider implements MultipleLangCommentProvider, Commenter {
  @Override
  public @Nullable Commenter getLineCommenter(@NotNull PsiFile file,
                                              @NotNull Editor editor,
                                              @NotNull Language lineStartLanguage,
                                              @NotNull Language lineEndLanguage) {
    final TextMateScope actualScope = TextMateEditorUtils.getCurrentScopeSelector((EditorEx)editor);
    if (actualScope == null) {
      return null;
    }

    ShellVariablesRegistry registry = TextMateService.getInstance().getShellVariableRegistry();
    final TextMateCommentPrefixes prefixes = PreferencesReadUtil.readCommentPrefixes(registry, actualScope);

    return (prefixes.getBlockCommentPair() != null || prefixes.getLineCommentPrefix() != null) ? new MyCommenter(prefixes) : null;
  }

  @Override
  public boolean canProcess(@NotNull PsiFile file, @NotNull FileViewProvider viewProvider) {
    return file.getFileType() == TextMateFileType.INSTANCE;
  }

  @Override
  public @Nullable String getLineCommentPrefix() {
    return null;
  }

  @Override
  public @Nullable String getBlockCommentPrefix() {
    return "";
  }

  @Override
  public @Nullable String getBlockCommentSuffix() {
    return "";
  }

  @Override
  public @Nullable String getCommentedBlockCommentPrefix() {
    return null;
  }

  @Override
  public @Nullable String getCommentedBlockCommentSuffix() {
    return null;
  }

  private static final class MyCommenter implements Commenter {
    final @Nullable String myLinePrefix;

    final @Nullable TextMateBlockCommentPair myBlockPrefixes;

    private MyCommenter(@NotNull TextMateCommentPrefixes prefixes) {
      myLinePrefix = prefixes.getLineCommentPrefix();
      myBlockPrefixes = prefixes.getBlockCommentPair();
    }

    @Override
    public @Nullable String getLineCommentPrefix() {
      return myLinePrefix;
    }

    @Override
    public @Nullable String getBlockCommentPrefix() {
      return myBlockPrefixes != null ? myBlockPrefixes.getPrefix() : null;
    }

    @Override
    public @Nullable String getBlockCommentSuffix() {
      return myBlockPrefixes != null ? myBlockPrefixes.getSuffix() : null;
    }

    @Override
    public @Nullable String getCommentedBlockCommentPrefix() {
      return null;
    }

    @Override
    public @Nullable String getCommentedBlockCommentSuffix() {
      return null;
    }
  }
}
