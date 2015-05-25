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

/**
 * @author yole
 */
public class PythonTracebackFilter implements Filter {
  private final Project myProject;
  private final String myWorkingDirectory;

  public PythonTracebackFilter(final Project project) {
    myProject = project;
    myWorkingDirectory = null;
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
}
