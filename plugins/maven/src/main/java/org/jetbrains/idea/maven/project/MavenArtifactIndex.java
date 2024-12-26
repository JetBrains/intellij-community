// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project;

import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.DependencyConflictId;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenId;

import java.util.*;

public final class MavenArtifactIndex {

  private static final MavenArtifactIndex EMPTY_INDEX = new MavenArtifactIndex(Collections.emptyMap());

  private final Map<String, Map<String, List<MavenArtifact>>> myData;

  private MavenArtifactIndex(Map<String, Map<String, List<MavenArtifact>>> data) {
    myData = data;
  }

  public Map<String, Map<String, List<MavenArtifact>>> getData() {
    return myData;
  }

  public boolean hasArtifact(@Nullable String groupId, @Nullable String artifactId) {
    Map<String, List<MavenArtifact>> groupMap = myData.get(groupId);
    if (groupMap == null) return false;

    return groupMap.containsKey(artifactId);
  }

  public @NotNull List<MavenArtifact> findArtifacts(@Nullable String groupId, @Nullable String artifactId) {
    Map<String, List<MavenArtifact>> groupMap = myData.get(groupId);
    if (groupMap == null) return Collections.emptyList();

    List<MavenArtifact> res = groupMap.get(artifactId);
    return res == null ? Collections.emptyList() : res;
  }

  public @NotNull List<MavenArtifact> findArtifacts(@Nullable MavenId mavenId) {
    if (mavenId == null) return Collections.emptyList();

    return findArtifacts(mavenId.getGroupId(), mavenId.getArtifactId(), mavenId.getVersion());
  }

  public @Nullable MavenArtifact findArtifacts(@NotNull DependencyConflictId id) {
    for (MavenArtifact artifact : findArtifacts(id.getGroupId(), id.getArtifactId())) {
      if (id.equals(DependencyConflictId.create(artifact))) {
        return artifact;
      }
    }

    return null;
  }

  public @NotNull List<MavenArtifact> findArtifacts(@Nullable String groupId, @Nullable String artifactId, @Nullable String version) {
    Map<String, List<MavenArtifact>> groupMap = myData.get(groupId);
    if (groupMap == null) return Collections.emptyList();

    List<MavenArtifact> artifacts = groupMap.get(artifactId);
    if (artifacts == null) return Collections.emptyList();

    List<MavenArtifact> res = new SmartList<>();
    for (MavenArtifact artifact : artifacts) {
      if (Objects.equals(version, artifact.getVersion())) {
        res.add(artifact);
      }
    }

    return res;
  }

  public static MavenArtifactIndex build(@NotNull List<? extends MavenArtifact> dependencies) {
    if (dependencies.isEmpty()) return EMPTY_INDEX;

    Map<String, Map<String, List<MavenArtifact>>> map = new HashMap<>();

    for (MavenArtifact dep : dependencies) {
      String groupId = dep.getGroupId();
      if (groupId == null) continue;

      String artifactId = dep.getArtifactId();
      if (artifactId == null) continue;

      Map<String, List<MavenArtifact>> groupMap = map.get(groupId);
      if (groupMap == null) {
        groupMap = new HashMap<>();
        map.put(groupId, groupMap);
      }

      List<MavenArtifact> artifactList = groupMap.get(artifactId);
      if (artifactList == null) {
        artifactList = new SmartList<>();
        groupMap.put(artifactId, artifactList);
      }

      artifactList.add(dep);
    }

    return new MavenArtifactIndex(map);
  }

}
