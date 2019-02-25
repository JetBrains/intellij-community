// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.filters;

import com.intellij.openapi.project.Project;
import org.jetbrains.idea.maven.project.AbstractMavenConsoleFilter;

import java.util.regex.Pattern;

/**
 * @author Sergey Evdokimov
 */
public class MavenGroovyConsoleFilter extends AbstractMavenConsoleFilter {

  // Example of gmaven error line:
  // [ERROR] /home/user/ideaProjects/simpleMaven/src/main/groovy/com/A.groovy: 17: [Static type checking] - Cannot assign value of type java.lang.String to variable of type int
  private static final Pattern PATTERN = Pattern.compile("\\[ERROR\\] (\\S.+\\.groovy): (-?\\d{1,5}): .+", Pattern.DOTALL);

  public MavenGroovyConsoleFilter(Project project) {
    super(project, PATTERN);
  }

  @Override
  protected boolean lightCheck(String line) {
    return line.startsWith("[ERROR] ") && line.contains(".groovy: ");
  }

}
