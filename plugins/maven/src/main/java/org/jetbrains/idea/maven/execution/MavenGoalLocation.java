/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.execution;

import com.intellij.execution.PsiLocation;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

import java.util.List;

public class MavenGoalLocation extends PsiLocation<PsiFile> {
  private final List<String> myGoals;

  public MavenGoalLocation(Project p, PsiFile file, List<String> goals) {
    super(p, file);
    myGoals = goals;
  }

  public List<String> getGoals() {
    return myGoals;
  }
}
