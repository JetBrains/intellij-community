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
  @Nullable
  @Override
  public Commenter getLineCommenter(PsiFile file, Editor editor, Language lineStartLanguage, Language lineEndLanguage) {
    final TextMateScope actualScope = TextMateEditorUtils.getCurrentScopeSelector((EditorEx)editor);
    if (actualScope == null) {
      return null;
    }

    ShellVariablesRegistry registry = TextMateService.getInstance().getShellVariableRegistry();
    final TextMateCommentPrefixes prefixes = PreferencesReadUtil.readCommentPrefixes(registry, actualScope);

    return (prefixes.getBlockCommentPair() != null || prefixes.getLineCommentPrefix() != null) ? new MyCommenter(prefixes) : null;
  }

  @Override
  public boolean canProcess(@NotNull PsiFile file, FileViewProvider viewProvider) {
    return file.getFileType() == TextMateFileType.INSTANCE;
  }

  @Nullable
  @Override
  public String getLineCommentPrefix() {
    return null;
  }

  @Nullable
  @Override
  public String getBlockCommentPrefix() {
    return "";
  }

  @Nullable
  @Override
  public String getBlockCommentSuffix() {
    return "";
  }

  @Nullable
  @Override
  public String getCommentedBlockCommentPrefix() {
    return null;
  }

  @Nullable
  @Override
  public String getCommentedBlockCommentSuffix() {
    return null;
  }

  private static final class MyCommenter implements Commenter {
    @Nullable
    final String myLinePrefix;

    @Nullable
    final TextMateBlockCommentPair myBlockPrefixes;

    private MyCommenter(@NotNull TextMateCommentPrefixes prefixes) {
      myLinePrefix = prefixes.getLineCommentPrefix();
      myBlockPrefixes = prefixes.getBlockCommentPair();
    }

    @Nullable
    @Override
    public String getLineCommentPrefix() {
      return myLinePrefix;
    }

    @Nullable
    @Override
    public String getBlockCommentPrefix() {
      return myBlockPrefixes != null ? myBlockPrefixes.getPrefix() : null;
    }

    @Nullable
    @Override
    public String getBlockCommentSuffix() {
      return myBlockPrefixes != null ? myBlockPrefixes.getSuffix() : null;
    }

    @Nullable
    @Override
    public String getCommentedBlockCommentPrefix() {
      return null;
    }

    @Nullable
    @Override
    public String getCommentedBlockCommentSuffix() {
      return null;
    }
  }
}
