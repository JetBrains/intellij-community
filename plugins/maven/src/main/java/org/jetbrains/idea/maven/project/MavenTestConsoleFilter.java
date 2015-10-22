/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.project;

import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.ide.actions.ShowFilePathAction;
import com.intellij.openapi.project.Project;
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
  public Result applyFilter(String line, int entireLength) {
    Matcher matcherReportDir = REPORT_DIR_PATTERN.matcher(line);
    if (matcherReportDir.matches()) {
      final String path = matcherReportDir.group(1);

      return new Result(entireLength - line.length() + matcherReportDir.start(1), entireLength - line.length() + matcherReportDir.end(1),
                        new HyperlinkInfo() {
                          @Override
                          public void navigate(Project project) {
                            File f = new File(path);
                            if (f.isDirectory()) {
                              ShowFilePathAction.openDirectory(f);
                            }
                          }
                        });
    }
    return null;
  }
}
