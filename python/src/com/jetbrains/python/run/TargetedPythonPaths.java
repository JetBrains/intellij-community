// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run;

import com.intellij.execution.target.TargetEnvironment;
import com.intellij.execution.target.TargetEnvironmentRequest;
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest;
import com.intellij.execution.target.value.TargetEnvironmentFunctions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.PersistentLibraryKind;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PlatformUtils;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.facet.LibraryContributingFacet;
import com.jetbrains.python.library.PythonLibraryType;
import com.jetbrains.python.sdk.PythonSdkAdditionalData;
import com.jetbrains.python.sdk.flavors.JythonSdkFlavor;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.function.Function;

import static com.jetbrains.python.sdk.PythonEnvUtil.PYTHONPATH;

public class TargetedPythonPaths {
  private TargetedPythonPaths() {
  }

  public static void initPythonPath(@NotNull Map<String, Function<TargetEnvironment, String>> envs,
                                    boolean passParentEnvs,
                                    @NotNull Collection<Function<TargetEnvironment, String>> pythonPathList,
                                    @NotNull TargetEnvironmentRequest targetEnvironmentRequest) {
    // TODO [Targets API] Passing parent envs logic should be moved somewhere else
    if (passParentEnvs && (targetEnvironmentRequest instanceof LocalTargetEnvironmentRequest) && !envs.containsKey(PYTHONPATH)) {
      appendSystemPythonPath(pythonPathList);
    }
    PythonScripts.appendToPythonPath(envs, pythonPathList, targetEnvironmentRequest.getTargetPlatform());
  }

  private static void appendSystemPythonPath(@NotNull Collection<Function<TargetEnvironment, String>> pythonPath) {
    String syspath = System.getenv(PYTHONPATH);
    if (syspath != null) {
      pythonPath.addAll(ContainerUtil.map(syspath.split(File.pathSeparator), s -> TargetEnvironmentFunctions.constant(s)));
    }
  }

  @NotNull
  public static Collection<Function<TargetEnvironment, String>> collectPythonPath(@NotNull TargetEnvironmentRequest targetEnvironmentRequest,
                                                                                  @Nullable Module module,
                                                                                  @Nullable String sdkHome,
                                                                                  boolean shouldAddContentRoots,
                                                                                  boolean shouldAddSourceRoots,
                                                                                  boolean isDebug) {
    Set<Function<TargetEnvironment, String>> pythonPath =
      new LinkedHashSet<>(collectPythonPath(targetEnvironmentRequest, module, shouldAddContentRoots, shouldAddSourceRoots));

    if (isDebug && PythonSdkFlavor.getFlavor(sdkHome) instanceof JythonSdkFlavor) {
      //that fixes Jython problem changing sys.argv on execfile, see PY-8164
      for (String helpersResource : Arrays.asList("pycharm", "pydev")) {
        String helperPath = PythonHelpersLocator.getHelperPath(helpersResource);
        Function<TargetEnvironment, String> targetHelperPath =
          TargetEnvironmentFunctions.getTargetEnvironmentValueForLocalPath(targetEnvironmentRequest, helperPath);
        pythonPath.add(targetHelperPath);
      }
    }

    return pythonPath;
  }

  @NotNull
  public static Collection<Function<TargetEnvironment, String>> collectPythonPath(@NotNull TargetEnvironmentRequest targetEnvironmentRequest,
                                                                                  @Nullable Module module,
                                                                                  boolean addContentRoots,
                                                                                  boolean addSourceRoots) {
    Collection<Function<TargetEnvironment, String>> pythonPathList = new LinkedHashSet<>();
    if (module != null) {
      Set<Module> dependencies = new HashSet<>();
      ModuleUtilCore.getDependencies(module, dependencies);

      if (addContentRoots) {
        addRoots(targetEnvironmentRequest, pythonPathList, ModuleRootManager.getInstance(module).getContentRoots());
        for (Module dependency : dependencies) {
          addRoots(targetEnvironmentRequest, pythonPathList, ModuleRootManager.getInstance(dependency).getContentRoots());
        }
      }
      if (addSourceRoots) {
        addRoots(targetEnvironmentRequest, pythonPathList, ModuleRootManager.getInstance(module).getSourceRoots());
        for (Module dependency : dependencies) {
          addRoots(targetEnvironmentRequest, pythonPathList, ModuleRootManager.getInstance(dependency).getSourceRoots());
        }
      }

      addLibrariesFromModule(targetEnvironmentRequest, module, pythonPathList);
      addRootsFromModule(module, pythonPathList);
      for (Module dependency : dependencies) {
        addLibrariesFromModule(targetEnvironmentRequest, dependency, pythonPathList);
        addRootsFromModule(dependency, pythonPathList);
      }
    }
    return pythonPathList;
  }

  public static @NotNull List<Function<TargetEnvironment, String>> getAddedPaths(@NotNull TargetEnvironmentRequest targetEnvironmentRequest,
                                                                                 @NotNull Sdk pythonSdk) {
    List<Function<TargetEnvironment, String>> pathList = new ArrayList<>();
    final SdkAdditionalData sdkAdditionalData = pythonSdk.getSdkAdditionalData();
    if (sdkAdditionalData instanceof PythonSdkAdditionalData) {
      final Set<VirtualFile> addedPaths = ((PythonSdkAdditionalData)sdkAdditionalData).getAddedPathFiles();
      for (VirtualFile file : addedPaths) {
        addToPythonPath(targetEnvironmentRequest, file, pathList);
      }
    }
    return pathList;
  }

  private static void addToPythonPath(@NotNull TargetEnvironmentRequest targetEnvironmentRequest,
                                      @NotNull VirtualFile file,
                                      @NotNull Collection<Function<TargetEnvironment, String>> pathList) {
    if (file.getFileSystem() instanceof JarFileSystem) {
      final VirtualFile realFile = JarFileSystem.getInstance().getVirtualFileForJar(file);
      if (realFile != null) {
        addIfNeeded(targetEnvironmentRequest, realFile, pathList);
      }
    }
    else {
      addIfNeeded(targetEnvironmentRequest, file, pathList);
    }
  }

  private static void addIfNeeded(@NotNull TargetEnvironmentRequest targetEnvironmentRequest,
                                  @NotNull VirtualFile file,
                                  @NotNull Collection<Function<TargetEnvironment, String>> pathList) {
    String filePath = FileUtil.toSystemDependentName(file.getPath());
    pathList.add(TargetEnvironmentFunctions.getTargetEnvironmentValueForLocalPath(targetEnvironmentRequest, filePath));
  }

  private static void addLibrariesFromModule(@NotNull TargetEnvironmentRequest targetEnvironmentRequest,
                                             @NotNull Module module,
                                             @NotNull Collection<Function<TargetEnvironment, String>> list) {
    final OrderEntry[] entries = ModuleRootManager.getInstance(module).getOrderEntries();
    for (OrderEntry entry : entries) {
      if (entry instanceof LibraryOrderEntry) {
        final String name = ((LibraryOrderEntry)entry).getLibraryName();
        if (name != null && name.endsWith(LibraryContributingFacet.PYTHON_FACET_LIBRARY_NAME_SUFFIX)) {
          // skip libraries from Python facet
          continue;
        }
        for (VirtualFile root : ((LibraryOrderEntry)entry).getRootFiles(OrderRootType.CLASSES)) {
          final Library library = ((LibraryOrderEntry)entry).getLibrary();
          if (!PlatformUtils.isPyCharm()) {
            addToPythonPath(targetEnvironmentRequest, root, list);
          }
          else if (library instanceof LibraryEx) {
            final PersistentLibraryKind<?> kind = ((LibraryEx)library).getKind();
            if (kind == PythonLibraryType.getInstance().getKind()) {
              addToPythonPath(targetEnvironmentRequest, root, list);
            }
          }
        }
      }
    }
  }

  private static void addRootsFromModule(@NotNull Module module, @NotNull Collection<Function<TargetEnvironment, String>> pythonPathList) {
    // for Jython
    final CompilerModuleExtension extension = CompilerModuleExtension.getInstance(module);
    if (extension != null) {
      final VirtualFile path = extension.getCompilerOutputPath();
      if (path != null) {
        pythonPathList.add(TargetEnvironmentFunctions.constant(path.getPath()));
      }
      final VirtualFile pathForTests = extension.getCompilerOutputPathForTests();
      if (pathForTests != null) {
        pythonPathList.add(TargetEnvironmentFunctions.constant(pathForTests.getPath()));
      }
    }
  }

  private static void addRoots(@NotNull TargetEnvironmentRequest targetEnvironmentRequest,
                               @NotNull Collection<Function<TargetEnvironment, String>> pythonPathList,
                               @NotNull VirtualFile @NotNull [] roots) {
    for (VirtualFile root : roots) {
      addToPythonPath(targetEnvironmentRequest, root, pythonPathList);
    }
  }
}
