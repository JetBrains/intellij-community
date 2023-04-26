// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.maven.model.impl;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.FileCollectionFactory;
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
import java.io.PrintWriter;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 */
public final class MavenResourcesTarget extends ModuleBasedTarget<MavenResourceRootDescriptor> {
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

  @NotNull
  @Override
  public List<MavenResourceRootDescriptor> computeRootDescriptors(@NotNull JpsModel model, @NotNull ModuleExcludeIndex index, @NotNull IgnoredFileIndex ignoredFileIndex, @NotNull BuildDataPaths dataPaths) {
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

  @Nullable
  public MavenModuleResourceConfiguration getModuleResourcesConfiguration(BuildDataPaths dataPaths) {
    final MavenProjectConfiguration projectConfig = JpsMavenExtensionService.getInstance().getMavenProjectConfiguration(dataPaths);
    if (projectConfig == null) return null;
    return projectConfig.moduleConfigurations.get(myModule.getName());
  }

  @Override
  public boolean isTests() {
    return ((MavenResourcesTargetType)getTargetType()).isTests();
  }

  @Nullable
  @Override
  public MavenResourceRootDescriptor findRootDescriptor(@NotNull String rootId, @NotNull BuildRootIndex rootIndex) {
    for (MavenResourceRootDescriptor descriptor : rootIndex.getTargetRoots(this, null)) {
      if (descriptor.getRootId().equals(rootId)) {
        return descriptor;
      }
    }
    return null;
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return getTargetType().getTypeId() + ":" + myModule.getName();
  }

  @NotNull
  @Override
  public Collection<File> getOutputRoots(@NotNull CompileContext context) {
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

  @Nullable
  public File getModuleOutputDir() {
    return JpsJavaExtensionService.getInstance().getOutputDirectory(myModule, isTests());
  }

  @Nullable
  public static File getOutputDir(@Nullable File moduleOutput, ResourceRootConfiguration config, @Nullable String outputDirectory) {
    if(outputDirectory != null) {
      moduleOutput = JpsPathUtil.urlToFile(outputDirectory);
    }

    if (moduleOutput == null) {
      return null;
    }
    String targetPath = config.targetPath;
    if (StringUtil.isEmptyOrSpaces(targetPath)) {
      return moduleOutput;
    }
    final File targetPathFile = new File(targetPath);
    final File outputFile = targetPathFile.isAbsolute() ? targetPathFile : new File(moduleOutput, targetPath);
    return new File(FileUtil.toCanonicalPath(outputFile.getPath()));
  }

  @Override
  public void writeConfiguration(@NotNull ProjectDescriptor pd, @NotNull PrintWriter out) {
    final BuildDataPaths dataPaths = pd.getTargetsState().getDataPaths();
    final MavenModuleResourceConfiguration configuration = getModuleResourcesConfiguration(dataPaths);
    if (configuration != null) {
      out.write(Integer.toHexString(configuration.computeConfigurationHash(isTests())));
    }
  }
}
