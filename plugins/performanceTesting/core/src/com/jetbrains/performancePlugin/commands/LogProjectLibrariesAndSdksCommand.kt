// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import org.jetbrains.annotations.NonNls
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * Logs the state of the currently opened project's libraries and SDKs so that a "red code" /
 * unresolved-references failure (typically caused by a cleared or half-populated `.m2`) can be
 * diagnosed from the logs.
 *
 * Reports:
 *  - project and application (global) libraries with invalid/unresolved CLASSES roots,
 *  - module-level libraries with invalid CLASSES roots (attributed to the owning module),
 *  - the project SDK and per-module SDKs (missing / no roots),
 *  - every JDK in [ProjectJdkTable] whose home path is missing or which has no CLASSES roots.
 *
 * This command is log-only: it never fails the test. Broken items are logged with [logger].warn,
 * everything else with [logger].info, and a one-line summary is also reported to the playback context.
 */
class LogProjectLibrariesAndSdksCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: @NonNls String = CMD_PREFIX + "logProjectLibrariesAndSdks"

    private val LOG = logger<LogProjectLibrariesAndSdksCommand>()
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val project = context.project
    val report = StringBuilder()
    report.appendLine("Project libraries and SDKs state for '${project.name}':")

    var brokenLibraries = 0
    var affectedModules = 0
    var brokenSdks = 0

    readAction {
      // Project-level and application (global) libraries.
      val registrar = LibraryTablesRegistrar.getInstance()
      val reportedLibraries = HashSet<Library>()
      for ((scope, table) in listOf("project" to registrar.getLibraryTable(project), "global" to registrar.libraryTable)) {
        for (library in table.libraries) {
          reportedLibraries.add(library)
          if (reportLibrary(report, "$scope library", library)) brokenLibraries++
        }
      }

      // Module-level libraries and module SDKs.
      val affected = HashSet<String>()
      for (module in ModuleManager.getInstance(project).modules) {
        val rootManager = ModuleRootManager.getInstance(module)
        for (entry in rootManager.orderEntries) {
          if (entry !is LibraryOrderEntry) continue
          val library = entry.library ?: continue
          if (!reportedLibraries.add(library)) continue // already reported as project/global library
          if (reportLibrary(report, "module '${module.name}' library", library)) {
            brokenLibraries++
            affected.add(module.name)
          }
        }

        if (!rootManager.isSdkInherited && rootManager.sdk == null) {
          report.appendLine("module '${module.name}' has no SDK configured")
          brokenSdks++
        }
      }
      affectedModules = affected.size

      // Project SDK.
      val projectSdk = ProjectRootManager.getInstance(project).projectSdk
      if (projectSdk == null) {
        report.appendLine("project SDK is not set")
        brokenSdks++
      }
      else if (reportSdk(report, "project SDK", projectSdk)) {
        brokenSdks++
      }

      // All JDKs registered in the table.
      for (jdk in ProjectJdkTable.getInstance().allJdks) {
        if (reportSdk(report, "JDK table entry", jdk)) brokenSdks++
      }
    }

    val summary = "Project state summary: $brokenLibraries library(ies) with unresolved roots " +
                  "across $affectedModules module(s), $brokenSdks broken/missing SDK(s)"
    report.appendLine(summary)

    LOG.info(report.toString())
    context.message(summary, line)
  }

  /** Returns `true` if the library has unresolved CLASSES roots. */
  private fun reportLibrary(report: StringBuilder, kind: String, library: Library): Boolean {
    val name = library.name ?: "<unnamed>"
    val libraryEx = library as? LibraryEx
    val invalidClasses = libraryEx?.getInvalidRootUrls(OrderRootType.CLASSES).orEmpty()
    if (invalidClasses.isEmpty()) {
      return false
    }
    report.appendLine("$kind '$name': unresolved CLASSES roots $invalidClasses")
    return true
  }

  /** Returns `true` if the SDK is broken (missing home path or no CLASSES roots). */
  private fun reportSdk(report: StringBuilder, kind: String, sdk: Sdk): Boolean {
    val homePath = sdk.homePath
    val homeExists = homePath != null && Path.of(homePath).exists()
    val classesRoots = sdk.rootProvider.getUrls(OrderRootType.CLASSES)
    val broken = !homeExists || classesRoots.isEmpty()
    if (broken) {
      report.appendLine("$kind '${sdk.name}' (${sdk.sdkType.name}): homePath=$homePath, homeExists=$homeExists, classesRoots=${classesRoots.size}")
    }
    return broken
  }
}
