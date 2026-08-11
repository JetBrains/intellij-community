// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.testFramework.fixtures

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry.Companion.get
import com.intellij.util.ArrayUtil
import com.intellij.util.ui.UIUtil
import org.jetbrains.idea.maven.indices.MavenArchetypeManager
import org.jetbrains.idea.maven.indices.MavenIndicesManager
import org.jetbrains.idea.maven.indices.MavenSystemIndicesManager
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.project.MavenSettingsCache
import org.jetbrains.idea.maven.server.MavenServerManager
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

class MavenIndicesTestFixture(
  private val myDir: Path,
  private val myProject: Project,
  private val myTestRootDisposable: Disposable,
  private val myLocalRepoDir: String?,
  vararg extraRepoDirs: String
) {
  private val myExtraRepoDirs = extraRepoDirs

  private var myRepositoryHelper: MavenCustomRepositoryHelper? = null

  constructor(dir: Path, project: Project, testRootDisposable: Disposable) : this(dir, project, testRootDisposable, "local1", "local2")

  @Throws(Exception::class)
  fun setUpBeforeImport() {
    myRepositoryHelper = MavenCustomRepositoryHelper(myDir, *ArrayUtil.append(myExtraRepoDirs, myLocalRepoDir))

    for (each in myExtraRepoDirs) {
      addToRepository(each)
    }

    MavenProjectsManager.getInstance(myProject).getGeneralSettings().setLocalRepository(
      myRepositoryHelper!!.getTestData(myLocalRepoDir!!).toString())
    MavenSettingsCache.getInstance(myProject).reload()
    get("maven.skip.gav.update.in.unit.test.mode").setValue(false, myTestRootDisposable)
  }

  @Throws(Exception::class)
  fun setUp() {
    setUpBeforeImport()
    setUpAfterImport()
  }

  fun setUpAfterImport() {
    MavenSystemIndicesManager.getInstance().setTestIndicesDir(myDir.resolve("MavenIndices"))
    //todo: rewrite al this to coroutines
    val f = CompletableFuture<Void?>()
    this.indicesManager.scheduleUpdateIndicesList {
      f.complete(null)
      null
    }
    f.join()
    this.indicesManager.waitForGavUpdateCompleted()
    runInEdt { UIUtil.dispatchAllInvocationEvents() }
  }

  @Throws(IOException::class)
  fun addToRepository(relPath: String?) {
    myRepositoryHelper!!.copy(relPath!!, myLocalRepoDir!!)
  }

  fun tearDown() {
    MavenSystemIndicesManager.getInstance().gc()
    MavenServerManager.getInstance().closeAllConnectorsAndWait()
    Disposer.dispose(this.indicesManager)
  }

  val indicesManager: MavenIndicesManager
    get() = MavenIndicesManager.getInstance(myProject)

  val archetypeManager: MavenArchetypeManager
    get() = MavenArchetypeManager.getInstance(myProject)

  val repositoryHelper: MavenCustomRepositoryHelper
    get() = myRepositoryHelper!!
}
