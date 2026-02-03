// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project;

import com.intellij.execution.filters.ConsoleFilterProvider;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.filters.RegexpFilter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MavenConsoleFilterProvider implements ConsoleFilterProvider {
  private static final String MAVEN_PREFIX = "(?:^|(?:\\[\\w+\\]\\s*)( /)?)";
  private static final String CONSOLE_FILTER_REGEXP =
    MAVEN_PREFIX + RegexpFilter.FILE_PATH_MACROS + ":\\[" + RegexpFilter.LINE_MACROS + "," + RegexpFilter.COLUMN_MACROS + "]";
  private static final String CONSOLE_FILTER_REGEXP_KT =
    MAVEN_PREFIX + RegexpFilter.FILE_PATH_MACROS + ": \\(" + RegexpFilter.LINE_MACROS + ", " + RegexpFilter.COLUMN_MACROS + "\\)";


  @Override
  public Filter @NotNull [] getDefaultFilters(@NotNull Project project) {
    return new Filter[]{
      new RegexpFilterMaven(project, CONSOLE_FILTER_REGEXP),
      new RegexpFilterMaven(project, CONSOLE_FILTER_REGEXP_KT),
      new MavenGroovyConsoleFilter(project),
      new MavenScalaConsoleFilter(project),
      new MavenTestConsoleFilter()
    };
  }

  private static class RegexpFilterMaven extends RegexpFilter {
    private final Project project;

    private RegexpFilterMaven(Project project, String expression) {
      super(project, expression);
      this.project = project;
    }

    @Override
    protected @Nullable HyperlinkInfo createOpenFileHyperlink(String fileName, int line, int column) {
      HyperlinkInfo res = super.createOpenFileHyperlink(fileName, line, column);
      if (res == null && fileName.startsWith("\\") && SystemInfo.isWindows) {
        // Maven cut prefix 'C:\' from paths on Windows
        VirtualFile[] roots = ProjectRootManager.getInstance(project).getContentRoots();
        if (roots.length > 0) {
          String projectPath = roots[0].getPath();
          if (projectPath.matches("[A-Z]:[\\\\/].+")) {
            res = super.createOpenFileHyperlink(projectPath.charAt(0) + ":" + fileName, line, column);
          }
        }
      }

      return res;
    }
  }
}
