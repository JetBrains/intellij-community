// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.python.pyproject.model.evolution.EvoPyProjectModel
import com.intellij.python.sdk.backend.PythonInterpreterRoots
import com.intellij.python.sdk.backend.isDisposed
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

/**
 * Creates the package manager service of the project at its start. The service then starts the manager of each
 * interpreter of the project, so the derived roots that follow the installed packages are there right after the start.
 */
internal class PythonPackageManagersStartupActivity : ProjectActivity, DumbAware {
  override suspend fun execute(project: Project) {
    project.serviceAsync<PythonPackageManagerService>()
  }
}

/**
 * For tests: suspends until the derived roots of each interpreter of [project] are there, as after the start of the
 * project. The manager of each interpreter has its installed, declared and outdated packages, and the roots follow them, see
 * [com.intellij.python.sdk.backend.PythonInterpreterRoots].
 */
@TestOnly
@ApiStatus.Internal
suspend fun awaitDerivedRootsForTests(project: Project) {
  // An interpreter whose SDK is gone has no manager, see PythonPackageManagerServiceImpl.startManagers.
  for (interpreter in EvoPyProjectModel.getInstance(project).snapshot().interpreters.filterNot { it.isDisposed }) {
    val manager = PythonPackageManager.forPythonInterpreter(project, interpreter)
    manager.waitForInit()
    // The init does not wait for the declared packages, and a test reads them, as an inspection does.
    manager.listDeclaredPackagesCached()
    // A check of the outdated packages runs a process in the environment, so a test that deletes it waits for the check.
    manager.awaitOutdatedPackages()
  }
  PythonInterpreterRoots.getInstance(project).awaitCommittedForTests()
}
