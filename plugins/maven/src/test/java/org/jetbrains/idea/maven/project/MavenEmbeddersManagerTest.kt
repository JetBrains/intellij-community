/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.project

import com.intellij.maven.testFramework.MavenTestCase

class MavenEmbeddersManagerTest : MavenTestCase() {
  private var myManager: MavenEmbeddersManager? = null

  override fun setUp() {
    super.setUp()
    myManager = MavenEmbeddersManager(project)
  }

  override fun tearDownFixtures() {
    super.tearDownFixtures()
  }

  override fun tearDown() {
    try {
      myManager!!.releaseForcefullyInTests()
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  fun testBasics() {
    val one = myManager!!.getEmbedder(MavenEmbeddersManager.FOR_FOLDERS_RESOLVE, dir.toString())
    val two = myManager!!.getEmbedder(MavenEmbeddersManager.FOR_DEPENDENCIES_RESOLVE, dir.toString())

    assertNotSame(one, two)
  }

  fun testForSameId() {
    val one1 = myManager!!.getEmbedder(MavenEmbeddersManager.FOR_DEPENDENCIES_RESOLVE, dir.toString())
    val one2 = myManager!!.getEmbedder(MavenEmbeddersManager.FOR_DEPENDENCIES_RESOLVE, dir.toString())

    assertNotSame(one1, one2)

    myManager!!.release(one1)

    val one3 = myManager!!.getEmbedder(MavenEmbeddersManager.FOR_DEPENDENCIES_RESOLVE, dir.toString())

    assertSame(one1, one3)
  }

  fun testCachingOnlyOne() {
    val one1 = myManager!!.getEmbedder(MavenEmbeddersManager.FOR_DEPENDENCIES_RESOLVE, dir.toString())
    val one2 = myManager!!.getEmbedder(MavenEmbeddersManager.FOR_DEPENDENCIES_RESOLVE, dir.toString())

    assertNotSame(one1, one2)

    myManager!!.release(one1)
    myManager!!.release(one2)

    val one11 = myManager!!.getEmbedder(MavenEmbeddersManager.FOR_DEPENDENCIES_RESOLVE, dir.toString())
    val one22 = myManager!!.getEmbedder(MavenEmbeddersManager.FOR_DEPENDENCIES_RESOLVE, dir.toString())

    assertSame(one1, one11)
    assertNotSame(one2, one22)
  }

  fun testResettingAllCachedAndInUse() {
    val one1 = myManager!!.getEmbedder(MavenEmbeddersManager.FOR_DEPENDENCIES_RESOLVE, dir.toString())
    val one2 = myManager!!.getEmbedder(MavenEmbeddersManager.FOR_FOLDERS_RESOLVE, dir.toString())

    myManager!!.release(one1)
    myManager!!.reset()

    myManager!!.release(one2)

    val one11 = myManager!!.getEmbedder(MavenEmbeddersManager.FOR_DEPENDENCIES_RESOLVE, dir.toString())
    val one22 = myManager!!.getEmbedder(MavenEmbeddersManager.FOR_FOLDERS_RESOLVE, dir.toString())

    assertNotSame(one1, one11)
    assertNotSame(one2, one22)
  }
}
