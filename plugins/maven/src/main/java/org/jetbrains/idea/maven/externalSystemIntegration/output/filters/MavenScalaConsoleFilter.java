// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.filters;

import com.intellij.openapi.project.Project;
import org.jetbrains.idea.maven.project.AbstractMavenConsoleFilter;

import java.util.regex.Pattern;

/**
 * @author Sergey Evdokimov
 */
public class MavenScalaConsoleFilter extends AbstractMavenConsoleFilter {

  private static final Pattern PATTERN = Pattern.compile("\\[ERROR\\] (\\S.+\\.scala): ?(-?\\d{1,5}): .+", Pattern.DOTALL);

  public MavenScalaConsoleFilter(Project project) {
    super(project, PATTERN);
  }

  @Override
  protected boolean lightCheck(String line) {
    return line.startsWith("[ERROR] ") && line.contains(".scala:");
  }
}
