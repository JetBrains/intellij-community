// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.env

import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.TestApplicationManager
import com.intellij.testFramework.UsefulTestCase
import java.io.File
import java.nio.file.Path
import kotlin.system.measureTimeMillis


/**
 * Based on `com.intellij.codeInsight.LargeProjectPerformanceTest`.
 */
abstract class AbstractLargeProjectTest : UsefulTestCase() {

  abstract val testProjectPath: Path
  protected open val projectLibraries get() = listOf<Pair<String, List<File>>>()

  protected lateinit var project: Project

  override fun runInDispatchThread() = false

  override fun setUp() {
    super.setUp()
    TestApplicationManager.getInstance()
    runWriteActionAndWait {
      // should we replace this with mock-JDK1.8 ?
      val j8 = JavaSdk.getInstance().createJdk(
        "1.8",
        JavaAwareProjectJdkTableImpl.getInstanceEx().internalJdk.homePath!!, false)
      ProjectJdkTable.getInstance().addJdk(j8, testRootDisposable)

      val internal = JavaSdk.getInstance().createJdk(
        "IDEA jdk",
        JavaAwareProjectJdkTableImpl.getInstanceEx().internalJdk.homePath!!, false)
      ProjectJdkTable.getInstance().addJdk(internal, testRootDisposable)
    }
    InspectionProfileImpl.INIT_INSPECTIONS = true

    val projectOpenTime = measureTimeMillis {
      project = openTestProject()
    }
    LOG.warn("Project has been opened successfully in $projectOpenTime ms")
  }

  override fun tearDown() {
    InspectionProfileImpl.INIT_INSPECTIONS = false
    super.tearDown()
  }

  private fun openTestProject(): Project {
    val project = ProjectManagerEx.getInstanceEx().openProject(testProjectPath, OpenProjectTask())!!
    disposeOnTearDownInEdt(Runnable { ProjectManager.getInstance().closeAndDispose(project) })

    runWriteActionAndWait {
      val tableModel = LibraryTablesRegistrar.getInstance().getLibraryTable(project).modifiableModel
      try {
        for ((libName, libRoots) in projectLibraries) {
          addLibrary(tableModel, libName, libRoots)
        }
      }
      finally {
        tableModel.commit()
      }
    }

    ApplicationManager.getApplication().invokeAndWait {
      // empty - openTestProject executed not in EDT, so, invokeAndWait just forces
      // processing of all events that were queued during project opening
    }

    //assert(ModuleManager.getInstance(project).modules.size > 200)

    (ProjectLevelVcsManager.getInstance(project) as ProjectLevelVcsManagerImpl).waitForInitialized()
    (ChangeListManager.getInstance(project) as ChangeListManagerImpl).waitUntilRefreshed()
    return project
  }

  private fun addLibrary(tableModel: LibraryTable.ModifiableModel, libName: String, libRoots: List<File>) {
    val library = tableModel.createLibrary(libName, null)
    val libModel = library.modifiableModel
    try {
      for (libRoot in libRoots) {
        libModel.addRoot(VfsUtil.getUrlForLibraryRoot(libRoot), OrderRootType.CLASSES)
      }
    }
    finally {
      libModel.commit()
    }
  }

  private fun disposeOnTearDownInEdt(runnable: Runnable) {
    Disposer.register(testRootDisposable, Disposable {
      ApplicationManager.getApplication().invokeAndWait(runnable)
    })
  }
}