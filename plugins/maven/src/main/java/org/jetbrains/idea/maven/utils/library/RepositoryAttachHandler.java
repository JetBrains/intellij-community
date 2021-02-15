// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.utils.library;

import com.intellij.jarRepository.JarRepositoryManager;
import com.intellij.jarRepository.RemoteRepositoryDescription;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.NewLibraryConfiguration;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenRepositoryInfo;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This class is deprecated and is to be removed in future releases
 * @deprecated Use {@link JarRepositoryManager} instead
 */
@ApiStatus.ScheduledForRemoval(inVersion = "2020.3")
@Deprecated
public final class RepositoryAttachHandler {

  @Nullable
  public static NewLibraryConfiguration chooseLibraryAndDownload(final @NotNull Project project, final @Nullable String initialFilter, JComponent parentComponent) {
    return JarRepositoryManager.chooseLibraryAndDownload(project, initialFilter, parentComponent);
  }

  @Nullable
  public static NewLibraryConfiguration resolveAndDownload(final Project project,
                                                           final String coord,
                                                           boolean attachJavaDoc,
                                                           boolean attachSources,
                                                           @Nullable final String copyTo,
                                                           List<? extends MavenRepositoryInfo> repositories) {
    final ArrayList<RemoteRepositoryDescription> repos =
      repositories.stream().map(info -> toRemoteRepositoryDescription(info)).collect(Collectors.toCollection(ArrayList::new));
    return JarRepositoryManager.resolveAndDownload(project, coord, attachSources, attachJavaDoc, true, copyTo, repos);
  }


  @NotNull
  private static RemoteRepositoryDescription toRemoteRepositoryDescription(MavenRepositoryInfo repo) {
    return new RemoteRepositoryDescription(repo.getId(), repo.getName(), repo.getUrl());
  }
}
