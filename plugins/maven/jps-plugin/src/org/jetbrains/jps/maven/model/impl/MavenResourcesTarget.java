// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.maven.model.impl;

import com.dynatrace.hash4j.hashing.HashSink;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.containers.FileCollectionFactory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.*;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.indices.IgnoredFileIndex;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.maven.model.JpsMavenExtensionService;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.File;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 */
@ApiStatus.Internal
public final class MavenResourcesTarget extends ModuleBasedTarget<MavenResourceRootDescriptor> implements BuildTargetHashSupplier {
  MavenResourcesTarget(@NotNull MavenResourcesTargetType type, @NotNull JpsModule module) {
    super(type, module);
  }

  @Override
  public @NotNull String getId() {
    return myModule.getName();
  }

  @Override
  public @NotNull Collection<BuildTarget<?>> computeDependencies(@NotNull BuildTargetRegistry targetRegistry, @NotNull TargetOutputIndex outputIndex) {
    return Collections.emptyList();
  }

  @Override
  public boolean isCompiledBeforeModuleLevelBuilders() {
    return true;
  }

  @Override
  public @NotNull List<MavenResourceRootDescriptor> computeRootDescriptors(@NotNull JpsModel model, @NotNull ModuleExcludeIndex index, @NotNull IgnoredFileIndex ignoredFileIndex, @NotNull BuildDataPaths dataPaths) {
    // todo: should we honor ignored and excluded roots here?
    final List<MavenResourceRootDescriptor> result = new ArrayList<>();

    MavenModuleResourceConfiguration moduleConfig = getModuleResourcesConfiguration(dataPaths);
    if (moduleConfig == null) return Collections.emptyList();

    int i = 0;

    for (ResourceRootConfiguration resource : getRootConfigurations(moduleConfig)) {
      result.add(new MavenResourceRootDescriptor(this, resource, i++, moduleConfig.overwrite));
    }
    return result;
  }

  private Collection<ResourceRootConfiguration> getRootConfigurations(@Nullable MavenModuleResourceConfiguration moduleConfig) {
    if (moduleConfig != null) {
      return isTests() ? moduleConfig.testResources : moduleConfig.resources;
    }
    return Collections.emptyList();
  }

  public @Nullable MavenModuleResourceConfiguration getModuleResourcesConfiguration(BuildDataPaths dataPaths) {
    final MavenProjectConfiguration projectConfig = JpsMavenExtensionService.getInstance().getMavenProjectConfiguration(dataPaths);
    if (projectConfig == null) return null;
    return projectConfig.moduleConfigurations.get(myModule.getName());
  }

  @Override
  public boolean isTests() {
    return ((MavenResourcesTargetType)getTargetType()).isTests();
  }

  @Override
  public @Nullable MavenResourceRootDescriptor findRootDescriptor(@NotNull String rootId, @NotNull BuildRootIndex rootIndex) {
    for (MavenResourceRootDescriptor descriptor : rootIndex.getTargetRoots(this, null)) {
      if (descriptor.getRootId().equals(rootId)) {
        return descriptor;
      }
    }
    return null;
  }

  @Override
  public @NotNull String getPresentableName() {
    return getTargetType().getTypeId() + ":" + myModule.getName();
  }

  @Override
  public @NotNull Collection<File> getOutputRoots(@NotNull CompileContext context) {
    MavenModuleResourceConfiguration configuration =
      getModuleResourcesConfiguration(context.getProjectDescriptor().dataManager.getDataPaths());
    if (configuration == null) return Collections.emptyList();

    Set<File> result = FileCollectionFactory.createCanonicalFileSet();
    final File moduleOutput = getModuleOutputDir();
    for (ResourceRootConfiguration resConfig : getRootConfigurations(configuration)) {
      final File output = getOutputDir(moduleOutput, resConfig, configuration.outputDirectory);
      if (output != null) {
        result.add(output);
      }
    }
    return result;
  }

  public @Nullable File getModuleOutputDir() {
    return JpsJavaExtensionService.getInstance().getOutputDirectory(myModule, isTests());
  }

  public static @Nullable File getOutputDir(@Nullable File moduleOutput, ResourceRootConfiguration config, @Nullable String outputDirectory) {
    if(outputDirectory != null) {
      moduleOutput = JpsPathUtil.urlToFile(outputDirectory);
    }

    if (moduleOutput == null) {
      return null;
    }
    String targetPath = config.targetPath;
    if (targetPath == null || targetPath.isBlank()) {
      return moduleOutput;
    }

    File targetPathFile = new File(targetPath);
    File outputFile = targetPathFile.isAbsolute() ? targetPathFile : new File(moduleOutput, targetPath);
    return new File(FileUtilRt.toCanonicalPath(outputFile.getPath(), File.separatorChar, true));
  }

  @Override
  public void computeConfigurationDigest(@NotNull ProjectDescriptor projectDescriptor, @NotNull HashSink hash) {
    BuildDataPaths dataPaths = projectDescriptor.dataManager.getDataPaths();
    MavenModuleResourceConfiguration configuration = getModuleResourcesConfiguration(dataPaths);
    if (configuration == null) {
      hash.putBoolean(false);
    }
    else {
      hash.putBoolean(true);
      configuration.computeConfigurationHash(isTests(), hash);
    }
  }
}
