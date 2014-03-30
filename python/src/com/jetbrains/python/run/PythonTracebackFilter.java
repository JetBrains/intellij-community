/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author yole
 */
public class PythonTracebackFilter implements Filter {
  private final Project myProject;
  private final String myWorkingDirectory;
  private final Pattern myMatchingPattern = Pattern.compile("File \"([^\"]+)\", line (\\d+)");

  public PythonTracebackFilter(Project project) {
    myProject = project;
    myWorkingDirectory = null;
  }

  public PythonTracebackFilter(Project project, @Nullable String workingDirectory) {
    myProject = project;
    myWorkingDirectory = workingDirectory;
  }

  public Result applyFilter(String line, int entireLength) {
    //   File "C:\Progs\Crack\psidc\scummdc.py", line 72, in ?
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
        int startPos = line.indexOf('\"') + 1;
        int endPos = line.indexOf('\"', startPos);
        return new Result(startPos + textStartOffset, endPos + textStartOffset, hyperlink);
      }
    }
    return null;
  }
}
