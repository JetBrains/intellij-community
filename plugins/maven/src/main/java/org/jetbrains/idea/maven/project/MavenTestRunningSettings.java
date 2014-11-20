/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

/**
 * @author Sergey Evdokimov
 */
public class MavenTestRunningSettings {

  private boolean myPassArgLine = true;
  private boolean myPassEnvironmentVariables = true;
  private boolean myPassSystemProperties = true;

  public boolean isPassArgLine() {
    return myPassArgLine;
  }

  public void setPassArgLine(boolean passArgLine) {
    myPassArgLine = passArgLine;
  }

  public boolean isPassEnvironmentVariables() {
    return myPassEnvironmentVariables;
  }

  public void setPassEnvironmentVariables(boolean passEnvironmentVariables) {
    myPassEnvironmentVariables = passEnvironmentVariables;
  }

  public boolean isPassSystemProperties() {
    return myPassSystemProperties;
  }

  public void setPassSystemProperties(boolean passSystemProperties) {
    myPassSystemProperties = passSystemProperties;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof MavenTestRunningSettings)) return false;

    MavenTestRunningSettings settings = (MavenTestRunningSettings)o;

    if (myPassArgLine != settings.myPassArgLine) return false;
    if (myPassEnvironmentVariables != settings.myPassEnvironmentVariables) return false;
    if (myPassSystemProperties != settings.myPassSystemProperties) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = (myPassArgLine ? 1 : 0);
    result = 31 * result + (myPassEnvironmentVariables ? 1 : 0);
    result = 31 * result + (myPassSystemProperties ? 1 : 0);
    return result;
  }
}
