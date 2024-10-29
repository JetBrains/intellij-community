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
import java.util.*;

public class MavenModelBase implements Serializable {
  private Properties myProperties;
  private ArrayList<MavenPlugin> myPlugins = new ArrayList<>();
  private ArrayList<MavenArtifact> myExtensions = new ArrayList<>();
  private ArrayList<MavenArtifact> myDependencies = new ArrayList<>();
  private ArrayList<MavenArtifactNode> myDependencyTree = new ArrayList<>();
  private ArrayList<MavenRemoteRepository> myRemoteRepositories = new ArrayList<>();
  private ArrayList<MavenRemoteRepository> myRemotePluginRepositories = new ArrayList<>();

  private ArrayList<String> myModules;

  public Properties getProperties() {
    if (myProperties == null) myProperties = new Properties();
    return myProperties;
  }

  public void setProperties(Properties newProperties) {
    Properties properties = getProperties();
    properties.clear();
    if (null == newProperties) return;
    Enumeration<?> newPropertyNames = newProperties.propertyNames();
    while (newPropertyNames.hasMoreElements()) {
      String key = newPropertyNames.nextElement().toString();
      String value = newProperties.getProperty(key);
      properties.setProperty(key, value);
    }
  }

  public void setProperties(Map<String, String> newMap) {
    Properties newProperties = new Properties();
    newProperties.putAll(newMap);
    setProperties(newProperties);
  }

  public List<MavenPlugin> getPlugins() {
    return myPlugins;
  }

  public void setPlugins(List<MavenPlugin> plugins) {
    myPlugins = new ArrayList<>(plugins);
  }

  public List<MavenArtifact> getExtensions() {
    return myExtensions;
  }

  public void setExtensions(List<MavenArtifact> extensions) {
    myExtensions = new ArrayList<>(extensions);
  }

  public List<MavenArtifact> getDependencies() {
    return myDependencies;
  }

  public void setDependencies(List<MavenArtifact> dependencies) {
    myDependencies = new ArrayList<>(dependencies);
  }

  public List<MavenArtifactNode> getDependencyTree() {
    return myDependencyTree;
  }

  public void setDependencyTree(List<MavenArtifactNode> dependencyTree) {
    myDependencyTree = new ArrayList<>(dependencyTree);
  }

  public List<MavenRemoteRepository> getRemoteRepositories() {
    return myRemoteRepositories;
  }

  public void setRemoteRepositories(List<MavenRemoteRepository> remoteRepositories) {
    myRemoteRepositories = new ArrayList<>(remoteRepositories);
  }

  public ArrayList<MavenRemoteRepository> getRemotePluginRepositories() {
    return myRemotePluginRepositories;
  }

  public void setRemotePluginRepositories(List<MavenRemoteRepository> remotePluginRepositories) {
    myRemotePluginRepositories = new ArrayList<>(remotePluginRepositories);
  }

  public List<String> getModules() {
    return myModules;
  }

  public void setModules(List<String> modules) {
    myModules = new ArrayList<>(modules);
  }
}
