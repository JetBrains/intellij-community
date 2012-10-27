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
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.incremental.ModuleBuildTarget;
import org.jetbrains.jps.indices.IgnoredFileIndex;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.maven.model.JpsMavenExtensionService;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.io.PrintWriter;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/21/12
 */
public class MavenResourcesTarget extends ModuleBasedTarget<MavenResourceRootDescriptor> {

  private static final Comparator<String> STRING_COMPARATOR = new Comparator<String>() {
    @Override
    public int compare(String o1, String o2) {
      return o1.compareTo(o2);
    }
  };
  private static final Comparator<ResourceRootConfiguration> ROOT_CONFIG_COMPARATOR = new Comparator<ResourceRootConfiguration>() {
    @Override
    public int compare(ResourceRootConfiguration o1, ResourceRootConfiguration o2) {
      return STRING_COMPARATOR.compare(o1.directory, o2.directory);
    }
  };

  MavenResourcesTarget(final MavenResourcesTargetType type, @NotNull JpsModule module) {
    super(type, module);
  }

  @Override
  public String getId() {
    return myModule.getName();
  }

  @Override
  public Collection<BuildTarget<?>> computeDependencies(BuildTargetRegistry targetRegistry) {
    final JavaModuleBuildTargetType targetType = isTests() ? JavaModuleBuildTargetType.TEST : JavaModuleBuildTargetType.PRODUCTION;
    return Collections.<BuildTarget<?>>singletonList(new ModuleBuildTarget(myModule, targetType));
  }

  @NotNull
  @Override
  public List<MavenResourceRootDescriptor> computeRootDescriptors(JpsModel model, ModuleExcludeIndex index, IgnoredFileIndex ignoredFileIndex, BuildDataPaths dataPaths) {
    // todo: should we honor ignored and excluded roots here?
    final List<MavenResourceRootDescriptor> result = new ArrayList<MavenResourceRootDescriptor>();
    for (ResourceRootConfiguration resource : getRootConfigurations(dataPaths)) {
      result.add(new MavenResourceRootDescriptor(this, resource));
    }
    return result;
  }

  private List<ResourceRootConfiguration> getRootConfigurations(BuildDataPaths dataPaths) {
    return getRootConfigurations(getModuleResourcesConfiguration(dataPaths));
  }

  private List<ResourceRootConfiguration> getRootConfigurations(@Nullable MavenModuleResourceConfiguration moduleConfig) {
    if (moduleConfig != null) {
      return isTests() ? moduleConfig.myTestResources : moduleConfig.myResources;
    }
    return Collections.emptyList();
  }

  public MavenModuleResourceConfiguration getModuleResourcesConfiguration(BuildDataPaths dataPaths) {
    final MavenProjectConfiguration projectConfig = JpsMavenExtensionService.getInstance().getMavenProjectConfiguration(dataPaths);
    return projectConfig.moduleConfigurations.get(myModule.getName());
  }

  public boolean isTests() {
    return ((MavenResourcesTargetType)getTargetType()).isTests();
  }

  @Nullable
  @Override
  public BuildRootDescriptor findRootDescriptor(String rootId, BuildRootIndex rootIndex) {
    for (BuildRootDescriptor descriptor : rootIndex.getTargetRoots(this, null)) {
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
  public Collection<File> getOutputDirs(BuildDataPaths paths) {
    final Set<File> result = new THashSet<File>(FileUtil.FILE_HASHING_STRATEGY);
    final File moduleOutput = getModuleOutputDir();
    for (ResourceRootConfiguration resConfig : getRootConfigurations(paths)) {
      final File output = getOutputDir(moduleOutput, resConfig);
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
  public static File getOutputDir(@Nullable File moduleOutput, ResourceRootConfiguration config) {
    if (moduleOutput == null) {
      return null;
    }
    String targetPath = config.targetPath;
    if (StringUtil.isEmptyOrSpaces(targetPath)) {
      return moduleOutput;
    }
    final File targetPathFile = new File(targetPath);
    return targetPathFile.isAbsolute()? targetPathFile : new File(moduleOutput, targetPath);
  }

  @Override
  public void writeConfiguration(PrintWriter out, BuildDataPaths dataPaths, BuildRootIndex buildRootIndex) {
    final MavenModuleResourceConfiguration configuration = getModuleResourcesConfiguration(dataPaths);
    if (configuration != null) {
      out.write(configuration.escapeString);

      out.write(String.valueOf(configuration.myProperties.hashCode()));

      writeStringCollection(out, configuration.getFiltetingExcludedExtensions());

      final List<ResourceRootConfiguration> sorted = new ArrayList<ResourceRootConfiguration>(getRootConfigurations(configuration));
      Collections.sort(sorted, ROOT_CONFIG_COMPARATOR);
      for (ResourceRootConfiguration root : sorted) {
        writeResourceRoot(out, root);
      }
    }
  }

  private static void writeResourceRoot(PrintWriter out, ResourceRootConfiguration rootConfig) {
    out.write(rootConfig.directory);
    out.write(rootConfig.targetPath == null? "" : FileUtil.toSystemIndependentName(rootConfig.targetPath));
    out.write(rootConfig.isFiltered? "f" : "n");
    writeStringCollection(out, rootConfig.includes);
    writeStringCollection(out, rootConfig.excludes);
  }

  private static void writeStringCollection(PrintWriter out, Collection<String> collection) {
    List<String> sorted = new ArrayList<String>(collection);
    Collections.sort(sorted, STRING_COMPARATOR);
    for (String extension : sorted) {
      out.write(extension);
    }
  }
}
