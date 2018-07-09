/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.maven.model.impl;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import gnu.trove.THashSet;
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
public class MavenResourcesTarget extends ModuleBasedTarget<MavenResourceRootDescriptor> {

  MavenResourcesTarget(final MavenResourcesTargetType type, @NotNull JpsModule module) {
    super(type, module);
  }

  @Override
  public String getId() {
    return myModule.getName();
  }

  @Override
  public Collection<BuildTarget<?>> computeDependencies(BuildTargetRegistry targetRegistry, TargetOutputIndex outputIndex) {
    return Collections.emptyList();
  }

  @Override
  public boolean isCompiledBeforeModuleLevelBuilders() {
    return true;
  }

  @NotNull
  @Override
  public List<MavenResourceRootDescriptor> computeRootDescriptors(JpsModel model, ModuleExcludeIndex index, IgnoredFileIndex ignoredFileIndex, BuildDataPaths dataPaths) {
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

  public boolean isTests() {
    return ((MavenResourcesTargetType)getTargetType()).isTests();
  }

  @Nullable
  @Override
  public MavenResourceRootDescriptor findRootDescriptor(String rootId, BuildRootIndex rootIndex) {
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
  public Collection<File> getOutputRoots(CompileContext context) {
    MavenModuleResourceConfiguration configuration =
      getModuleResourcesConfiguration(context.getProjectDescriptor().dataManager.getDataPaths());
    if (configuration == null) return Collections.emptyList();

    final Set<File> result = new THashSet<>(FileUtil.FILE_HASHING_STRATEGY);
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
  public void writeConfiguration(ProjectDescriptor pd, PrintWriter out) {
    final BuildDataPaths dataPaths = pd.getTargetsState().getDataPaths();
    final MavenModuleResourceConfiguration configuration = getModuleResourcesConfiguration(dataPaths);
    if (configuration != null) {
      out.write(Integer.toHexString(configuration.computeConfigurationHash(isTests())));
    }
  }
}
