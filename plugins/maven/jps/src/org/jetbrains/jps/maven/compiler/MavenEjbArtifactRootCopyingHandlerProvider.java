// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.maven.compiler;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.incremental.artifacts.instructions.ArtifactRootCopyingHandlerProvider;
import org.jetbrains.jps.incremental.artifacts.instructions.FileCopyingHandler;
import org.jetbrains.jps.incremental.artifacts.instructions.FilterCopyHandler;
import org.jetbrains.jps.maven.model.JpsMavenExtensionService;
import org.jetbrains.jps.maven.model.impl.MavenEjbClientConfiguration;
import org.jetbrains.jps.maven.model.impl.MavenProjectConfiguration;
import org.jetbrains.jps.maven.model.impl.MavenResourceFileFilter;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.artifact.elements.JpsPackagingElement;
import org.jetbrains.jps.model.ex.JpsElementBase;

import java.io.File;

public class MavenEjbArtifactRootCopyingHandlerProvider extends ArtifactRootCopyingHandlerProvider {
  @Override
  public @Nullable FileCopyingHandler createCustomHandler(@NotNull JpsArtifact artifact,
                                                          @NotNull File root,
                                                          @NotNull File targetDirectory,
                                                          @NotNull JpsPackagingElement contextElement,
                                                          @NotNull JpsModel model,
                                                          @NotNull BuildDataPaths buildDataPaths) {
    MavenProjectConfiguration projectConfiguration = JpsMavenExtensionService.getInstance().getMavenProjectConfiguration(buildDataPaths);
    if (projectConfiguration == null) return null;

    MavenEjbClientConfiguration ejbCfg = projectConfiguration.ejbClientArtifactConfigs.get(artifact.getName());
    if (ejbCfg == null) {
      JpsArtifact parentArtifact = findParentArtifact(contextElement);
      if (parentArtifact != null) {
        ejbCfg = projectConfiguration.ejbClientArtifactConfigs.get(parentArtifact.getName());
      }
    }
    return ejbCfg == null ? null : new FilterCopyHandler(new MavenResourceFileFilter(root, ejbCfg));
  }

  private static JpsArtifact findParentArtifact(JpsElement element) {
    if (element instanceof JpsElementBase) {
      JpsElementBase parent = ((JpsElementBase<?>)element).getParent();
      if (parent instanceof JpsArtifact) {
        return (JpsArtifact)parent;
      }
      if (parent != null) {
        return findParentArtifact(parent);
      }
    }
    return null;
  }
}
