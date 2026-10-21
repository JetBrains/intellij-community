// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PySdkUsage")

package com.jetbrains.python.sdk

import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.pyproject.model.evolution.EvoPyProjectModel
import com.intellij.python.sdk.backend.getSdkAPI
import com.intellij.python.sdk.backend.isFor
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.ApiStatus

/** Whether a Python project of this snapshot uses [sdk]. */
@ApiStatus.Internal
fun EvoPyProjectModel.Snapshot.usesSdk(sdk: Sdk): Boolean = interpreters.any { it.isFor(sdk) }

/** The modules whose Python project uses [sdk] in this snapshot. */
@ApiStatus.Internal
fun EvoPyProjectModel.Snapshot.modulesUsing(sdk: Sdk): List<Module> =
  evoPyProjects.filter { it.interpreter?.isFor(sdk) == true }.map { it.pyProject.residesOnModule }.toList()

/** The SDK of each interpreter of this snapshot. */
@ApiStatus.Internal
@Suppress("DEPRECATION") // The updater works on an SDK.
fun EvoPyProjectModel.Snapshot.sdks(): Set<Sdk> = interpreters.mapTo(LinkedHashSet()) { it.getSdkAPI() }

/**
 * Whether a Python project of [project] is known not to use [sdk]: the current snapshot holds no interpreter of it.
 * `false` before the first snapshot, when nothing is known yet. Takes no lock and does not wait, so any thread can call it.
 */
@ApiStatus.Internal
fun isKnownUnused(project: Project, sdk: Sdk): Boolean =
  EvoPyProjectModel.getInstance(project).snapshotOrNull()?.usesSdk(sdk) == false

/** The current snapshot of [project]. Waits for the first one, so it runs on a background thread outside a read action. */
@ApiStatus.Internal
@RequiresBackgroundThread
fun awaitSnapshot(project: Project): EvoPyProjectModel.Snapshot =
  runBlockingMaybeCancellable { EvoPyProjectModel.getInstance(project).snapshot() }
