/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.model;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

public class MavenBuildBase implements Serializable {
  private String myFinalName;
  private String myDefaultGoal;
  private String myDirectory;
  private List<MavenResource> myResources;
  private List<MavenResource> myTestResources;
  private List<String> myFilters;

  public String getFinalName() {
    return myFinalName;
  }

  public void setFinalName(String finalName) {
    myFinalName = finalName;
  }

  public String getDefaultGoal() {
    return myDefaultGoal;
  }

  public void setDefaultGoal(String defaultGoal) {
    myDefaultGoal = defaultGoal;
  }

  public String getDirectory() {
    return myDirectory;
  }

  public void setDirectory(String directory) {
    myDirectory = directory;
  }

  public List<MavenResource> getResources() {
    return myResources == null ? Collections.<MavenResource>emptyList() : myResources;
  }

  public void setResources(List<MavenResource> resources) {
    myResources = resources;
  }

  public List<MavenResource> getTestResources() {
    return myTestResources == null ? Collections.<MavenResource>emptyList() : myTestResources;
  }

  public void setTestResources(List<MavenResource> testResources) {
    myTestResources = testResources;
  }

  public List<String> getFilters() {
    return myFilters == null ? Collections.<String>emptyList() : myFilters;
  }

  public void setFilters(List<String> filters) {
    myFilters = filters;
  }
}