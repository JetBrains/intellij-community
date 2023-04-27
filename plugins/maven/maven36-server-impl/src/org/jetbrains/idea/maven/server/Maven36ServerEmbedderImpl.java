// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

import com.intellij.util.text.VersionComparatorUtil;
import org.apache.maven.plugin.internal.DefaultPluginDependenciesResolver;
import org.apache.maven.plugin.internal.PluginDependenciesResolver;
import org.apache.maven.project.DefaultProjectDependenciesResolver;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.apache.maven.repository.internal.DefaultArtifactDescriptorReader;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.impl.ArtifactDescriptorReader;
import org.eclipse.aether.impl.ArtifactResolver;
import org.eclipse.aether.impl.DependencyCollector;
import org.eclipse.aether.internal.impl.DefaultRepositorySystem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.server.embedder.CustomMaven36ArtifactDescriptorReader;
import org.jetbrains.idea.maven.server.embedder.CustomMaven36ArtifactResolver;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.rmi.RemoteException;
import java.util.concurrent.atomic.AtomicReference;

public class Maven36ServerEmbedderImpl extends Maven3XServerEmbedder {
  private final AtomicReference<ArtifactResolver> myArtifactResolver = new AtomicReference<>();
  private final AtomicReference<ArtifactDescriptorReader> myArtifactDescriptorReader = new AtomicReference<>();
  private final AtomicReference<RepositorySystem> myRepositorySystem = new AtomicReference<>();

  public Maven36ServerEmbedderImpl(MavenEmbedderSettings settings) throws RemoteException {
    super(settings);
  }

  @NotNull
  private ArtifactResolver createArtifactResolver() {
    ArtifactResolver artifactResolver = getComponent(ArtifactResolver.class);
    return new CustomMaven36ArtifactResolver(artifactResolver);
  }

  @NotNull
  private ArtifactResolver getArtifactResolver() {
    ArtifactResolver artifactResolver = myArtifactResolver.get();
    if (artifactResolver != null) return artifactResolver;
    return myArtifactResolver.updateAndGet(value -> value == null ? createArtifactResolver() : value);
  }

  @NotNull
  private ArtifactDescriptorReader createArtifactDescriptorReader() {
    ArtifactDescriptorReader artifactDescriptorReader = getComponent(ArtifactDescriptorReader.class);
    if (artifactDescriptorReader instanceof DefaultArtifactDescriptorReader) {
      ((DefaultArtifactDescriptorReader)artifactDescriptorReader).setArtifactResolver(getArtifactResolver());
    }
    return new CustomMaven36ArtifactDescriptorReader(artifactDescriptorReader);
  }

  @NotNull
  private ArtifactDescriptorReader getArtifactDescriptorReader() {
    ArtifactDescriptorReader artifactDescriptorReader = myArtifactDescriptorReader.get();
    if (artifactDescriptorReader != null) return artifactDescriptorReader;
    return myArtifactDescriptorReader.updateAndGet(value -> value == null ? createArtifactDescriptorReader() : value);
  }

  @NotNull
  private RepositorySystem createRepositorySystem() {
    RepositorySystem repositorySystem = getComponent(RepositorySystem.class);
    if (repositorySystem instanceof DefaultRepositorySystem) {
      DefaultRepositorySystem defaultRepositorySystem = (DefaultRepositorySystem)repositorySystem;
      defaultRepositorySystem.setArtifactResolver(getArtifactResolver());

      ArtifactDescriptorReader artifactDescriptorReader = getArtifactDescriptorReader();
      defaultRepositorySystem.setArtifactDescriptorReader(artifactDescriptorReader);

      DependencyCollector dependencyCollector;
      if (VersionComparatorUtil.compare(getMavenVersion(), "3.9.0") >= 0) {
        // depth-first dependency collector, available since maven 3.9.0
        dependencyCollector = getComponentIfExists(DependencyCollector.class, "df");
      }
      else {
        // default dependency collector, maven 3.8
        dependencyCollector = getComponentIfExists(DependencyCollector.class);
      }
      try {
        Method method = dependencyCollector.getClass().getMethod("setArtifactDescriptorReader", ArtifactDescriptorReader.class);
        method.invoke(dependencyCollector, artifactDescriptorReader);
        defaultRepositorySystem.setDependencyCollector(dependencyCollector);
      }
      catch (Throwable e) {
        MavenServerGlobals.getLogger().warn(e);
      }
    }
    return repositorySystem;
  }

  @NotNull
  private RepositorySystem getRepositorySystem() {
    RepositorySystem artifactDescriptorReader = myRepositorySystem.get();
    if (artifactDescriptorReader != null) return artifactDescriptorReader;
    return myRepositorySystem.updateAndGet(value -> value == null ? createRepositorySystem() : value);
  }

  @Override
  @NotNull
  protected ProjectDependenciesResolver createDependenciesResolver() {
    ProjectDependenciesResolver dependenciesResolver = getComponent(ProjectDependenciesResolver.class);

    if (myEmbedderSettings.useCustomDependenciesResolver()) {
      if (dependenciesResolver instanceof DefaultProjectDependenciesResolver) {
        try {
          DefaultProjectDependenciesResolver defaultResolver = (DefaultProjectDependenciesResolver)dependenciesResolver;
          Field repoSystemField = defaultResolver.getClass().getDeclaredField("repoSystem");
          repoSystemField.setAccessible(true);
          repoSystemField.set(defaultResolver, getRepositorySystem());
        }
        catch (Exception e) {
          MavenServerGlobals.getLogger().warn(e);
        }
      }
    }

    return dependenciesResolver;
  }

  @Override
  @NotNull
  protected PluginDependenciesResolver createPluginDependenciesResolver() {
    PluginDependenciesResolver dependenciesResolver = getComponent(PluginDependenciesResolver.class);

    if (myEmbedderSettings.useCustomDependenciesResolver()) {
      if (dependenciesResolver instanceof DefaultPluginDependenciesResolver) {
        try {
          DefaultPluginDependenciesResolver defaultResolver = (DefaultPluginDependenciesResolver)dependenciesResolver;
          Field repoSystemField = defaultResolver.getClass().getDeclaredField("repoSystem");
          repoSystemField.setAccessible(true);
          repoSystemField.set(defaultResolver, getRepositorySystem());
        }
        catch (Exception e) {
          MavenServerGlobals.getLogger().warn(e);
        }
      }
    }

    return dependenciesResolver;
  }

  private void resetArtifactResolver() {
    ArtifactResolver artifactResolver = myArtifactResolver.get();
    if (artifactResolver instanceof CustomMaven36ArtifactResolver) {
      ((CustomMaven36ArtifactResolver)artifactResolver).reset();
    }
  }

  private void resetArtifactDescriptorReader() {
    ArtifactDescriptorReader artifactDescriptorReader = myArtifactDescriptorReader.get();
    if (artifactDescriptorReader instanceof CustomMaven36ArtifactDescriptorReader) {
      ((CustomMaven36ArtifactDescriptorReader)artifactDescriptorReader).reset();
    }
  }

  @Override
  protected void resetCustomizedComponents() {
    super.resetCustomizedComponents();

    resetArtifactResolver();
    resetArtifactDescriptorReader();
  }

  @Override
  protected void initLogging(Maven3ServerConsoleLogger consoleWrapper) {
    Maven3Sl4jLoggerWrapper.setCurrentWrapper(consoleWrapper);
  }
}

