// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.server.m40.utils;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.util.graph.manager.DependencyManagerUtils;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.*;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * {@link Maven40AetherModelConverter} provides adapted methods of {@link Maven40ModelConverter} for aether models conversion
 */
public final class Maven40AetherModelConverter extends Maven40ModelConverter {
  public static @NotNull MavenModel convertModelWithAetherDependencyTree(MavenProject mavenProject,
                                                                         Model model,
                                                                         Collection<? extends DependencyNode> dependencyTree,
                                                                         File localRepository) {
    MavenModel result = new MavenModel();
    result.setMavenId(new MavenId(mavenProject.getGroupId(), mavenProject.getArtifactId(), mavenProject.getVersion()));

    Parent parent = model.getParent();
    if (parent != null) {
      result.setParent(
        new MavenParent(new MavenId(parent.getGroupId(), parent.getArtifactId(), parent.getVersion()), parent.getRelativePath()));
    }
    result.setPackaging(model.getPackaging());
    result.setName(mavenProject.getName());
    result.setProperties(mavenProject.getProperties() == null ? new Properties() : model.getProperties());
    //noinspection SSBasedInspection
    result.setPlugins(convertPlugins(model, mavenProject.getPluginArtifacts()));

    Map<Artifact, MavenArtifact> convertedArtifacts = new HashMap<>();
    result.setExtensions(convertArtifacts(mavenProject.getExtensionArtifacts(), convertedArtifacts, localRepository));
    result.setDependencyTree(convertAetherDependencyNodes(null, dependencyTree, convertedArtifacts, localRepository));
    result.setDependencies(convertArtifacts(mavenProject.getArtifacts(), convertedArtifacts, localRepository));

    result.setRemoteRepositories(convertAetherRepositories(mavenProject.getRemoteProjectRepositories()));
    result.setRemotePluginRepositories(convertAetherRepositories(mavenProject.getRemotePluginRepositories()));
    result.setProfiles(convertProfiles(model.getProfiles()));
    result.setModules(mavenProject.getModules());

    convertBuild(result.getBuild(), model.getBuild());
    return result;
  }

  @SuppressWarnings("SSBasedInspection")
  private static List<MavenRemoteRepository> convertAetherRepositories(List<RemoteRepository> repositories) {
    return repositories.stream().map(
      r -> new MavenRemoteRepository(r.getId(), r.getId(), r.getUrl(), "default", convertPolicy(r.getPolicy(false)),
                                     convertPolicy(r.getPolicy(true)))).collect(Collectors.toList());
  }

  private static MavenRemoteRepository.@Nullable Policy convertPolicy(RepositoryPolicy policy) {
    return policy != null ? new MavenRemoteRepository.Policy(policy.isEnabled(), policy.getArtifactUpdatePolicy(),
                                                             policy.getChecksumPolicy()) : null;
  }

  public static List<MavenArtifactNode> convertAetherDependencyNodes(MavenArtifactNode parent,
                                                                     Collection<? extends DependencyNode> nodes,
                                                                     Map<Artifact, MavenArtifact> nativeToConvertedMap,
                                                                     File localRepository) {
    List<MavenArtifactNode> result = new ArrayList<>(nodes.size());
    for (DependencyNode each : nodes) {
      Artifact a = toArtifact(each.getDependency());
      MavenArtifact ma = convertArtifact(a, nativeToConvertedMap, localRepository);

      Map<?, ?> data = each.getData();
      String premanagedVersion = DependencyManagerUtils.getPremanagedVersion(each);
      String premanagedScope = DependencyManagerUtils.getPremanagedScope(each);


      MavenArtifactState state = MavenArtifactState.ADDED;
      MavenArtifact relatedArtifact = null;

      String scope = each.getDependency().getScope();
      Object winner = data.get(ConflictResolver.NODE_DATA_WINNER);
      if (winner instanceof DependencyNode) {
        DependencyNode winnerNode = (DependencyNode)winner;
        scope = winnerNode.getDependency().getScope();
        Artifact winnerArtifact = toArtifact(winnerNode.getDependency());
        relatedArtifact = convertArtifact(winnerArtifact, nativeToConvertedMap, localRepository);
        nativeToConvertedMap.put(winnerArtifact, relatedArtifact);
        if (!Objects.equals(each.getVersion().toString(), winnerNode.getVersion().toString())) {
          state = MavenArtifactState.CONFLICT;
        }
        else {
          state = MavenArtifactState.DUPLICATE;
        }
      }

      ma.setScope(scope);
      MavenArtifactNode newNode =
        new MavenArtifactNode(parent, ma, state, relatedArtifact, each.getDependency().getScope(), premanagedVersion, premanagedScope);
      newNode.setDependencies(convertAetherDependencyNodes(newNode, each.getChildren(), nativeToConvertedMap, localRepository));
      result.add(newNode);
    }
    return result;
  }

  public static @Nullable Artifact toArtifact(@Nullable Dependency dependency) {
    if (dependency == null) {
      return null;
    }

    Artifact result = RepositoryUtils.toArtifact(dependency.getArtifact());
    if (result == null) {
      return null;
    }
    result.setScope(dependency.getScope());
    result.setOptional(dependency.isOptional());
    return result;
  }
}
