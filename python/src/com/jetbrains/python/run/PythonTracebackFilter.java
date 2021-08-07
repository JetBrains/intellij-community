// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run;

import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.OpenFileHyperlinkInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.traceBackParsers.LinkInTrace;
import com.jetbrains.python.traceBackParsers.TraceBackParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;


public class PythonTracebackFilter implements Filter {
  private final Project myProject;
  private final String myWorkingDirectory;

  public PythonTracebackFilter(final Project project) {
    myProject = project;
    myWorkingDirectory = project.getBasePath();
  }

  public PythonTracebackFilter(final Project project, @Nullable final String workingDirectory) {
    myProject = project;
    myWorkingDirectory = workingDirectory;
  }

  @Override
  @Nullable
  public final Result applyFilter(@NotNull final String line, final int entireLength) {

    for (final TraceBackParser parser : TraceBackParser.PARSERS) {
      final LinkInTrace linkInTrace = parser.findLinkInTrace(line);
      if (linkInTrace == null) {
        continue;
      }
      final int lineNumber = linkInTrace.getLineNumber();
      final VirtualFile vFile = findFileByName(linkInTrace.getFileName());

      if (vFile != null) {
        if (!vFile.isDirectory()) {
          var extension = vFile.getExtension();
          if (extension != null && !extension.equals("py")) {
            return null;
          }
        }
        final OpenFileHyperlinkInfo hyperlink = new OpenFileHyperlinkInfo(myProject, vFile, lineNumber - 1);
        final int textStartOffset = entireLength - line.length();
        final int startPos = linkInTrace.getStartPos();
        final int endPos = linkInTrace.getEndPos();
        return new Result(startPos + textStartOffset, endPos + textStartOffset, hyperlink);
      }
    }
    return null;
  }

  @Nullable
  protected VirtualFile findFileByName(@NotNull final String fileName) {
    VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(fileName);
    if (vFile == null && !StringUtil.isEmptyOrSpaces(myWorkingDirectory)) {
      vFile = LocalFileSystem.getInstance().findFileByIoFile(new File(myWorkingDirectory, fileName));
    }
    return vFile;
  }

  protected Project getProject() {
    return myProject;
  }
}
