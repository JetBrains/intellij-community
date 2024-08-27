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

public class MavenBuild extends MavenBuildBase implements Serializable {
  private String myOutputDirectory;
  private String myTestOutputDirectory;
  private List<@NotNull String> mySources;
  private List<@NotNull String> myTestSources;

  public String getOutputDirectory() {
    return myOutputDirectory;
  }

  public void setOutputDirectory(String outputDirectory) {
    myOutputDirectory = outputDirectory;
  }

  public String getTestOutputDirectory() {
    return myTestOutputDirectory;
  }

  public void setTestOutputDirectory(String testOutputDirectory) {
    myTestOutputDirectory = testOutputDirectory;
  }

  public @NotNull List<@NotNull String> getSources() {
    return mySources == null ? Collections.emptyList() : mySources;
  }

  public void setSources(@NotNull List<@NotNull String> sources) {
    mySources = new ArrayList<>(sources);
  }

  public void addSource(@NotNull String source) {
    if (mySources == null) mySources = new ArrayList<>();
    mySources.add(source);
  }

  public @NotNull List<@NotNull String> getTestSources() {
    return myTestSources == null ? Collections.emptyList() : myTestSources;
  }

  public void setTestSources(@NotNull List<@NotNull String> testSources) {
    myTestSources = new ArrayList<>(testSources);
  }

  public void addTestSource(@NotNull String source) {
    if (myTestSources == null) myTestSources = new ArrayList<>();
    myTestSources.add(source);
  }
}