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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

public class MavenModelBase implements Serializable {
  private Properties myProperties;
  private List<MavenPlugin> myPlugins = Collections.emptyList();
  private List<MavenArtifact> myExtensions = Collections.emptyList();
  private List<MavenArtifact> myDependencies = Collections.emptyList();
  private List<MavenArtifactNode> myDependencyTree = Collections.emptyList();
  private List<MavenRemoteRepository> myRemoteRepositories = Collections.emptyList();

  private List<String> myModules;

  public Properties getProperties() {
    if (myProperties == null) myProperties = new Properties();
    return myProperties;
  }

  public void setProperties(Properties properties) {
    myProperties = properties;
  }

  public List<MavenPlugin> getPlugins() {
    return myPlugins;
  }

  public void setPlugins(List<MavenPlugin> plugins) {
    myPlugins = new ArrayList<MavenPlugin>(plugins);
  }

  public List<MavenArtifact> getExtensions() {
    return myExtensions;
  }

  public void setExtensions(List<MavenArtifact> extensions) {
    myExtensions = new ArrayList<MavenArtifact>(extensions);
  }

  public List<MavenArtifact> getDependencies() {
    return myDependencies;
  }

  public void setDependencies(List<MavenArtifact> dependencies) {
    myDependencies = new ArrayList<MavenArtifact>(dependencies);
  }

  public List<MavenArtifactNode> getDependencyTree() {
    return myDependencyTree;
  }

  public void setDependencyTree(List<MavenArtifactNode> dependencyTree) {
    myDependencyTree = new ArrayList<MavenArtifactNode>(dependencyTree);
  }

  public List<MavenRemoteRepository> getRemoteRepositories() {
    return myRemoteRepositories;
  }

  public void setRemoteRepositories(List<MavenRemoteRepository> remoteRepositories) {
    myRemoteRepositories = new ArrayList<MavenRemoteRepository>(remoteRepositories);
  }

  public List<String> getModules() {
    return myModules;
  }

  public void setModules(List<String> modules) {
    myModules = new ArrayList<String>(modules);
  }
}
