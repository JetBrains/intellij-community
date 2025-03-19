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

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MavenBuildBase implements Serializable {
  private String myFinalName;
  private String myDefaultGoal;
  private String myDirectory;
  private @NotNull List<@NotNull MavenResource> myResources = Collections.emptyList();
  private @NotNull List<@NotNull MavenResource> myTestResources = Collections.emptyList();
  private @NotNull List<@NotNull String> myFilters = Collections.emptyList();

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

  public @NotNull List<@NotNull MavenResource> getResources() {
    return myResources;
  }

  public void setResources(@NotNull List<@NotNull MavenResource> resources) {
    myResources = new ArrayList<>(resources);
  }

  public @NotNull List<@NotNull MavenResource> getTestResources() {
    return myTestResources;
  }

  public void setTestResources(@NotNull List<@NotNull MavenResource> testResources) {
    myTestResources = new ArrayList<>(testResources);
  }

  public @NotNull List<@NotNull String> getFilters() {
    return myFilters;
  }

  public void setFilters(@NotNull List<@NotNull String> filters) {
    myFilters = new ArrayList<>(filters);
  }
}