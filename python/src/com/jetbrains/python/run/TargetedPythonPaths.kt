// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("TargetedPythonPaths")

package com.jetbrains.python.run

import com.intellij.execution.target.TargetEnvironment
import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.execution.target.value.constant
import com.intellij.execution.target.value.getTargetEnvironmentValueForLocalPath
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PlatformUtils
import com.jetbrains.python.PythonHelpersLocator
import com.jetbrains.python.facet.LibraryContributingFacet
import com.jetbrains.python.library.PythonLibraryType
import com.jetbrains.python.remote.PyRemotePathMapper
import com.jetbrains.python.run.target.getTargetPathForPythonConsoleExecution
import com.jetbrains.python.sdk.PythonEnvUtil
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.sdk.flavors.JythonSdkFlavor
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import java.io.File
import java.util.function.Function

fun initPythonPath(envs: MutableMap<String, Function<TargetEnvironment, String>>,
                   passParentEnvs: Boolean,
                   pythonPathList: MutableCollection<Function<TargetEnvironment, String>>,
                   targetEnvironmentRequest: TargetEnvironmentRequest) {
  // TODO [Targets API] Passing parent envs logic should be moved somewhere else
  if (passParentEnvs && targetEnvironmentRequest is LocalTargetEnvironmentRequest && !envs.containsKey(PythonEnvUtil.PYTHONPATH)) {
    appendSystemPythonPath(pythonPathList)
  }
  appendToPythonPath(envs, pythonPathList, targetEnvironmentRequest.targetPlatform)
}

private fun appendSystemPythonPath(pythonPath: MutableCollection<Function<TargetEnvironment, String>>) {
  val syspath = System.getenv(PythonEnvUtil.PYTHONPATH)
  if (syspath != null) {
    pythonPath.addAll(syspath.split(File.pathSeparator).dropLastWhile(String::isEmpty).map(::constant))
  }
}

fun collectPythonPath(project: Project,
                      module: Module?,
                      sdkHome: String?,
                      pathMapper: PyRemotePathMapper?,
                      shouldAddContentRoots: Boolean,
                      shouldAddSourceRoots: Boolean,
                      isDebug: Boolean): Collection<Function<TargetEnvironment, String>> {
  val sdk = PythonSdkUtil.findSdkByPath(sdkHome)
  return collectPythonPath(
    LocalPathToTargetPathConverterSdkAware(project, sdk, pathMapper),
    module,
    sdkHome,
    shouldAddContentRoots,
    shouldAddSourceRoots,
    isDebug
  )
}

private fun collectPythonPath(pathConverter: LocalPathToTargetPathConverter,
                              module: Module?,
                              sdkHome: String?,
                              shouldAddContentRoots: Boolean,
                              shouldAddSourceRoots: Boolean,
                              isDebug: Boolean): Collection<Function<TargetEnvironment, String>> {
  val pythonPath: MutableSet<Function<TargetEnvironment, String>> = LinkedHashSet(
    collectPythonPath(pathConverter,
                      module,
                      shouldAddContentRoots,
                      shouldAddSourceRoots)
  )
  if (isDebug && PythonSdkFlavor.getFlavor(sdkHome) is JythonSdkFlavor) {
    //that fixes Jython problem changing sys.argv on execfile, see PY-8164
    for (helpersResource in listOf("pycharm", "pydev")) {
      val helperPath = PythonHelpersLocator.getHelperPath(helpersResource)
      val targetHelperPath = getTargetEnvironmentValueForLocalPath(helperPath)
      pythonPath.add(targetHelperPath)
    }
  }
  return pythonPath
}

private fun collectPythonPath(pathConverter: LocalPathToTargetPathConverter,
                              module: Module?,
                              addContentRoots: Boolean,
                              addSourceRoots: Boolean): Collection<Function<TargetEnvironment, String>> {
  val pythonPathList: MutableCollection<Function<TargetEnvironment, String>> = LinkedHashSet()
  if (module != null) {
    val dependencies: MutableSet<Module> = HashSet()
    ModuleUtilCore.getDependencies(module, dependencies)
    if (addContentRoots) {
      addRoots(pathConverter, pythonPathList, ModuleRootManager.getInstance(module).contentRoots)
      for (dependency in dependencies) {
        addRoots(pathConverter, pythonPathList, ModuleRootManager.getInstance(dependency).contentRoots)
      }
    }
    if (addSourceRoots) {
      addRoots(pathConverter, pythonPathList, ModuleRootManager.getInstance(module).sourceRoots)
      for (dependency in dependencies) {
        addRoots(pathConverter, pythonPathList, ModuleRootManager.getInstance(dependency).sourceRoots)
      }
    }
    addLibrariesFromModule(module, pythonPathList)
    addRootsFromModule(module, pythonPathList)
    for (dependency in dependencies) {
      addLibrariesFromModule(dependency, pythonPathList)
      addRootsFromModule(dependency, pythonPathList)
    }
  }
  return pythonPathList
}

fun getAddedPaths(pythonSdk: Sdk): List<Function<TargetEnvironment, String>> {
  val pathList: MutableList<Function<TargetEnvironment, String>> = ArrayList()
  val sdkAdditionalData = pythonSdk.sdkAdditionalData
  if (sdkAdditionalData is PythonSdkAdditionalData) {
    val addedPaths = sdkAdditionalData.addedPathFiles
    for (file in addedPaths) {
      addToPythonPath(LocalPathToTargetPathConverterImpl(), file, pathList)
    }
  }
  return pathList
}

private fun addToPythonPath(pathConverter: LocalPathToTargetPathConverter,
                            file: VirtualFile,
                            pathList: MutableCollection<Function<TargetEnvironment, String>>) {
  if (file.fileSystem is JarFileSystem) {
    val realFile = JarFileSystem.getInstance().getVirtualFileForJar(file)
    if (realFile != null) {
      addIfNeeded(pathConverter, realFile, pathList)
    }
  }
  else {
    addIfNeeded(pathConverter, file, pathList)
  }
}

private fun addIfNeeded(pathConverter: LocalPathToTargetPathConverter,
                        file: VirtualFile,
                        pathList: MutableCollection<Function<TargetEnvironment, String>>) {
  val filePath = FileUtil.toSystemDependentName(file.path)
  pathList.add(pathConverter.getTargetPath(filePath))
}

private fun addLibrariesFromModule(module: Module,
                                   list: MutableCollection<Function<TargetEnvironment, String>>) {
  val entries = ModuleRootManager.getInstance(module).orderEntries
  for (entry in entries) {
    if (entry is LibraryOrderEntry) {
      val name = entry.libraryName
      if (name != null && name.endsWith(LibraryContributingFacet.PYTHON_FACET_LIBRARY_NAME_SUFFIX)) {
        // skip libraries from Python facet
        continue
      }
      for (root in entry.getRootFiles(OrderRootType.CLASSES)) {
        val library = entry.library
        if (!PlatformUtils.isPyCharm()) {
          addToPythonPath(LocalPathToTargetPathConverterImpl(), root, list)
        }
        else if (library is LibraryEx) {
          val kind = library.kind
          if (kind === PythonLibraryType.getInstance().kind) {
            addToPythonPath(LocalPathToTargetPathConverterImpl(), root, list)
          }
        }
      }
    }
  }
}

private fun addRootsFromModule(module: Module, pythonPathList: MutableCollection<Function<TargetEnvironment, String>>) {
  // for Jython
  val extension = CompilerModuleExtension.getInstance(module)
  if (extension != null) {
    val path = extension.compilerOutputPath
    if (path != null) {
      pythonPathList.add(constant(path.path))
    }
    val pathForTests = extension.compilerOutputPathForTests
    if (pathForTests != null) {
      pythonPathList.add(constant(pathForTests.path))
    }
  }
}

private fun addRoots(pathConverter: LocalPathToTargetPathConverter,
                     pythonPathList: MutableCollection<Function<TargetEnvironment, String>>,
                     roots: Array<VirtualFile>) {
  for (root in roots) {
    addToPythonPath(pathConverter, root, pythonPathList)
  }
}

private fun interface LocalPathToTargetPathConverter {
  fun getTargetPath(localPath: String): Function<TargetEnvironment, String>
}

private class LocalPathToTargetPathConverterImpl : LocalPathToTargetPathConverter {
  override fun getTargetPath(localPath: String): Function<TargetEnvironment, String> {
    return getTargetEnvironmentValueForLocalPath(localPath)
  }
}

private class LocalPathToTargetPathConverterSdkAware(private val project: Project,
                                                     private val sdk: Sdk?,
                                                     private val pathMapper: PyRemotePathMapper?)
  : LocalPathToTargetPathConverter {
  override fun getTargetPath(localPath: String): Function<TargetEnvironment, String> {
    return getTargetPathForPythonConsoleExecution(project, sdk, pathMapper, localPath)
  }
}