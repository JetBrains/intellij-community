// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.utils.MavenSimpleProjectComponent;
import org.jetbrains.idea.reposearch.DependencySearchService;

import java.util.List;
import java.util.Set;

/**
 * @deprecated use {@link #org.jetbrains.idea.maven.indices.MavenIndicesManager}
 */
@Deprecated(forRemoval = true)
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
   * @deprecated use {@link MavenIndicesManager#scheduleUpdateIndicesList(Consumer)}
   */
  @Deprecated(forRemoval = true)
  public void scheduleUpdateIndicesList(@Nullable Consumer<? super List<MavenIndex>> consumer) {
    MavenIndicesManager.getInstance(myProject).scheduleUpdateIndicesList(consumer);
  }

  /**
   * @deprecated use {@link MavenIndicesManager#getIndex()}
   */
  @Deprecated(forRemoval = true)
  public List<MavenIndex> getIndices() {
    return MavenIndicesManager.getInstance(myProject).getIndex().getIndices();
  }

  /**
   * @deprecated use {@link MavenIndicesManager#scheduleUpdateContent(List)}
   */
  @Deprecated(forRemoval = true)
  public void scheduleUpdate(List<MavenIndex> indices) {
    MavenIndicesManager.getInstance(myProject).scheduleUpdateContent(indices);
  }

  /**
   * @deprecated use {@link DependencySearchService}
   * or use {@link MavenGroupIdCompletionContributor} for example to fill async completion variants.
   **/
  @Deprecated(forRemoval = true)
  public Set<String> getGroupIds() {
    return getGroupIds("");
  }

  /**
   * @deprecated use {@link DependencySearchService}
   * or use {@link MavenGroupIdCompletionContributor} for example to fill async completion variants.
   **/
  @Deprecated(forRemoval = true)
  public Set<String> getGroupIds(String pattern) {
    return DependencySearchService.getInstance(myProject).getGroupIds(pattern);
  }

  /**
   * @deprecated use {@link DependencySearchService}
   * or use {@link MavenArtifactIdCompletionContributor} for example to fill async completion variants.
   **/
  @Deprecated(forRemoval = true)
  public Set<String> getArtifactIds(String groupId) {
    return DependencySearchService.getInstance(myProject).getArtifactIds(groupId);
  }
}
