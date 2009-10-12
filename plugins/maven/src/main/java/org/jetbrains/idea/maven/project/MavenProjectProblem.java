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
package org.jetbrains.idea.maven.project;

import java.io.Serializable;

public class MavenProjectProblem implements Serializable {
  private String myDescription;
  private boolean isCritical;

  protected MavenProjectProblem() {
  }

  public MavenProjectProblem(String description, boolean critical) {
    myDescription = description;
    isCritical = critical;
  }

  public String getDescription() {
    return myDescription;
  }

  public boolean isCritical() {
    return isCritical;
  }

  @Override
  public String toString() {
    return (isCritical ? "!!!" : "") + myDescription;
  }
}
