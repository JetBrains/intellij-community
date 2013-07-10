package org.jetbrains.idea.maven.project;

import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.OpenFileHyperlinkInfo;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Sergey Evdokimov
 */
public abstract class AbstractMavenConsoleFilter implements Filter {

  private final Pattern myPattern;

  private final Project myProject;

  public AbstractMavenConsoleFilter(Project project, Pattern pattern) {
    myProject = project;
    myPattern = pattern;
  }

  protected abstract boolean lightCheck(String line);

  @Nullable
  @Override
  public Result applyFilter(String line, int entireLength) {
    if (!lightCheck(line)) return null;

    Matcher matcher = myPattern.matcher(line);
    if (!matcher.matches()) return null;

    String path = matcher.group(1);

    VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
    if (file == null) {
      if (SystemInfo.isWindows && path.matches("/[A-Z]:/.+")) {
        file = LocalFileSystem.getInstance().findFileByPath(path.substring(1));
      }
      if (file == null) return null;
    }

    int lineNumber = Integer.parseInt(matcher.group(2)) - 1;
    if (lineNumber < 0) {
      lineNumber = -1;
    }

    TextAttributes attr = createCompilationErrorAttr();

    return new Result(entireLength - line.length() + matcher.start(1), entireLength - line.length() + matcher.end(1),
                      new OpenFileHyperlinkInfo(myProject, file, lineNumber), attr);
  }

  private static TextAttributes createCompilationErrorAttr() {
    TextAttributes attr = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(CodeInsightColors.HYPERLINK_ATTRIBUTES).clone();
    attr.setForegroundColor(JBColor.RED);
    attr.setEffectColor(JBColor.RED);
    attr.setEffectType(EffectType.LINE_UNDERSCORE);
    attr.setFontType(Font.PLAIN);
    return attr;
  }

}
