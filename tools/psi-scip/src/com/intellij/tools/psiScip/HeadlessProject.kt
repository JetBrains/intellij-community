// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.psiScip

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.waitForSmartMode
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.InheritedJdkOrderEntry
import com.intellij.openapi.roots.JdkOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.platform.backend.observation.Observation
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.file.Path
import java.util.TreeSet
import kotlin.io.path.isDirectory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val DEFAULT_JDK_NAME = "psi-scip JDK"
private const val OPEN_TIMEOUT_PROPERTY = "psi.scip.open.timeout.seconds"
private const val SMART_MODE_TIMEOUT_PROPERTY = "psi.scip.smart.mode.timeout.seconds"

/**
 * Opens [root] headless for an export, with [jdk] as every JDK it asks for, and returns it in smart mode with its
 * configuration (such as a Maven import) done; [log] reports the steps. The export starters of this module share it.
 *
 * A directory with `.idea` opens without import, any other directory is imported.
 * A wait that exceeds its timeout is a failure; the waits use [withTimeoutOrNull],
 * because the [kotlinx.coroutines.TimeoutCancellationException] of `withTimeout` is a cancellation that the failure handler rethrows.
 */
internal suspend fun openReadyProject(root: Path, jdk: Path, log: (String) -> Unit): Project {
  val project = openProject(root, jdk, log)
  waitForProject(project, jdk, log)
  val moduleSdks = readAction {
    ModuleManager.getInstance(project).modules.map { module ->
      val sdk = ModuleRootManager.getInstance(module).sdk
      "${module.name}=${sdk?.name}@${sdk?.homePath}"
    }.sorted()
  }
  log("module SDKs: ${moduleSdks.joinToString()}")
  return project
}

private suspend fun openProject(root: Path, jdk: Path, log: (String) -> Unit): Project {
  TrustedProjects.setProjectTrusted(root, true)
  val task = OpenProjectTask {
    showWelcomeScreen = false
    beforeOpenTasks += { project ->
      configureSdk(project, jdk, log)
      true
    }
  }
  log("opening $root")
  val timeout = timeoutFromProperty(OPEN_TIMEOUT_PROPERTY, 20.minutes)
  val project = withTimeoutOrNull(timeout) {
    if (root.resolve(".idea").isDirectory()) {
      ProjectManagerEx.getInstanceEx().openProjectAsync(root, task)
    }
    else {
      ProjectUtil.openOrImportAsync(root, task)
    } ?: error("the project did not open: $root")
  } ?: error("the project did not open within $timeout ($OPEN_TIMEOUT_PROPERTY): $root")
  TrustedProjects.setProjectTrusted(project, true)
  // an import can skip the tasks of the open, and the SDK setup is idempotent
  configureSdk(project, jdk, log)
  return project
}

/**
 * Waits for smart mode and for the project configuration, such as a Maven import.
 * An import can give modules JDK names that do not exist yet, so the wait repeats until [configureSdk] registers no new name.
 */
private suspend fun waitForProject(project: Project, jdkHome: Path, log: (String) -> Unit) {
  val timeout = timeoutFromProperty(SMART_MODE_TIMEOUT_PROPERTY, 120.minutes)
  withTimeoutOrNull(timeout) {
    repeat(3) {
      project.waitForSmartMode()
      log("smart mode; waiting for the project configuration")
      Observation.awaitConfiguration(project) { message -> log("configuration: $message") }
      project.waitForSmartMode()
      if (!configureSdk(project, jdkHome, log)) {
        return@withTimeoutOrNull
      }
    }
  } ?: error("smart mode and the project configuration took longer than $timeout ($SMART_MODE_TIMEOUT_PROPERTY)")
}

/**
 * Registers [jdkHome] under every JDK name that the project or one of its modules asks for and cannot find, plus [DEFAULT_JDK_NAME].
 * A project without an SDK gets [DEFAULT_JDK_NAME]. Returns `true` when the call changed the JDK table or the project SDK.
 */
private suspend fun configureSdk(project: Project, jdkHome: Path, log: (String) -> Unit): Boolean {
  val names = TreeSet<String>()
  readAction {
    val rootManager = ProjectRootManager.getInstance(project)
    if (rootManager.projectSdk == null) {
      rootManager.projectSdkName?.let(names::add)
    }
    for (module in ModuleManager.getInstance(project).modules) {
      for (entry in ModuleRootManager.getInstance(module).orderEntries) {
        if (entry is JdkOrderEntry && entry !is InheritedJdkOrderEntry && entry.jdk == null) {
          entry.jdkName?.let(names::add)
        }
      }
    }
  }
  names.add(DEFAULT_JDK_NAME)
  val added = ArrayList<String>()
  val setProjectSdk = edtWriteAction {
    val table = ProjectJdkTable.getInstance()
    for (name in names) {
      if (table.findJdk(name) == null) {
        table.addJdk(JavaSdk.getInstance().createJdk(name, jdkHome.toString(), false))
        added.add(name)
      }
    }
    val rootManager = ProjectRootManager.getInstance(project)
    if (rootManager.projectSdk == null) {
      rootManager.projectSdk = table.findJdk(DEFAULT_JDK_NAME)
      true
    }
    else {
      false
    }
  }
  if (added.isNotEmpty()) {
    log("registered $jdkHome as ${added.joinToString()}")
  }
  if (setProjectSdk) {
    log("set the project SDK to $DEFAULT_JDK_NAME")
  }
  return added.isNotEmpty() || setProjectSdk
}

private fun timeoutFromProperty(name: String, default: Duration): Duration {
  return System.getProperty(name)?.toLongOrNull()?.takeIf { it > 0 }?.seconds ?: default
}
