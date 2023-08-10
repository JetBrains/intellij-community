// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project;

import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.ide.actions.RevealFileAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MavenTestConsoleFilter implements Filter {
  private static final Pattern REPORT_DIR_PATTERN = Pattern
    .compile("\\s*(?:\\[INFO\\] +Surefire report directory:|\\[ERROR\\] Please refer to) +(.+?)(?: for the individual test results.)?\\s*");

  public MavenTestConsoleFilter() {
  }

  @Nullable
  @Override
  public Result applyFilter(@NotNull String line, int entireLength) {
    Matcher matcherReportDir = REPORT_DIR_PATTERN.matcher(line);
    if (matcherReportDir.matches()) {
      final String path = matcherReportDir.group(1);

      return new Result(entireLength - line.length() + matcherReportDir.start(1), entireLength - line.length() + matcherReportDir.end(1),
                        new HyperlinkInfo() {
                          @Override
                          public void navigate(@NotNull Project project) {
                            File f = new File(path);
                            if (f.isDirectory()) {
                              RevealFileAction.openDirectory(f);
                            }
                          }
                        });
    }
    return null;
  }
}
