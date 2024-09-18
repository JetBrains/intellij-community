// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.server.m40;

import com.intellij.maven.server.m40.utils.Maven40TransferListenerAdapter;
import com.intellij.maven.server.m40.utils.Maven40WorkspaceMapReader;
import org.apache.maven.api.PathType;
import org.apache.maven.api.Type;
import org.apache.maven.api.services.TypeRegistry;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.repository.internal.MavenSessionBuilderSupplier;
import org.apache.maven.repository.internal.type.DefaultType;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.ArtifactType;
import org.eclipse.aether.artifact.ArtifactTypeRegistry;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.util.graph.manager.DependencyManagerUtils;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;
import org.eclipse.aether.util.repository.SimpleResolutionErrorPolicy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenWorkspaceMap;
import org.jetbrains.idea.maven.server.MavenServerConsoleIndicatorImpl;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class IdeaMavenSessionBuilderSupplier extends MavenSessionBuilderSupplier {
  private final MavenWorkspaceMap myWorkspaceMap;
  private final MavenServerConsoleIndicatorImpl myIndicator;
  private final Path myLocalRepoPath;
  @NotNull private final TypeRegistry myTypeRegistry;
  private final Map<String, String> mySystemProperties = new HashMap<>();
  private final Map<String, String> myUserProperties = new HashMap<>();
  private final boolean myUpdateSnapshots;

  public IdeaMavenSessionBuilderSupplier(RepositorySystem repositorySystem,
                                         MavenWorkspaceMap map,
                                         MavenServerConsoleIndicatorImpl indicator,
                                         @NotNull MavenExecutionRequest request,
                                         @NotNull TypeRegistry typeRegistry) {
    super(repositorySystem);
    myWorkspaceMap = map;
    myIndicator = indicator;
    myLocalRepoPath = request.getLocalRepositoryPath().toPath();
    myTypeRegistry = typeRegistry;
    putAll(request.getSystemProperties(), mySystemProperties);
    putAll(request.getUserProperties(), myUserProperties);
    myUpdateSnapshots = request.isUpdateSnapshots();
  }

  private static void putAll(Properties from, Map<String, String> to) {
    from.stringPropertyNames().forEach(n -> to.put(n, from.getProperty(n)));
  }

  @Override
  protected void configureSessionBuilder(RepositorySystemSession.SessionBuilder session) {
    super.configureSessionBuilder(session);
    session.setWorkspaceReader(new Maven40WorkspaceMapReader(myWorkspaceMap))
      .setTransferListener(new Maven40TransferListenerAdapter(myIndicator))
      .setConfigProperty(ConflictResolver.CONFIG_PROP_VERBOSE, true)
      .setConfigProperty(DependencyManagerUtils.CONFIG_PROP_VERBOSE, true)
      .setSystemProperties(mySystemProperties)
      .setUserProperties(myUserProperties)
      .withLocalRepositories(new LocalRepository(myLocalRepoPath))
      .setResolutionErrorPolicy(new SimpleResolutionErrorPolicy(1, 1));
    if (myUpdateSnapshots) {
      session.setArtifactUpdatePolicy(RepositoryPolicy.UPDATE_POLICY_ALWAYS);
      session.setMetadataUpdatePolicy(RepositoryPolicy.UPDATE_POLICY_ALWAYS);
    }
  }

  @Override
  protected ArtifactTypeRegistry getArtifactTypeRegistry() {
    return new TypeRegistryWrapper();
  }

  private class TypeRegistryWrapper implements ArtifactTypeRegistry {

    @Override
    public ArtifactType get(String typeId) {
      Type type = myTypeRegistry.require(typeId);
      if (type instanceof ArtifactType) {
        return (ArtifactType)type;
      }
      if (type != null) {
        return new DefaultType(
          type.id(),
          type.getLanguage(),
          type.getExtension(),
          type.getClassifier(),
          type.isIncludesDependencies(),
          type.getPathTypes().toArray(new PathType[0]));
      }
      return null;
    }
  }
}
