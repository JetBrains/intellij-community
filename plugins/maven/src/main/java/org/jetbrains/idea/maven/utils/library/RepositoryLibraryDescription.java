/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.util.containers.HashMap;
import icons.MavenIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenRepositoryInfo;

import javax.swing.*;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public abstract class RepositoryLibraryDescription {
  protected static final ExtensionPointName<RepositoryLibraryDescription> EP_NAME
    = ExtensionPointName.create("org.jetbrains.idea.maven.repositoryLibrary");
  private static final List<MavenRepositoryInfo> defaultRemoteRepositories = Collections.singletonList(new MavenRepositoryInfo(
    "maven.central",
    "maven.central",
    "http://repo1.maven.org/maven2"));
  private static Map<String, RepositoryLibraryDescription> registeredLibraries;

  @NotNull
  public static synchronized RepositoryLibraryDescription findDescription(@NotNull final RepositoryLibraryProperties properties) {
    if (registeredLibraries == null) {
      registeredLibraries = new HashMap<String, RepositoryLibraryDescription>();
      for (RepositoryLibraryDescription description : EP_NAME.getExtensions()) {
        String id = description.getGroupId() + ":" + description.getArtifactId();
        RepositoryLibraryDescription existDescription = registeredLibraries.get(id);
        if (existDescription == null || existDescription.getWeight() >= description.getWeight()) {
          registeredLibraries.put(id, description);
        }
      }
    }
    String id = properties.getGroupId() + ":" + properties.getArtifactId();
    RepositoryLibraryDescription description = registeredLibraries.get(id);
    if (description != null) {
      return description;
    }
    return new RepositoryLibraryDescription() {
      @NotNull
      @Override
      public String getGroupId() {
        return properties.getGroupId();
      }

      @NotNull
      @Override
      public String getArtifactId() {
        return properties.getArtifactId();
      }

      @NotNull
      @Override
      public String getDisplayName() {
        return properties.getGroupId() + ":" + properties.getArtifactId();
      }

      @NotNull
      @Override
      public Icon getIcon() {
        return MavenIcons.MavenLogo;
      }
    };
  }

  @NotNull
  public abstract String getGroupId();

  @NotNull
  public abstract String getArtifactId();

  @NotNull
  public abstract String getDisplayName();

  @NotNull
  public abstract Icon getIcon();

  @Nullable
  public DependencyScope getSuggestedScope() {
    return null;
  }

  @NotNull
  List<MavenRepositoryInfo> getRemoteRepositories() {
    return defaultRemoteRepositories;
  }

  // One library could have more then one description - for ex. in different plugins
  // In this case heaviest description will be used
  public int getWeight() {
    return 1000;
  }

  public RepositoryLibraryProperties createDefaultProperties() {
    return new RepositoryLibraryProperties(getGroupId(), getArtifactId(), RepositoryUtils.LatestVersionId);
  }
}
