// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("unused")
package org.jetbrains.idea.maven.fixtures

import com.intellij.maven.testFramework.fixtures.MavenImportingTestFixture
import com.intellij.maven.testFramework.fixtures.MavenTestFixture
import com.intellij.maven.testFramework.fixtures.getModule
import com.intellij.maven.testFramework.fixtures.projectRoot
import com.intellij.maven.testFramework.fixtures.testRootDisposable
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.registry.Registry
import junit.framework.TestCase.assertTrue
import org.jetbrains.idea.maven.execution.MavenRunner
import org.jetbrains.idea.maven.execution.MavenRunnerParameters
import org.jetbrains.idea.maven.execution.MavenRunnerSettings
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

// JDK setup, Maven-goal execution and installation checks (ported from MavenImportingTestCase / MavenTestCase).

fun MavenImportingTestFixture.setupJdkForModules(vararg moduleNames: String) {
  for (each in moduleNames) {
    setupJdkForModule(each)
  }
}

fun MavenImportingTestFixture.setupJdkForModule(moduleName: String): Sdk {
  val sdk = JavaAwareProjectJdkTableImpl.getInstanceEx().internalJdk
  WriteAction.runAndWait<RuntimeException> { ProjectJdkTable.getInstance(project).addJdk(sdk, disposable) }
  ModuleRootModificationUtil.setModuleSdk(getModule(moduleName), sdk)
  return sdk
}

fun MavenImportingTestFixture.executeGoal(relativePath: String?, goal: String) {
  // Running a Maven goal requires a project JDK (legacy MavenTestCase.setUp did this via setupCustomJdk()).
  val sdk = JavaAwareProjectJdkTableImpl.getInstanceEx().internalJdk
  WriteAction.runAndWait<RuntimeException> {
    val table = ProjectJdkTable.getInstance(project)
    if (table.findJdk(sdk.name) == null) table.addJdk(sdk, disposable)
    ProjectRootManager.getInstance(project).projectSdk = sdk
  }
  val dir = projectRoot.findFileByRelativePath(relativePath!!)
  val rp = MavenRunnerParameters(true, dir!!.path, null as String?, listOf(goal), emptyList())
  val rs = MavenRunnerSettings()
  val wait = Semaphore(1)
  wait.acquire()
  MavenRunner.getInstance(project).run(rp, rs) { wait.release() }
  val ok = wait.tryAcquire(10, TimeUnit.SECONDS)
  assertTrue("Maven execution failed", ok)
}

/**
 * Matches legacy MavenTestCase.hasMavenInstallation(): goal-running tests (executeGoal/deploy) require a real
 * external Maven home. When `idea.maven.test.home` is not set the test early-returns (skips) — same as legacy,
 * which kept these tests fast (~150ms) instead of running real Maven.
 */
fun MavenTestFixture.hasMavenInstallation(): Boolean = System.getProperty("idea.maven.test.home") != null

/** Disables the Maven pre-import (static sync) registry flag for the test, mirroring the legacy no-arg helper. */
fun MavenImportingTestFixture.runWithoutStaticSync() {
  Registry.get("maven.preimport.project").setValue(false, testRootDisposable)
}
