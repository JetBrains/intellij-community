// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("TargetedPythonPaths")

package com.jetbrains.python.run

import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.python.sdk.backend.pathMappings
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.execution.target.value.TargetEnvironmentFunction
import com.intellij.execution.target.value.constant
import com.intellij.execution.target.value.targetPath
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreterDetails
import com.intellij.util.PlatformUtils
import com.jetbrains.python.PYTHONPATH
import com.jetbrains.python.facet.LibraryContributingFacet
import com.jetbrains.python.library.PythonLibraryType
import com.jetbrains.python.remote.PyRemotePathMapper
import com.jetbrains.python.run.target.getTargetPathForPythonConsoleExecution
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.nio.file.Path

/**
 * Append pythonPathList to envs
 * Append PYTHONPATH from system environment for local target if it is necessary
 * checkPythonPathInEnvs flag used for optionally checking envs already contains PYTHONPATH
 */
internal fun initPythonPath(envs: MutableMap<String, TargetEnvironmentFunction<String>>,
                   passParentEnvs: Boolean,
                   pythonPathList: MutableCollection<TargetEnvironmentFunction<String>>,
                   targetEnvironmentRequest: TargetEnvironmentRequest,
                   checkPythonPathInEnvs: Boolean = true) {
  // TODO [Targets API] Passing parent envs logic should be moved somewhere else
  if (passParentEnvs && targetEnvironmentRequest is LocalTargetEnvironmentRequest &&
      !(checkPythonPathInEnvs && envs.containsKey(PYTHONPATH))) {
    appendSystemPythonPath(pythonPathList)
  }
  appendToPythonPath(envs, pythonPathList, targetEnvironmentRequest.targetPlatform)
}

private fun appendSystemPythonPath(pythonPath: MutableCollection<TargetEnvironmentFunction<String>>) {
  val syspath = System.getenv(PYTHONPATH)
  if (syspath != null) {
    pythonPath.addAll(syspath.split(File.pathSeparator).dropLastWhile(String::isEmpty).map(::constant))
  }
}

@ApiStatus.Internal
fun collectPythonPath(project: Project,
                      module: Module?,
                      interpreter: PythonInterpreter?,
                      pathMapper: PyRemotePathMapper?,
                      shouldAddContentRoots: Boolean,
                      shouldAddSourceRoots: Boolean
): Collection<TargetEnvironmentFunction<String>> =
  collectPythonPath(Context(project, interpreter, pathMapper), module, shouldAddContentRoots, shouldAddSourceRoots)

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
 * The paths that the user added to the `sys.path` of [interpreter], for the ``PYTHONPATH``. The path
 * mappings of a target interpreter give the path on the target.
 */
internal fun getAddedPaths(interpreter: PythonInterpreter): List<TargetEnvironmentFunction<String>> {
  val addedPaths = PythonInterpreterDetails.getInstance(interpreter.project).facts(interpreter).addedPaths
    .map { FileUtil.toSystemIndependentName(it.toString()) }
  val pathMappings = interpreter.pathMappings
  return addedPaths.map { path -> constant(pathMappings?.convertToRemote(path) ?: path) }
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
  else if (file.isDirectory()) {
    addIfNeeded(context, file, pathList)
  }
}

private fun addIfNeeded(context: Context,
                        file: VirtualFile,
                        pathList: MutableCollection<TargetEnvironmentFunction<String>>) {
  val filePath = Path.of(FileUtil.toSystemDependentName(file.path))
  pathList.add(getTargetPathForPythonConsoleExecution(context.project, context.interpreter, context.pathMapper, filePath))
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

private data class Context(val project: Project, val interpreter: PythonInterpreter?, val pathMapper: PyRemotePathMapper?)