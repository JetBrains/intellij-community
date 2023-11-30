// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.indices;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.server.AddArtifactResponse;

import java.io.File;
import java.util.Collection;
import java.util.List;

public interface MavenIndex extends MavenSearchIndex, MavenGAVIndex, MavenArchetypeContainer, MavenUpdatableIndex {

  @Topic.AppLevel
  Topic<IndexListener> INDEX_IS_BROKEN =
    new Topic<>("Maven Index Broken Listener", IndexListener.class);

  @NotNull List<AddArtifactResponse> tryAddArtifacts(@NotNull Collection<File> artifactFiles);
}
