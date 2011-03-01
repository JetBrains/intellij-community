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
package org.jetbrains.idea.maven.tasks;

public class MavenCompilerTask implements Comparable {
  private String myProjectPath;
  private String myGoal;

  public MavenCompilerTask() {
  }

  public MavenCompilerTask(String projectPath, String goal) {
    myProjectPath = projectPath;
    myGoal = goal;
  }

  public String getProjectPath() {
    return myProjectPath;
  }

  // for reflection
  public void setProjectPath(String projectPath) {
    myProjectPath = projectPath;
  }

  public String getGoal() {
    return myGoal;
  }

  // for reflection
  public void setGoal(String goal) {
    myGoal = goal;
  }

  @Override
  public String toString() {
    return myProjectPath + ":" + myGoal;
  }

  public int compareTo(Object o) {
    return toString().compareTo(o.toString());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    MavenCompilerTask task = (MavenCompilerTask)o;

    if (myGoal != null ? !myGoal.equals(task.myGoal) : task.myGoal != null) return false;
    if (myProjectPath != null ? !myProjectPath.equals(task.myProjectPath) : task.myProjectPath != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result;
    result = (myProjectPath != null ? myProjectPath.hashCode() : 0);
    result = 31 * result + (myGoal != null ? myGoal.hashCode() : 0);
    return result;
  }
}
