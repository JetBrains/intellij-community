// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("TargetedPythonPaths")

package com.jetbrains.python.run

import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.execution.target.value.TargetEnvironmentFunction
import com.intellij.execution.target.value.constant
import com.intellij.execution.target.value.targetPath
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkAdditionalData
import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.remote.RemoteSdkProperties
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
import java.nio.file.Path

fun initPythonPath(envs: MutableMap<String, TargetEnvironmentFunction<String>>,
                   passParentEnvs: Boolean,
                   pythonPathList: MutableCollection<TargetEnvironmentFunction<String>>,
                   targetEnvironmentRequest: TargetEnvironmentRequest) {
  // TODO [Targets API] Passing parent envs logic should be moved somewhere else
  if (passParentEnvs && targetEnvironmentRequest is LocalTargetEnvironmentRequest && !envs.containsKey(PythonEnvUtil.PYTHONPATH)) {
    appendSystemPythonPath(pythonPathList)
  }
  appendToPythonPath(envs, pythonPathList, targetEnvironmentRequest.targetPlatform)
}

private fun appendSystemPythonPath(pythonPath: MutableCollection<TargetEnvironmentFunction<String>>) {
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
                      isDebug: Boolean): Collection<TargetEnvironmentFunction<String>> {
  val sdk = PythonSdkUtil.findSdkByPath(sdkHome)
  return collectPythonPath(
    Context(project, sdk, pathMapper),
    module,
    sdkHome,
    shouldAddContentRoots,
    shouldAddSourceRoots,
    isDebug
  )
}

private fun collectPythonPath(context: Context,
                              module: Module?,
                              sdkHome: String?,
                              shouldAddContentRoots: Boolean,
                              shouldAddSourceRoots: Boolean,
                              isDebug: Boolean): Collection<TargetEnvironmentFunction<String>> {
  val pythonPath: MutableSet<TargetEnvironmentFunction<String>> = LinkedHashSet(
    collectPythonPath(context,
                      module,
                      shouldAddContentRoots,
                      shouldAddSourceRoots)
  )
  if (isDebug && PythonSdkFlavor.getFlavor(sdkHome) is JythonSdkFlavor) {
    //that fixes Jython problem changing sys.argv on execfile, see PY-8164
    for (helpersResource in listOf("pycharm", "pydev")) {
      val helperPath = PythonHelpersLocator.getHelperPath(helpersResource)
      val targetHelperPath = targetPath(Path.of(helperPath))
      pythonPath.add(targetHelperPath)
    }
  }
  return pythonPath
}

private fun collectPythonPath(context: Context,
                              module: Module?,
                              addContentRoots: Boolean,
                              addSourceRoots: Boolean): Collection<TargetEnvironmentFunction<String>> {
  val pythonPathList: MutableCollection<TargetEnvironmentFunction<String>> = LinkedHashSet()
  if (module != null) {
    val dependencies: MutableSet<Module> = HashSet()
    ModuleUtilCore.getDependencies(module, dependencies)
    if (addContentRoots) {
      addRoots(context, pythonPathList, ModuleRootManager.getInstance(module).contentRoots)
      for (dependency in dependencies) {
        addRoots(context, pythonPathList, ModuleRootManager.getInstance(dependency).contentRoots)
      }
    }
    if (addSourceRoots) {
      addRoots(context, pythonPathList, ModuleRootManager.getInstance(module).sourceRoots)
      for (dependency in dependencies) {
        addRoots(context, pythonPathList, ModuleRootManager.getInstance(dependency).sourceRoots)
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

/**
 * List of [target->targetPath] functions. TargetPaths are to be added to ``PYTHONPATH`` because user did so
 */
fun getAddedPaths(sdkAdditionalData: SdkAdditionalData): List<TargetEnvironmentFunction<String>> {
  val pathList: MutableList<TargetEnvironmentFunction<String>> = ArrayList()
  if (sdkAdditionalData is PythonSdkAdditionalData) {
    val addedPaths = if (sdkAdditionalData is RemoteSdkProperties) {
      sdkAdditionalData.addedPathFiles.map { sdkAdditionalData.pathMappings.convertToRemote(it.path) }
    }
    else {
      sdkAdditionalData.addedPathFiles.map { it.path }
    }
    for (file in addedPaths) {
      pathList.add(constant(file))
    }
  }
  return pathList
}

private fun addToPythonPath(context: Context,
                            file: VirtualFile,
                            pathList: MutableCollection<TargetEnvironmentFunction<String>>) {
  if (file.fileSystem is JarFileSystem) {
    val realFile = JarFileSystem.getInstance().getVirtualFileForJar(file)
    if (realFile != null) {
      addIfNeeded(context, realFile, pathList)
    }
  }
  else {
    addIfNeeded(context, file, pathList)
  }
}

private fun addIfNeeded(context: Context,
                        file: VirtualFile,
                        pathList: MutableCollection<TargetEnvironmentFunction<String>>) {
  val filePath = Path.of(FileUtil.toSystemDependentName(file.path))
  pathList.add(getTargetPathForPythonConsoleExecution(context.project, context.sdk, context.pathMapper, filePath))
}

/**
 * Adds all libs from [module] to [pythonPathList] as [target,targetPath] func
 */
private fun addLibrariesFromModule(module: Module,
                                   pythonPathList: MutableCollection<TargetEnvironmentFunction<String>>) {
  val entries = ModuleRootManager.getInstance(module).orderEntries
  for (entry in entries) {
    if (entry is LibraryOrderEntry) {
      val name = entry.libraryName
      if (name != null && name.endsWith(LibraryContributingFacet.PYTHON_FACET_LIBRARY_NAME_SUFFIX)) {
        // skip libraries from Python facet
        continue
      }
      for (root in entry.getRootFiles(OrderRootType.CLASSES).mapNotNull { it.toNioPathOrNull() }) {
        val library = entry.library
        if (!PlatformUtils.isPyCharm()) {
          pythonPathList += targetPath(root)
        }
        else if (library is LibraryEx) {
          val kind = library.kind
          if (kind === PythonLibraryType.getInstance().kind) {
            pythonPathList += targetPath(root)
          }
        }
      }
    }
  }
}

/**
 * Returns a related [Path] for [this] virtual file where possible or `null` otherwise.
 *
 * Unlike [VirtualFile.toNioPath], this extension function does not throw [UnsupportedOperationException], but rather return `null` in the
 * same cases.
 */
private fun VirtualFile.toNioPathOrNull(): Path? = fileSystem.getNioPath(this)

private fun addRootsFromModule(module: Module, pythonPathList: MutableCollection<TargetEnvironmentFunction<String>>) {
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

private fun addRoots(context: Context,
                     pythonPathList: MutableCollection<TargetEnvironmentFunction<String>>,
                     roots: Array<VirtualFile>) {
  for (root in roots) {
    addToPythonPath(context, root, pythonPathList)
  }
}

private data class Context(val project: Project, val sdk: Sdk?, val pathMapper: PyRemotePathMapper?)