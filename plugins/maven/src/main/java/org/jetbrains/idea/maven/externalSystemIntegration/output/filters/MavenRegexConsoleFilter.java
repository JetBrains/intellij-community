// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.filters;

import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.filters.RegexpFilter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

public class MavenRegexConsoleFilter extends RegexpFilter {
  private static final String KOTLIN_FILTER_REGEXP =
    "(?:^|(?:\\[\\w+\\]\\s*)( /)?)" +
    RegexpFilter.FILE_PATH_MACROS + ":\\s?\\(" + RegexpFilter.LINE_MACROS + ",\\s?" + RegexpFilter.COLUMN_MACROS + "\\)";

  private static final String JAVA_FILTER_REGEXP =
    "(?:^|(?:\\[\\w+\\]\\s*)( /)?)" +
    RegexpFilter.FILE_PATH_MACROS + ":\\[" + RegexpFilter.LINE_MACROS + ",\\s?" + RegexpFilter.COLUMN_MACROS + "]";

  private final Project myProject;

  private MavenRegexConsoleFilter(Project project, String regexp) {
    super(project, regexp);
    myProject = project;
  }

  @Nullable
  @Override
  protected HyperlinkInfo createOpenFileHyperlink(String fileName, int line, int column) {
    HyperlinkInfo res = super.createOpenFileHyperlink(fileName, line, column);
    if (res == null && fileName.startsWith("\\") && SystemInfo.isWindows) {
      // Maven cut prefix 'C:\' from paths on Windows
      VirtualFile[] roots = ProjectRootManager.getInstance(myProject).getContentRoots();
      if (roots.length > 0) {
        String projectPath = roots[0].getPath();
        if (projectPath.matches("[A-Z]:[\\\\/].+")) {
          res = super.createOpenFileHyperlink(projectPath.charAt(0) + ":" + fileName, line, column);
        }
      }
    }

    return res;
  }

  public static MavenRegexConsoleFilter kotlinFilter(Project project) {
    return new MavenRegexConsoleFilter(project, KOTLIN_FILTER_REGEXP);
  }

  public static MavenRegexConsoleFilter javaFilter(Project project) {
    return new MavenRegexConsoleFilter(project, JAVA_FILTER_REGEXP);
  }
}
