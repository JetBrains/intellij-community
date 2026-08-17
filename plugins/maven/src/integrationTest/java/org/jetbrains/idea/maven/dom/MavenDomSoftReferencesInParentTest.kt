/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.dom

import com.intellij.maven.testFramework.fixtures.MavenDomTestFixture.Highlight
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenDomFixture
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.fixtures.checkHighlighting
import org.jetbrains.idea.maven.utils.MavenLog
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenDomSoftReferencesInParentTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenDomFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )

  @BeforeEach
  fun setUp() = runBlocking {
    VfsTestUtil.syncRefresh()
  }

  @Test
  fun testDoNotHighlightSourceDirectoryInParentPom() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <build>
                    <sourceDirectory>dsfsfd/sdfsdf</sourceDirectory>
                    <testSourceDirectory>qwqwq/weqweqw</testSourceDirectory>
                    <scriptSourceDirectory>dfsdf/fsdf</scriptSourceDirectory>
                    </build>
                    """.trimIndent())

    maven.checkHighlighting()
  }

  @Test
  fun testHighlightSourceDirectory() = runBlocking {
    ApplicationManager.getApplication().messageBus.connect(maven.disposable)
      .subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
        override fun before(events: MutableList<out VFileEvent>) {
          MavenLog.LOG.warn("before $events")
        }

        override fun after(events: MutableList<out VFileEvent>) {
          MavenLog.LOG.warn("after $events")
        }
      })

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>jar</packaging>
                    <build>
                    <sourceDirectory>foo1</sourceDirectory>
                    <testSourceDirectory>foo2</testSourceDirectory>
                    <scriptSourceDirectory>foo3</scriptSourceDirectory>
                    </build>
                    """.trimIndent())


    maven.checkHighlighting(maven.projectPom,
                            Highlight(text = "foo1"),
                            Highlight(text = "foo2"),
                            Highlight(text = "foo3"))

  }
}
