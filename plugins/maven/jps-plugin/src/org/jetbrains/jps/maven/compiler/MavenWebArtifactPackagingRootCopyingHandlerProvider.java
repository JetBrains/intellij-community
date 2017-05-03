/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.jps.maven.compiler;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.incremental.artifacts.instructions.ArtifactRootCopyingHandlerProvider;
import org.jetbrains.jps.incremental.artifacts.instructions.FileCopyingHandler;
import org.jetbrains.jps.incremental.artifacts.instructions.FilterCopyHandler;
import org.jetbrains.jps.maven.model.JpsMavenExtensionService;
import org.jetbrains.jps.maven.model.impl.*;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.artifact.elements.JpsModuleOutputPackagingElement;
import org.jetbrains.jps.model.artifact.elements.JpsPackagingElement;

import java.io.File;
import java.io.PrintWriter;

import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static com.intellij.openapi.util.io.FileUtil.toSystemIndependentName;
import static com.intellij.openapi.util.text.StringUtil.trimEnd;

/**
 * @author ibessonov
 */
public class MavenWebArtifactPackagingRootCopyingHandlerProvider extends ArtifactRootCopyingHandlerProvider {

  private static final Logger LOG = Logger.getInstance(MavenWebArtifactPackagingRootCopyingHandlerProvider.class);

  @Nullable
  @Override
  public FileCopyingHandler createCustomHandler(@NotNull JpsArtifact artifact,
                                                @NotNull File root,
                                                @NotNull JpsPackagingElement contextElement,
                                                @NotNull JpsModel model,
                                                @NotNull BuildDataPaths buildDataPaths) {
    if (contextElement instanceof JpsModuleOutputPackagingElement) return null;

    MavenProjectConfiguration projectConfig = JpsMavenExtensionService.getInstance().getMavenProjectConfiguration(buildDataPaths);
    if (projectConfig == null) return null;

    MavenWebArtifactConfiguration artifactConfig = projectConfig.webArtifactConfigs.get(artifact.getName());
    if (artifactConfig == null) return null;

    MavenModuleResourceConfiguration moduleResourceConfig = projectConfig.moduleConfigurations.get(artifactConfig.moduleName);
    if (moduleResourceConfig == null) {
      LOG.debug("Maven resource configuration not found for module " + artifactConfig.moduleName);
      return null;
    }

    ResourceRootConfiguration rootConfig = new ResourceRootConfiguration();
    rootConfig.directory = trimEnd(toSystemIndependentName(moduleResourceConfig.directory), '/') + "/src/main/webapp";
    rootConfig.targetPath = moduleResourceConfig.outputDirectory;
    rootConfig.includes.addAll(artifactConfig.packagingIncludes);
    rootConfig.excludes.addAll(artifactConfig.packagingExcludes);

    return new MavenWebArtifactPackagingRootCopyingHandler(rootConfig, moduleResourceConfig);
  }

  private static class MavenWebArtifactPackagingRootCopyingHandler extends FilterCopyHandler {

    private final @NotNull ResourceRootConfiguration myRootConfig;
    private final @NotNull MavenModuleResourceConfiguration myModuleResourceConfig;

    public MavenWebArtifactPackagingRootCopyingHandler(@NotNull ResourceRootConfiguration rootConfig,
                                                       @NotNull MavenModuleResourceConfiguration moduleResourceConfig) {
      super(new MavenResourceFileFilter(new File(toSystemDependentName(rootConfig.directory)), rootConfig));
      myRootConfig = rootConfig;
      myModuleResourceConfig = moduleResourceConfig;
    }

    @Override
    public void writeConfiguration(@NotNull PrintWriter out) {
      out.print("maven packaging hash:");
      int hash = 0;
      hash = 31 * hash + myRootConfig.includes.hashCode();
      hash = 31 * hash + myRootConfig.excludes.hashCode();
      hash = 31 * hash + myRootConfig.computeConfigurationHash();
      hash = 31 * hash + myModuleResourceConfig.computeModuleConfigurationHash();
      out.println(hash);
    }
  }
}
