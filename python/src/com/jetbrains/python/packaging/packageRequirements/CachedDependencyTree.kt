// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.packageRequirements

import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.requirements.PyDependenciesFile
import com.jetbrains.python.sdk.associatedModuleDir

/**
 * A provider that runs [fetchOutput] once per state of the files the tree is built from, however many callers ask.
 *
 * A startup asks four times: the tool window when it binds the SDK, the inspection through the dependency cache, and
 * once for each of the packagesChanged and outdatedPackagesChanged events. Every one of them ran the command again
 * while it was failing (PY-90174).
 *
 * The state is [dependencyFiles] and the lock file, with their modification stamps. A tool without a lock file, or
 * one whose lock is not written yet, is keyed on its dependency files alone: uv on a `requirements.txt` works that
 * way. [lockFileName] is looked for at the SDK's own directory, where a tool writes it, and not beside the root
 * dependency file, which is the wrong place when that file is an explicitly stored `requirements.txt` somewhere else.
 *
 * The state is `null` when nothing was resolved, rather than an empty map: two empty maps are equal, so a failure
 * held under one would never expire.
 */
internal fun PythonPackageManager.cachedDependencyTree(
  lockFileName: String? = null,
  parse: (String) -> List<PackageTreeNode> = { TreeParser.parseTrees(it.lines()) },
  dependencyFiles: suspend () -> List<PyDependenciesFile>,
  fetchOutput: suspend () -> PyResult<String>,
): DependencyTreeProvider = CachedDependencyTreeProvider(
  fetchOutput = fetchOutput,
  parse = parse,
  dependenciesState = {
    val lockFile = lockFileName?.let { sdk.associatedModuleDir?.findChild(it) }
    val inputs = dependencyFiles().map { it.virtualFile } + listOfNotNull(lockFile)
    inputs.associate { it.path to it.modificationStamp }.ifEmpty { null }
  },
)
