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
import org.jetbrains.plugins.textmate.Constants;
import org.jetbrains.plugins.textmate.TextMateService;
import org.jetbrains.plugins.textmate.language.TextMateFileType;
import org.jetbrains.plugins.textmate.language.preferences.TextMateShellVariable;

public class TextMateCommentProvider implements MultipleLangCommentProvider, Commenter {
  @Nullable
  @Override
  public Commenter getLineCommenter(PsiFile file, Editor editor, Language lineStartLanguage, Language lineEndLanguage) {
    TextMateCommentPair lineCommentPair = null;
    TextMateCommentPair blockCommentPair = null;
    int index = 1;
    while (blockCommentPair == null || lineCommentPair == null) {
      String variableSuffix = index > 1 ? "_" + index : "";
      TextMateShellVariable start = TextMateService.getInstance().getVariable(Constants.COMMENT_START_VARIABLE + variableSuffix, (EditorEx)editor);
      TextMateShellVariable end = TextMateService.getInstance().getVariable(Constants.COMMENT_END_VARIABLE + variableSuffix, (EditorEx)editor);
      index++;

      if (start == null) break;
      if ((end == null || !end.scopeName.equals(start.scopeName)) && lineCommentPair == null) {
        lineCommentPair = new TextMateCommentPair(index, start.value, null);
      }
      if ((end != null && end.scopeName.equals(start.scopeName)) && blockCommentPair == null) {
        blockCommentPair = new TextMateCommentPair(index, start.value, end.value);
      }
    }

    return lineCommentPair != null || blockCommentPair != null ? new MyCommenter(lineCommentPair, blockCommentPair) : null;
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

  private static class MyCommenter implements Commenter {
    @Nullable
    private final TextMateCommentPair myLineCommentPair;
    @Nullable
    private final TextMateCommentPair myBlockCommentPair;

    private MyCommenter(@Nullable TextMateCommentPair lineCommentPair, @Nullable TextMateCommentPair blockCommentPair) {
      myLineCommentPair = lineCommentPair;
      myBlockCommentPair = blockCommentPair;
    }

    @Nullable
    @Override
    public String getLineCommentPrefix() {
      return myLineCommentPair != null ? myLineCommentPair.startComment : null;
    }

    @Nullable
    @Override
    public String getBlockCommentPrefix() {
      return myBlockCommentPair != null ? myBlockCommentPair.startComment : null;
    }

    @Nullable
    @Override
    public String getBlockCommentSuffix() {
      return myBlockCommentPair != null ? myBlockCommentPair.endComment : null;
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


  public static class TextMateCommentPair implements Comparable<TextMateCommentPair> {
    private final int myIndex;
    @NotNull public final String startComment;
    @Nullable public final String endComment;

    public TextMateCommentPair(int index, @NotNull String startComment, @Nullable String endComment) {
      myIndex = index;
      this.startComment = startComment;
      this.endComment = endComment;
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      TextMateCommentPair pair = (TextMateCommentPair)o;

      if (endComment != null ? !endComment.equals(pair.endComment) : pair.endComment != null) return false;
      if (!startComment.equals(pair.startComment)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = startComment.hashCode();
      result = 31 * result + (endComment != null ? endComment.hashCode() : 0);
      return result;
    }

    @Override
    public String toString() {
      return "TextMateCommentPair{" +
             "startComment='" + startComment + '\'' +
             ", endComment='" + endComment + '\'' +
             '}';
    }

    @Override
    public int compareTo(TextMateCommentPair o) {
      return myIndex - o.myIndex;
    }
  }
}
