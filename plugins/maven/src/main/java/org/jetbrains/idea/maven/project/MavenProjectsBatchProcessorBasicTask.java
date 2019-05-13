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

import java.util.Collection;

public abstract class MavenProjectsBatchProcessorBasicTask implements MavenProjectsProcessorTask {
  protected final Collection<MavenProject> myMavenProjects;
  protected final MavenProjectsTree myTree;

  public MavenProjectsBatchProcessorBasicTask(Collection<MavenProject> mavenProjects, MavenProjectsTree tree) {
    myMavenProjects = mavenProjects;
    myTree = tree;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    return myMavenProjects.equals(((MavenProjectsBatchProcessorBasicTask)o).myMavenProjects);
  }

  @Override
  public int hashCode() {
    return myMavenProjects.hashCode();
  }
}