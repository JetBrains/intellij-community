// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.utils.MavenSimpleProjectComponent;
import org.jetbrains.idea.reposearch.DependencySearchService;

import java.util.List;
import java.util.Set;

/**
 * @deprecated use {@link #org.jetbrains.idea.maven.indices.MavenIndicesManager}
 */
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
public final class MavenProjectIndicesManager extends MavenSimpleProjectComponent implements Disposable {

  public static MavenProjectIndicesManager getInstance(Project p) {
    return p.getService(MavenProjectIndicesManager.class);
  }

  @Override
  public void dispose() {
  }

  public MavenProjectIndicesManager(Project project) {
    super(project);
  }

  /**
   * @deprecated use {@link MavenIndicesManager#scheduleUpdateIndicesList(com.intellij.util.Consumer)}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
  public void scheduleUpdateIndicesList(@Nullable Consumer<? super List<MavenIndex>> consumer) {
    MavenIndicesManager.getInstance(myProject).scheduleUpdateIndicesList(consumer);
  }

  /**
   * @deprecated use {@link #org.jetbrains.idea.maven.indices.MavenIndicesManager#getIndex()}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
  public List<MavenIndex> getIndices() {
    return MavenIndicesManager.getInstance(myProject).getIndex().getIndices();
  }

  /**
   * @deprecated use {@link #org.jetbrains.idea.maven.indices.MavenIndicesManager#scheduleUpdate(List)}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
  public void scheduleUpdate(List<MavenIndex> indices) {
    MavenIndicesManager.getInstance(myProject).scheduleUpdateContent(indices);
  }

  /**
   * @deprecated use {@link DependencySearchService}
   * or use {@link MavenGroupIdCompletionContributor} for example to fill async completion variants.
   **/
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
  public Set<String> getGroupIds() {
    return getGroupIds("");
  }

  /**
   * @deprecated use {@link DependencySearchService}
   * or use {@link MavenGroupIdCompletionContributor} for example to fill async completion variants.
   **/
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
  public Set<String> getGroupIds(String pattern) {
    return DependencySearchService.getInstance(myProject).getGroupIds(pattern);
  }

  /**
   * @deprecated use {@link DependencySearchService}
   * or use {@link MavenArtifactIdCompletionContributor} for example to fill async completion variants.
   **/
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
  public Set<String> getArtifactIds(String groupId) {
    return DependencySearchService.getInstance(myProject).getArtifactIds(groupId);
  }
}
