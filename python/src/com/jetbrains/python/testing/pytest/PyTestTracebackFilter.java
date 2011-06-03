package com.jetbrains.python.testing.pytest;

import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.OpenFileHyperlinkInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User : catherine
 */
public class PyTestTracebackFilter implements Filter {
  private final Project myProject;
  private final String myWorkingDirectory;
  private final Pattern myMatchingPattern = Pattern.compile("([^\"]+):(\\d+)");

  public PyTestTracebackFilter(Project project, @Nullable String workingDirectory) {
    myProject = project;
    myWorkingDirectory = workingDirectory;
  }

  public Result applyFilter(String line, int entireLength) {
    //   C:\Progs\Crack\psidc\scummdc.py:72: AssertionError
    Matcher matcher = myMatchingPattern.matcher(line);
    if (matcher.find()) {
      String fileName = matcher.group(1).replace('\\', '/');
      int lineNumber = Integer.parseInt(matcher.group(2));
      VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(fileName);
      if (vFile == null && !StringUtil.isEmptyOrSpaces(myWorkingDirectory)) {
        vFile = LocalFileSystem.getInstance().findFileByIoFile(new File(myWorkingDirectory, fileName)); 
      }
      
      if (vFile != null) {
        OpenFileHyperlinkInfo hyperlink = new OpenFileHyperlinkInfo(myProject, vFile, lineNumber - 1);
        final int textStartOffset = entireLength - line.length();
        int startPos = 0;
        int endPos = line.lastIndexOf(':');
        return new Result(startPos + textStartOffset, endPos + textStartOffset, hyperlink);
      }
    }
    return null;
  }
}
