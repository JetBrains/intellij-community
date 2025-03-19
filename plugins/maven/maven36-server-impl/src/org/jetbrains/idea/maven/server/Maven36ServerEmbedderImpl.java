// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.eclipse.aether.internal.impl.DefaultArtifactResolver;
import org.eclipse.aether.internal.impl.DefaultRepositorySystem;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenWorkspaceMap;
import org.jetbrains.idea.maven.server.embedder.CustomMaven36ArtifactDescriptorReader;
import org.jetbrains.idea.maven.server.embedder.CustomMaven36ArtifactResolver;
import org.jetbrains.idea.maven.server.embedder.Resetable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.rmi.RemoteException;

public class Maven36ServerEmbedderImpl extends Maven3XServerEmbedder {

  public Maven36ServerEmbedderImpl(MavenEmbedderSettings settings) throws RemoteException {
    super(settings);
  }

  @Override
  protected void customizeComponents(@Nullable MavenWorkspaceMap workspaceMap) {
    super.customizeComponents(workspaceMap);

    ArtifactResolver customResolver = createCustomArtifactResolver(workspaceMap);
    if (customResolver != null) {

      addComponent(customResolver, ArtifactResolver.class);

      ArtifactDescriptorReader artifactDescriptorReader = getComponent(ArtifactDescriptorReader.class);
      if (artifactDescriptorReader instanceof DefaultArtifactDescriptorReader) {
        ((DefaultArtifactDescriptorReader)artifactDescriptorReader).setArtifactResolver(customResolver);
        artifactDescriptorReader = new CustomMaven36ArtifactDescriptorReader(artifactDescriptorReader);
        addComponent(artifactDescriptorReader, ArtifactDescriptorReader.class);
      }

      RepositorySystem repositorySystem = getComponent(RepositorySystem.class);
      if (repositorySystem instanceof DefaultRepositorySystem) {
        DefaultRepositorySystem defaultRepositorySystem = (DefaultRepositorySystem)repositorySystem;
        defaultRepositorySystem.setArtifactResolver(customResolver);

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

      // TODO: redundant?
      ProjectDependenciesResolver projectDependenciesResolver = getComponent(ProjectDependenciesResolver.class);
      if (projectDependenciesResolver instanceof DefaultProjectDependenciesResolver) {
        try {
          DefaultProjectDependenciesResolver defaultResolver = (DefaultProjectDependenciesResolver)projectDependenciesResolver;
          Field repoSystemField = defaultResolver.getClass().getDeclaredField("repoSystem");
          repoSystemField.setAccessible(true);
          repoSystemField.set(defaultResolver, repositorySystem);
        }
        catch (Exception e) {
          MavenServerGlobals.getLogger().warn(e);
        }
      }

      // TODO: redundant?
      PluginDependenciesResolver pluginDependenciesResolver = getComponent(PluginDependenciesResolver.class);
      if (pluginDependenciesResolver instanceof DefaultPluginDependenciesResolver) {
        try {
          DefaultPluginDependenciesResolver defaultResolver = (DefaultPluginDependenciesResolver)pluginDependenciesResolver;
          Field repoSystemField = defaultResolver.getClass().getDeclaredField("repoSystem");
          repoSystemField.setAccessible(true);
          repoSystemField.set(defaultResolver, repositorySystem);
        }
        catch (Exception e) {
          MavenServerGlobals.getLogger().warn(e);
        }
      }
    }
  }

  protected @Nullable ArtifactResolver createCustomArtifactResolver(@Nullable MavenWorkspaceMap workspaceMap) {
    if (!myEmbedderSettings.useCustomDependenciesResolver()) {
      return null;
    }
    String resolverClassName =
      System.getProperty("intellij.custom.artifactResolver", CustomMaven36ArtifactResolver.class.getCanonicalName());
    if (resolverClassName == null || resolverClassName.isEmpty()) return null;
    try {
      ArtifactResolver artifactResolver = getComponent(ArtifactResolver.class);
      if (artifactResolver instanceof DefaultArtifactResolver) {
        Constructor<?> constructor = Class.forName(resolverClassName).getConstructor(ArtifactResolver.class);
        return (ArtifactResolver)constructor.newInstance(artifactResolver);
      }
    }
    catch (Exception e) {
      MavenServerGlobals.getLogger().error(e);
    }
    return null;
  }

  @Override
  protected void resetComponents() {
    super.resetComponents();

    ArtifactResolver artifactResolver = getComponent(ArtifactResolver.class);
    if (artifactResolver instanceof Resetable) {
      ((Resetable)artifactResolver).reset();
    }
    ArtifactDescriptorReader artifactDescriptorReader = getComponent(ArtifactDescriptorReader.class);
    if (artifactDescriptorReader instanceof Resetable) {
      ((Resetable)artifactDescriptorReader).reset();
    }
  }

  @Override
  protected void initLogging(Maven3ServerConsoleLogger consoleWrapper) {
    Maven3Sl4jLoggerWrapper.setCurrentWrapper(consoleWrapper);
  }
}

