/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.project.Project;

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
