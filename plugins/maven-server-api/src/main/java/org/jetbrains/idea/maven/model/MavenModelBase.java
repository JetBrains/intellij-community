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
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;

public class MavenModelBase implements Serializable {
  private Properties myProperties;
  private @NotNull List<@NotNull MavenPlugin> myPlugins = new CopyOnWriteArrayList<>();
  private @NotNull List<@NotNull MavenArtifact> myExtensions = new CopyOnWriteArrayList<>();
  private @NotNull List<@NotNull MavenArtifact> myDependencies = new CopyOnWriteArrayList<>();
  private @NotNull List<@NotNull MavenArtifactNode> myDependencyTree = new CopyOnWriteArrayList<>();
  private @NotNull List<@NotNull MavenRemoteRepository> myRemoteRepositories = new CopyOnWriteArrayList<>();
  private @NotNull List<@NotNull MavenRemoteRepository> myRemotePluginRepositories = new CopyOnWriteArrayList<>();
  private @NotNull List<@NotNull String> myModules = new CopyOnWriteArrayList<>();


  public MavenModelBase() { }

  protected MavenModelBase(@NotNull MavenModelBase other) {
    if (other.myProperties != null) {
      Properties p = new Properties();
      p.putAll(other.myProperties);
      this.myProperties = p;
    }

    this.myPlugins = new CopyOnWriteArrayList<>(other.myPlugins);
    this.myExtensions = new CopyOnWriteArrayList<>(other.myExtensions);
    this.myDependencies = new CopyOnWriteArrayList<>(other.myDependencies);
    this.myDependencyTree = new CopyOnWriteArrayList<>(other.myDependencyTree);
    this.myRemoteRepositories = new CopyOnWriteArrayList<>(other.myRemoteRepositories);
    this.myRemotePluginRepositories = new CopyOnWriteArrayList<>(other.myRemotePluginRepositories);
    this.myModules = new CopyOnWriteArrayList<>(other.myModules);
  }

  public MavenModelBase copy() {
    return new MavenModelBase(this);
  }

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

  public @NotNull List<@NotNull MavenPlugin> getPlugins() {
    return Collections.unmodifiableList(myPlugins);
  }

  public void setPlugins(@NotNull List<@NotNull MavenPlugin> plugins) {
    myPlugins = new CopyOnWriteArrayList<>(plugins);
  }

  public @NotNull List<@NotNull MavenArtifact> getExtensions() {
    return Collections.unmodifiableList(myExtensions);
  }

  public void setExtensions(@NotNull List<@NotNull MavenArtifact> extensions) {
    myExtensions = new CopyOnWriteArrayList<>(extensions);
  }

  public @NotNull List<@NotNull MavenArtifact> getDependencies() {
    return Collections.unmodifiableList(myDependencies);
  }

  public void setDependencies(@NotNull List<@NotNull MavenArtifact> dependencies) {
    myDependencies = new CopyOnWriteArrayList<>(dependencies);
  }

  public @NotNull List<@NotNull MavenArtifactNode> getDependencyTree() {
    return Collections.unmodifiableList(myDependencyTree);
  }

  public void setDependencyTree(@NotNull List<@NotNull MavenArtifactNode> dependencyTree) {
    myDependencyTree = new CopyOnWriteArrayList<>(dependencyTree);
  }

  public @NotNull List<@NotNull MavenRemoteRepository> getRemoteRepositories() {
    return Collections.unmodifiableList(myRemoteRepositories);
  }

  public void setRemoteRepositories(@NotNull List<@NotNull MavenRemoteRepository> remoteRepositories) {
    myRemoteRepositories = new CopyOnWriteArrayList<>(remoteRepositories);
  }

  public @NotNull List<@NotNull MavenRemoteRepository> getRemotePluginRepositories() {
    return Collections.unmodifiableList(myRemotePluginRepositories);
  }

  public void setRemotePluginRepositories(@NotNull List<@NotNull MavenRemoteRepository> remotePluginRepositories) {
    myRemotePluginRepositories = new CopyOnWriteArrayList<>(remotePluginRepositories);
  }

  public @NotNull List<@NotNull String> getModules() {
    return Collections.unmodifiableList(myModules);
  }

  public void setModules(@NotNull List<@NotNull String> modules) {
    myModules = new CopyOnWriteArrayList<>(modules);
  }
}
