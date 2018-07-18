/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.utils.library;

import com.intellij.jarRepository.JarRepositoryManager;
import com.intellij.jarRepository.RemoteRepositoryDescription;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.NewLibraryConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenRepositoryInfo;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This class is deprecated and is to be removed in future releases
 * Use {@link JarRepositoryManager} instead
 */
@Deprecated
public class RepositoryAttachHandler {

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
                                                           List<MavenRepositoryInfo> repositories) {
    final ArrayList<RemoteRepositoryDescription> repos =
      repositories.stream().map(info -> toRemoteRepositoryDescription(info)).collect(Collectors.toCollection(ArrayList::new));
    return JarRepositoryManager.resolveAndDownload(project, coord, attachSources, attachJavaDoc, true, copyTo, repos);
  }


  @NotNull
  private static RemoteRepositoryDescription toRemoteRepositoryDescription(MavenRepositoryInfo repo) {
    return new RemoteRepositoryDescription(repo.getId(), repo.getName(), repo.getUrl());
  }
}
