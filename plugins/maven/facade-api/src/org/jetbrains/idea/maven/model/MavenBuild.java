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

import com.intellij.util.SmartList;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MavenBuild extends MavenBuildBase implements Serializable {
  private String myOutputDirectory;
  private String myTestOutputDirectory;
  private List<String> mySources;
  private List<String> myTestSources;

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

  public List<String> getSources() {
    return mySources == null ? Collections.<String>emptyList() : mySources;
  }

  public void setSources(List<String> sources) {
    mySources = new ArrayList<String>(sources);
  }

  public void addSource(String source) {
    if (mySources == null) mySources = new ArrayList<String>();
    mySources.add(source);
  }

  public List<String> getTestSources() {
    return myTestSources == null ? Collections.<String>emptyList() : myTestSources;
  }

  public void setTestSources(List<String> testSources) {
    myTestSources = new ArrayList<String>(testSources);
  }

  public void addTestSource(String source) {
    if (myTestSources == null) myTestSources = new ArrayList<String>();
    myTestSources.add(source);
  }
}