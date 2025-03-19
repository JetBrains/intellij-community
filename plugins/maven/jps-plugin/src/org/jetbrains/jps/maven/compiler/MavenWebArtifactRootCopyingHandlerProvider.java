// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.maven.compiler;

import com.dynatrace.hash4j.hashing.HashFunnel;
import com.dynatrace.hash4j.hashing.HashSink;
import com.dynatrace.hash4j.hashing.Hashing;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.FileHashUtil;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static com.intellij.openapi.util.io.FileUtil.toSystemIndependentName;
import static com.intellij.openapi.util.text.StringUtil.trimEnd;
import static com.intellij.openapi.util.text.StringUtil.trimStart;

public final class MavenWebArtifactRootCopyingHandlerProvider extends ArtifactRootCopyingHandlerProvider {
  private static final Logger LOG = Logger.getInstance(MavenWebArtifactRootCopyingHandlerProvider.class);

  @Override
  public @Nullable FileCopyingHandler createCustomHandler(@NotNull JpsArtifact artifact,
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
      if (root.isFile()) {
        root = root.getParentFile();
      }
      warRootConfig.directory = root.getPath();
      if (relativeDirInWar == null) {
        warRootConfig.includes.addAll(artifactResourceConfiguration.warSourceIncludes);
        warRootConfig.excludes.addAll(artifactResourceConfiguration.warSourceExcludes);
      }
      return new MavenWebArtifactCopyingHandler(warRootConfig, moduleResourceConfiguration, relativeDirInWar);
    }

    MavenResourceFileProcessor fileProcessor = new MavenResourceFileProcessor(projectConfiguration, model.getProject(), moduleResourceConfiguration);
    return new MavenWebRootCopyingHandler(fileProcessor, artifactResourceConfiguration, rootConfiguration, moduleResourceConfiguration, root);
  }

  private static class MavenWebArtifactCopyingHandler extends FilterCopyHandler {
    private final ResourceRootConfiguration myWarRootConfig;
    private final MavenModuleResourceConfiguration myModuleResourceConfig;

    MavenWebArtifactCopyingHandler(@NotNull MavenWebArtifactConfiguration artifactConfig,
                                          @NotNull MavenModuleResourceConfiguration moduleResourceConfig,
                                          @Nullable String relativeDirectoryPath) {
      this(getWarRootConfig(artifactConfig, moduleResourceConfig), moduleResourceConfig, relativeDirectoryPath);
    }

    private MavenWebArtifactCopyingHandler(@NotNull ResourceRootConfiguration warRootConfig,
                                           @NotNull MavenModuleResourceConfiguration moduleResourceConfig,
                                           @Nullable String relativeDirectoryPath) {
      this(new MavenResourceFileFilter(new File(toSystemDependentName(warRootConfig.directory)), warRootConfig, relativeDirectoryPath).acceptingWebXml(),
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
    public void writeConfiguration(@NotNull HashSink hash) {
      hash.putString("maven hash:");
      configurationHash(hash);
    }

    protected void configurationHash(@NotNull HashSink hash) {
      hash.putUnorderedIterable(myWarRootConfig.includes, HashFunnel.forString(), Hashing.komihash5_0());
      hash.putUnorderedIterable(myWarRootConfig.excludes, HashFunnel.forString(), Hashing.komihash5_0());
      myWarRootConfig.computeConfigurationHash(hash);
      myModuleResourceConfig.computeModuleConfigurationHash(hash);
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

    MavenClassesCopyingHandler(@NotNull File targetDir,
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
    private static @NotNull List<ResourceRootConfiguration> getWebResources(@NotNull File targetDir,
                                                                   @NotNull MavenWebArtifactConfiguration artifactConfig) {
      String webInfClassesPath = toSystemIndependentName(new File(targetDir, "WEB-INF" + File.separator + "classes").getPath());
      List<ResourceRootConfiguration> result = new SmartList<>();
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

    @Override
    protected void configurationHash(@NotNull HashSink hash) {
      super.configurationHash(hash);

      FileHashUtil.computePathHashCode(myTargetDir.toPath().toAbsolutePath().normalize().toString(), hash);
      for (ResourceRootConfiguration webResource : myWebResources) {
        hash.putUnorderedIterable(webResource.includes, HashFunnel.forString(), Hashing.komihash5_0());
        hash.putUnorderedIterable(webResource.excludes, HashFunnel.forString(), Hashing.komihash5_0());
        webResource.computeConfigurationHash(hash);
      }
    }

    private static class ClassesFilter extends MavenResourceFileFilter {
      private final File myTargetDir;
      private final Map<ResourceRootConfiguration, FileFilter> myWebResourcesMap = new HashMap<>();

      ClassesFilter(@NotNull File targetDir,
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

  private static final class MavenWebRootCopyingHandler extends MavenWebArtifactCopyingHandler {
    private final MavenResourceFileProcessor myFileProcessor;
    private final @NotNull ResourceRootConfiguration myRootConfiguration;
    private final FileFilter myFilteringFilter;
    private final FileFilter myCopyingFilter;

    private MavenWebRootCopyingHandler(@NotNull MavenResourceFileProcessor fileProcessor,
                                       @NotNull MavenWebArtifactConfiguration artifactConfiguration,
                                       @NotNull ResourceRootConfiguration rootConfiguration,
                                       @NotNull MavenModuleResourceConfiguration moduleResourceConfiguration,
                                       @NotNull File root) {
      super(artifactConfiguration, moduleResourceConfiguration, null);
      myFileProcessor = fileProcessor;
      myRootConfiguration = rootConfiguration;

      FileFilter superFileFilter = super.createFileFilter();
      FileFilter rootFileFilter = new MavenResourceFileFilter(root, myRootConfiguration).acceptingWebXml();

      // for additional resource directory 'exclude' means 'exclude from copying'
      // but for the default webapp resource it means 'exclude from filtering'
      boolean isMainWebAppRoot = FileUtil.pathsEqual(artifactConfiguration.warSourceDirectory, rootConfiguration.directory);

      if (isMainWebAppRoot) {
        myCopyingFilter = superFileFilter;
      }
      else {
        myCopyingFilter = path -> superFileFilter.accept(path) && rootFileFilter.accept(path);
      }

      Set<String> nonFilteredFileExtensions = artifactConfiguration.nonFilteredFileExtensions;
      FileFilter extensionsFileFilter = file -> !nonFilteredFileExtensions.contains(FileUtilRt.getExtension(file.getName()));
      if (isMainWebAppRoot) {
        myFilteringFilter = file -> rootFileFilter.accept(file) && extensionsFileFilter.accept(file);
      }
      else {
        myFilteringFilter = extensionsFileFilter;
      }
    }

    @Override
    public void copyFile(@NotNull File from, @NotNull File to, @NotNull CompileContext context) throws IOException {
      myFileProcessor.copyFile(from, to, myRootConfiguration, context, myFilteringFilter);
    }

    @Override
    protected void configurationHash(@NotNull HashSink hash) {
      myRootConfiguration.computeConfigurationHash(hash);
      super.configurationHash(hash);
    }

    @Override
    public @NotNull FileFilter createFileFilter() {
      return myCopyingFilter;
    }
  }
}
