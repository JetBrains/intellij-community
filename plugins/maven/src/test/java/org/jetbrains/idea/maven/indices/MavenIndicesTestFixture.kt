/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.indices

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry.Companion.get
import com.intellij.util.ArrayUtil
import com.intellij.util.ui.UIUtil
import org.jetbrains.idea.maven.MavenCustomRepositoryHelper
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
