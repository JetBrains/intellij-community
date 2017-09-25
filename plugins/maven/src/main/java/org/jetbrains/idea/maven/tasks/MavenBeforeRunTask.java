/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.tasks;

import com.intellij.execution.BeforeRunTask;
import com.intellij.openapi.util.io.FileUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.project.MavenProject;

public class MavenBeforeRunTask extends BeforeRunTask<MavenBeforeRunTask> {
  private String myProjectPath;
  private String myGoal;

  public MavenBeforeRunTask() {
    super(MavenBeforeRunTasksProvider.ID);
  }

  public String getProjectPath() {
    return myProjectPath;
  }

  public void setProjectPath(String projectPath) {
    myProjectPath = projectPath;
  }

  public String getGoal() {
    return myGoal;
  }

  public void setGoal(String goal) {
    myGoal = goal;
  }

  public boolean isFor(MavenProject project, String goal) {
    if (myProjectPath == null || myGoal == null) return false;
    return FileUtil.pathsEqual(project.getPath(), myProjectPath) && goal.equals(myGoal);
  }

  @Override
  public void writeExternal(@NotNull Element element) {
    super.writeExternal(element);
    if (myProjectPath != null) element.setAttribute("file", myProjectPath);
    if (myGoal != null) element.setAttribute("goal", myGoal);
  }

  @Override
  public void readExternal(@NotNull Element element) {
    super.readExternal(element);
    myProjectPath = element.getAttributeValue("file");
    myGoal = element.getAttributeValue("goal");
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    MavenBeforeRunTask that = (MavenBeforeRunTask)o;

    if (myGoal != null ? !myGoal.equals(that.myGoal) : that.myGoal != null) return false;
    if (myProjectPath != null ? !myProjectPath.equals(that.myProjectPath) : that.myProjectPath != null) return false;

    return true;
  }

  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (myProjectPath != null ? myProjectPath.hashCode() : 0);
    result = 31 * result + (myGoal != null ? myGoal.hashCode() : 0);
    return result;
  }
}
