/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.incremental.CompileContext;
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
import java.io.FileFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static com.intellij.openapi.util.io.FileUtil.toSystemIndependentName;
import static com.intellij.openapi.util.text.StringUtil.trimEnd;
import static com.intellij.openapi.util.text.StringUtil.trimStart;

/**
 * @author nik
 */
public class MavenWebArtifactRootCopyingHandlerProvider extends ArtifactRootCopyingHandlerProvider {
  private static final Logger LOG = Logger.getInstance(MavenWebArtifactRootCopyingHandlerProvider.class);

  @Nullable
  @Override
  public FileCopyingHandler createCustomHandler(@NotNull JpsArtifact artifact,
                                                @NotNull File root,
                                                @NotNull File targetDirectory,
                                                @NotNull JpsPackagingElement contextElement,
                                                @NotNull JpsModel model,
                                                @NotNull BuildDataPaths buildDataPaths) {
    MavenProjectConfiguration projectConfiguration = JpsMavenExtensionService.getInstance().getMavenProjectConfiguration(buildDataPaths);
    if (projectConfiguration == null) return null;

    MavenWebArtifactConfiguration artifactResourceConfiguration = projectConfiguration.webArtifactConfigs.get(artifact.getName());
    if (artifactResourceConfiguration == null) return null;

    MavenModuleResourceConfiguration moduleResourceConfiguration = projectConfiguration.moduleConfigurations.get(artifactResourceConfiguration.moduleName);
    if (moduleResourceConfiguration == null) {
      LOG.debug("Maven resource configuration not found for module " + artifactResourceConfiguration.moduleName);
      return null;
    }

    if (contextElement instanceof JpsModuleOutputPackagingElement) {
      if (!FileUtil.namesEqual("classes", root.getName())) return null;

      return new MavenClassesCopyingHandler(root.getParentFile(), artifactResourceConfiguration, moduleResourceConfiguration);
    }

    ResourceRootConfiguration rootConfiguration = artifactResourceConfiguration.getRootConfiguration(root);
    String relativeDirInWar = null;
    if (rootConfiguration == null) {
      if (artifact.getOutputPath() != null &&
          !FileUtil.isAncestor(new File(artifactResourceConfiguration.warSourceDirectory), root, false)) {
        relativeDirInWar = FileUtil.getRelativePath(new File(artifact.getOutputPath()), targetDirectory);
        if (relativeDirInWar == null) return null;
      }
      ResourceRootConfiguration warRootConfig = getWarRootConfig(artifactResourceConfiguration, moduleResourceConfiguration);
      warRootConfig.directory = root.getPath();
      return new MavenWebArtifactCopyingHandler(warRootConfig, moduleResourceConfiguration, relativeDirInWar);
    }

    MavenResourceFileProcessor fileProcessor = new MavenResourceFileProcessor(projectConfiguration, model.getProject(), moduleResourceConfiguration);
    return new MavenWebRootCopyingHandler(fileProcessor, artifactResourceConfiguration, rootConfiguration, moduleResourceConfiguration, root);
  }

  private static class MavenWebArtifactCopyingHandler extends FilterCopyHandler {

    private final ResourceRootConfiguration myWarRootConfig;
    private final MavenModuleResourceConfiguration myModuleResourceConfig;

    public MavenWebArtifactCopyingHandler(@NotNull MavenWebArtifactConfiguration artifactConfig,
                                          @NotNull MavenModuleResourceConfiguration moduleResourceConfig,
                                          @Nullable String relativeDirectoryPath) {
      this(getWarRootConfig(artifactConfig, moduleResourceConfig), moduleResourceConfig, relativeDirectoryPath);
    }

    private MavenWebArtifactCopyingHandler(@NotNull ResourceRootConfiguration warRootConfig,
                                           @NotNull MavenModuleResourceConfiguration moduleResourceConfig,
                                           @Nullable String relativeDirectoryPath) {
      this(new MavenResourceFileFilter(new File(toSystemDependentName(warRootConfig.directory)), warRootConfig, relativeDirectoryPath),
           warRootConfig, moduleResourceConfig);
    }

    protected MavenWebArtifactCopyingHandler(@NotNull FileFilter filter,
                                             @NotNull ResourceRootConfiguration warRootConfig,
                                             @NotNull MavenModuleResourceConfiguration moduleResourceConfig) {
      super(filter);
      myWarRootConfig = warRootConfig;
      myModuleResourceConfig = moduleResourceConfig;
    }

    @Override
    public void writeConfiguration(@NotNull PrintWriter out) {
      out.print("maven hash:");
      out.println(configurationHash());
    }

    protected int configurationHash() {
      int hash = 0;
      hash = 31 * hash + myWarRootConfig.includes.hashCode();
      hash = 31 * hash + myWarRootConfig.excludes.hashCode();
      hash = 31 * hash + myWarRootConfig.computeConfigurationHash();
      hash = 31 * hash + myModuleResourceConfig.computeModuleConfigurationHash();
      return hash;
    }
  }

  private static ResourceRootConfiguration getWarRootConfig(@NotNull MavenWebArtifactConfiguration artifactConfig,
                                                            @NotNull MavenModuleResourceConfiguration moduleResourceConfig) {
    ResourceRootConfiguration rootConfig = new ResourceRootConfiguration();
    rootConfig.directory = artifactConfig.warSourceDirectory;
    rootConfig.targetPath = moduleResourceConfig.outputDirectory;
    rootConfig.includes.addAll(artifactConfig.packagingIncludes);
    rootConfig.excludes.addAll(artifactConfig.packagingExcludes);
    return rootConfig;
  }

  private static class MavenClassesCopyingHandler extends MavenWebArtifactCopyingHandler {

    private final File myTargetDir;
    private final List<ResourceRootConfiguration> myWebResources;

    public MavenClassesCopyingHandler(@NotNull File targetDir,
                                      @NotNull MavenWebArtifactConfiguration artifactConfig,
                                      @NotNull MavenModuleResourceConfiguration moduleResourceConfig) {
      this(targetDir, getWarRootConfig(artifactConfig, moduleResourceConfig),
           getWebResources(targetDir, artifactConfig), moduleResourceConfig);
    }

    protected MavenClassesCopyingHandler(@NotNull File targetDir,
                                         @NotNull ResourceRootConfiguration warRootConfig,
                                         @NotNull List<ResourceRootConfiguration> webResources,
                                         @NotNull MavenModuleResourceConfiguration moduleResourceConfig) {
      super(new ClassesFilter(targetDir, warRootConfig, webResources), warRootConfig, moduleResourceConfig);
      myTargetDir = targetDir;
      myWebResources = webResources;
    }

    /**
     * Returns list of resource root configurations that are targeted on {@code targetDir}/WEB-INF/classes and have filtering enabled
     */
    @NotNull
    private static List<ResourceRootConfiguration> getWebResources(@NotNull File targetDir,
                                                                   @NotNull MavenWebArtifactConfiguration artifactConfig) {
      String webInfClassesPath = toSystemIndependentName(new File(targetDir, "WEB-INF" + File.separator + "classes").getPath());
      List<ResourceRootConfiguration> result = ContainerUtil.newSmartList();
      for (ResourceRootConfiguration webResource : artifactConfig.webResources) {
        if (!webResource.isFiltered) continue;

        String targetPath = webResource.targetPath;
        if (StringUtil.isEmptyOrSpaces(targetPath)) continue;

        if (webInfClassesPath.equals(targetPath) || "WEB-INF/classes".equals(trimEnd(trimStart(targetPath, "/"), '/'))) {
          result.add(webResource);
        }
      }
      return result;
    }

    protected int configurationHash() {
      int hash = super.configurationHash();
      hash = 31 * hash + FileUtil.fileHashCode(myTargetDir);
      for (ResourceRootConfiguration webResource : myWebResources) {
        hash = 31 * hash + webResource.includes.hashCode();
        hash = 31 * hash + webResource.excludes.hashCode();
        hash = 31 * hash + webResource.computeConfigurationHash();
      }
      return hash;
    }

    private static class ClassesFilter extends MavenResourceFileFilter {
      private final File myTargetDir;
      private final Map<ResourceRootConfiguration, FileFilter> myWebResourcesMap = ContainerUtil.newHashMap();

      public ClassesFilter(@NotNull File targetDir,
                           @NotNull ResourceRootConfiguration warRootConfig,
                           @NotNull List<ResourceRootConfiguration> webResources) {
        super(targetDir, warRootConfig);
        myTargetDir = targetDir;
        for (ResourceRootConfiguration webResource : webResources) {
          MavenResourceFileFilter filter = new MavenResourceFileFilter(new File(toSystemDependentName(webResource.directory)), webResource);
          myWebResourcesMap.put(webResource, filter);
        }
      }

      @Override
      public boolean accept(@NotNull File file) {
        for (Map.Entry<ResourceRootConfiguration, FileFilter> entry : myWebResourcesMap.entrySet()) {
          String relPath = FileUtil.getRelativePath(new File(myTargetDir, "classes"), file);
          if (relPath == null) {
            LOG.debug("File " + file.getPath() + " is not under classes directory of " + myTargetDir.getPath());
            continue;
          }

          // do not accept files that will be copied and filtered by another copyingHandlerProvider
          File webResourceFile = new File(toSystemDependentName(entry.getKey().directory), relPath);
          if (webResourceFile.exists() && entry.getValue().accept(webResourceFile)) return false;
        }

        String relPath = FileUtil.getRelativePath(myTargetDir, file);
        return relPath != null && super.accept(new File(myTargetDir, "WEB-INF" + File.separator + relPath));
      }
    }
  }

  private static class MavenWebRootCopyingHandler extends MavenWebArtifactCopyingHandler {
    private final MavenResourceFileProcessor myFileProcessor;
    @NotNull private final ResourceRootConfiguration myRootConfiguration;
    private FileFilter myFileFilter;
    private boolean myMainWebAppRoot;

    private MavenWebRootCopyingHandler(@NotNull MavenResourceFileProcessor fileProcessor,
                                       @NotNull MavenWebArtifactConfiguration artifactConfiguration,
                                       @NotNull ResourceRootConfiguration rootConfiguration,
                                       @NotNull MavenModuleResourceConfiguration moduleResourceConfiguration,
                                       @NotNull File root) {
      super(artifactConfiguration, moduleResourceConfiguration, null);
      myFileProcessor = fileProcessor;
      myRootConfiguration = rootConfiguration;
      myFileFilter = new MavenResourceFileFilter(root, myRootConfiguration);

      //for additional resource directory 'exclude' means 'exclude from copying' but for the default webapp resource it mean 'exclude from filtering'
      myMainWebAppRoot = artifactConfiguration.warSourceDirectory.equals(trimEnd(rootConfiguration.directory, "/"));
    }

    @Override
    public void copyFile(@NotNull File from, @NotNull File to, @NotNull CompileContext context) throws IOException {
      myFileProcessor.copyFile(from, to, myRootConfiguration, context, myMainWebAppRoot ? myFileFilter : FileUtilRt.ALL_FILES);
    }

    @Override
    protected int configurationHash() {
      return myRootConfiguration.computeConfigurationHash() + super.configurationHash() * 31;
    }

    @NotNull
    @Override
    public FileFilter createFileFilter() {
      FileFilter superFilter = super.createFileFilter();
      FileFilter thisFilter = myMainWebAppRoot ? FileUtilRt.ALL_FILES : myFileFilter;
      return path -> superFilter.accept(path) && thisFilter.accept(path);
    }
  }
}
