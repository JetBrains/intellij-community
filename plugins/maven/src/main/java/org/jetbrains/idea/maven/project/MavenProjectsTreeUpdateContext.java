// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@ApiStatus.Internal
class MavenProjectsTreeUpdateContext {
  private final MavenProjectsTree myTree;
  private final Map<MavenProject, MavenProjectChanges> updatedProjectsWithChanges = new ConcurrentHashMap<>();
  private final Set<MavenProject> deletedProjects = ConcurrentHashMap.newKeySet();

  public MavenProjectsTreeUpdateContext(MavenProjectsTree tree) { myTree = tree; }

  public void updated(MavenProject project, @NotNull MavenProjectChanges changes) {
    deletedProjects.remove(project);
    updatedProjectsWithChanges.compute(project, (__, previousChanges) ->
      previousChanges == null ? changes : MavenProjectChangesBuilder.merged(changes, previousChanges)
    );
  }

  public void deleted(MavenProject project) {
    updatedProjectsWithChanges.remove(project);
    deletedProjects.add(project);
  }

  public MavenProjectsTreeUpdateResult toUpdateResult() {
    return new MavenProjectsTreeUpdateResult(mapToListWithPairs(), new ArrayList<>(deletedProjects));
  }

  public void fireUpdatedIfNecessary() {
    if (updatedProjectsWithChanges.isEmpty() && deletedProjects.isEmpty()) {
      return;
    }
    List<Pair<MavenProject, MavenProjectChanges>> updated = mapToListWithPairs();
    List<MavenProject> deleted = new ArrayList<>(deletedProjects);
    myTree.fireProjectsUpdated(updated, deleted);
  }

  private @NotNull List<Pair<MavenProject, MavenProjectChanges>> mapToListWithPairs() {
    ArrayList<Pair<MavenProject, MavenProjectChanges>> result = new ArrayList<>(updatedProjectsWithChanges.size());
    for (Map.Entry<MavenProject, MavenProjectChanges> entry : updatedProjectsWithChanges.entrySet()) {
      entry.getKey().collectProblems(null); // need for fill problem cache
      result.add(Pair.create(entry.getKey(), entry.getValue()));
    }
    return result;
  }

  public Collection<MavenProject> getDeletedProjects() {
    return deletedProjects;
  }
}
